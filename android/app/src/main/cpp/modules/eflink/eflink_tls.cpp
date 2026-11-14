#include "eflink_lobby_client.h"
#include "modules/eflink/eflink_tls.h"

#include <atomic>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>

#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>

namespace betterendfieldnext {
namespace {
JavaVM* java_vm = nullptr;
}
void SetJavaVm(JavaVM* vm) { java_vm = vm; }
JavaVM* ActiveJavaVm() { return java_vm; }
}

namespace eflink::lobby {
namespace {

struct JniEnv {
    JNIEnv* env = nullptr;
    bool attached = false;
    JniEnv() {
        JavaVM* vm = betterendfieldnext::ActiveJavaVm();
        if (!vm) return;
        const auto state = vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (state == JNI_EDETACHED) {
            attached = vm->AttachCurrentThread(&env, nullptr) == JNI_OK;
            if (!attached) env = nullptr;
        }
    }
    ~JniEnv() {
        JavaVM* vm = betterendfieldnext::ActiveJavaVm();
        if (attached && vm) vm->DetachCurrentThread();
    }
    bool ok() {
        if (!env) return false;
        if (!env->ExceptionCheck()) return true;
        env->ExceptionClear();
        return false;
    }
};

struct Pump {
    jobject socket = nullptr;
    jobject input = nullptr;
    jobject output = nullptr;
    int remote = -1;
    std::atomic<int> alive{2};
    std::mutex close_mutex;
    bool socket_closed = false;

    void close_socket() {
        std::lock_guard lock(close_mutex);
        if (socket_closed || !socket) return;
        socket_closed = true;
        JniEnv jni;
        if (!jni.env) return;
        jclass type = jni.env->GetObjectClass(socket);
        jmethodID close = type ? jni.env->GetMethodID(type, "close", "()V") : nullptr;
        if (close) jni.env->CallVoidMethod(socket, close);
        jni.ok();
    }

    void release() {
        if (alive.fetch_sub(1) != 1) return;
        JniEnv jni;
        if (jni.env) {
            if (socket) jni.env->DeleteGlobalRef(socket);
            if (input) jni.env->DeleteGlobalRef(input);
            if (output) jni.env->DeleteGlobalRef(output);
        }
        if (remote >= 0) ::close(remote);
        remote = -1;
    }
};

bool timeout_exception(JNIEnv* env) {
    jthrowable thrown = env->ExceptionOccurred();
    if (!thrown) return false;
    jclass type = env->FindClass("java/net/SocketTimeoutException");
    const bool timeout = type && env->IsInstanceOf(thrown, type);
    env->ExceptionClear();
    return timeout;
}

void copy_down(const std::shared_ptr<Pump>& pump) {
    JniEnv jni;
    if (!jni.env) {
        pump->release();
        return;
    }
    jbyteArray buffer = jni.env->NewByteArray(4096);
    jclass type = jni.env->GetObjectClass(pump->input);
    jmethodID read = type ? jni.env->GetMethodID(type, "read", "([B)I") : nullptr;
    if (!buffer || !read) {
        pump->release();
        return;
    }
    while (true) {
        const jint count = jni.env->CallIntMethod(pump->input, read, buffer);
        if (jni.env->ExceptionCheck()) {
            if (timeout_exception(jni.env)) continue;
            break;
        }
        if (count < 0) break;
        if (count == 0) continue;
        std::vector<jbyte> bytes(static_cast<std::size_t>(count));
        jni.env->GetByteArrayRegion(buffer, 0, count, bytes.data());
        std::size_t sent = 0;
        bool failed = false;
        while (sent < bytes.size()) {
            const auto wrote = ::send(pump->remote, bytes.data() + sent, bytes.size() - sent, MSG_NOSIGNAL);
            if (wrote <= 0) {
                failed = true;
                break;
            }
            sent += static_cast<std::size_t>(wrote);
        }
        if (failed) break;
    }
    ::shutdown(pump->remote, SHUT_WR);
    pump->release();
}

void copy_up(const std::shared_ptr<Pump>& pump) {
    JniEnv jni;
    if (!jni.env) {
        pump->close_socket();
        pump->release();
        return;
    }
    jbyteArray buffer = jni.env->NewByteArray(4096);
    jclass type = jni.env->GetObjectClass(pump->output);
    jmethodID write = type ? jni.env->GetMethodID(type, "write", "([BII)V") : nullptr;
    jmethodID flush = type ? jni.env->GetMethodID(type, "flush", "()V") : nullptr;
    if (!buffer || !write || !flush) {
        pump->close_socket();
        pump->release();
        return;
    }
    std::vector<char> bytes(4096);
    while (true) {
        const auto count = ::recv(pump->remote, bytes.data(), bytes.size(), 0);
        if (count <= 0) break;
        jni.env->SetByteArrayRegion(buffer, 0, static_cast<jsize>(count), reinterpret_cast<const jbyte*>(bytes.data()));
        jni.env->CallVoidMethod(pump->output, write, buffer, 0, static_cast<jint>(count));
        if (!jni.ok()) break;
        jni.env->CallVoidMethod(pump->output, flush);
        if (!jni.ok()) break;
    }
    pump->close_socket();
    pump->release();
}

jobject call(JNIEnv* env, jobject object, const char* name, const char* signature) {
    jclass type = object ? env->GetObjectClass(object) : nullptr;
    jmethodID method = type ? env->GetMethodID(type, name, signature) : nullptr;
    if (!method) return nullptr;
    jobject result = env->CallObjectMethod(object, method);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return nullptr;
    }
    return result;
}

}  // namespace

int DialTls(const std::string& endpoint, int timeoutMs, std::string& error) {
    const auto colon = endpoint.rfind(':');
    if (colon == std::string::npos || colon == 0 || colon + 1 >= endpoint.size()) {
        error = "大厅地址须为 host:port";
        return -1;
    }
    if (!betterendfieldnext::ActiveJavaVm()) {
        error = "无法连接大厅";
        return -1;
    }
    const auto host = endpoint.substr(0, colon);
    int port = 0;
    try {
        port = std::stoi(endpoint.substr(colon + 1));
    } catch (...) {
        error = "大厅地址须为 host:port";
        return -1;
    }
    JniEnv jni;
    if (!jni.env) {
        error = "无法连接大厅";
        return -1;
    }
    JNIEnv* env = jni.env;
    jclass contextClass = env->FindClass("javax/net/ssl/SSLContext");
    jmethodID getDefault = contextClass ? env->GetStaticMethodID(contextClass, "getDefault", "()Ljavax/net/ssl/SSLContext;") : nullptr;
    jobject context = getDefault ? env->CallStaticObjectMethod(contextClass, getDefault) : nullptr;
    jobject factory = call(env, context, "getSocketFactory", "()Ljavax/net/ssl/SSLSocketFactory;");
    jobject socket = call(env, factory, "createSocket", "()Ljava/net/Socket;");
    if (!socket) {
        error = "无法连接大厅";
        return -1;
    }
    jclass addressClass = env->FindClass("java/net/InetSocketAddress");
    jmethodID addressInit = addressClass ? env->GetMethodID(addressClass, "<init>", "(Ljava/lang/String;I)V") : nullptr;
    jstring hostName = env->NewStringUTF(host.c_str());
    jobject address = addressInit && hostName ? env->NewObject(addressClass, addressInit, hostName, port) : nullptr;
    jclass socketClass = env->GetObjectClass(socket);
    jmethodID connect = socketClass ? env->GetMethodID(socketClass, "connect", "(Ljava/net/SocketAddress;I)V") : nullptr;
    if (!address || !connect) {
        error = "无法连接大厅";
        return -1;
    }
    env->CallVoidMethod(socket, connect, address, timeoutMs);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        error = "无法连接大厅";
        return -1;
    }
    jclass sslClass = env->FindClass("javax/net/ssl/SSLSocket");
    jmethodID getParams = sslClass ? env->GetMethodID(sslClass, "getSSLParameters", "()Ljavax/net/ssl/SSLParameters;") : nullptr;
    jobject params = getParams ? env->CallObjectMethod(socket, getParams) : nullptr;
    jclass paramsClass = params ? env->GetObjectClass(params) : nullptr;
    jmethodID setName = paramsClass ? env->GetMethodID(paramsClass, "setEndpointIdentificationAlgorithm", "(Ljava/lang/String;)V") : nullptr;
    jstring https = env->NewStringUTF("HTTPS");
    if (!params || !setName || !https) {
        error = "无法验证联机服务器";
        return -1;
    }
    env->CallVoidMethod(params, setName, https);
    jmethodID setParams = env->GetMethodID(sslClass, "setSSLParameters", "(Ljavax/net/ssl/SSLParameters;)V");
    env->CallVoidMethod(socket, setParams, params);
    jmethodID handshake = env->GetMethodID(sslClass, "startHandshake", "()V");
    env->CallVoidMethod(socket, handshake);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        error = "无法验证联机服务器";
        return -1;
    }
    jmethodID timeout = env->GetMethodID(socketClass, "setSoTimeout", "(I)V");
    jmethodID noDelay = env->GetMethodID(socketClass, "setTcpNoDelay", "(Z)V");
    if (timeout) env->CallVoidMethod(socket, timeout, 8000);
    if (noDelay) env->CallVoidMethod(socket, noDelay, JNI_TRUE);
    env->ExceptionClear();
    jobject input = call(env, socket, "getInputStream", "()Ljava/io/InputStream;");
    jobject output = call(env, socket, "getOutputStream", "()Ljava/io/OutputStream;");
    if (!input || !output) {
        error = "无法连接大厅";
        return -1;
    }
    int fds[2] = {-1, -1};
    if (::socketpair(AF_UNIX, SOCK_STREAM, 0, fds) != 0) {
        error = "无法连接大厅";
        return -1;
    }
    auto pump = std::make_shared<Pump>();
    pump->socket = env->NewGlobalRef(socket);
    pump->input = env->NewGlobalRef(input);
    pump->output = env->NewGlobalRef(output);
    pump->remote = fds[1];
    std::thread(copy_down, pump).detach();
    std::thread(copy_up, pump).detach();
    timeval wait{8, 0};
    ::setsockopt(fds[0], SOL_SOCKET, SO_RCVTIMEO, &wait, sizeof wait);
    ::setsockopt(fds[0], SOL_SOCKET, SO_SNDTIMEO, &wait, sizeof wait);
    error.clear();
    return fds[0];
}

}  // namespace eflink::lobby
