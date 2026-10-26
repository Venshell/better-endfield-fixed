package dev.betterendfield.android;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Native custom-model configuration transport accepts at most 1 MiB of UTF-8. */
final class BemConfigurationLimit {
    static final int MAX_BYTES=1024*1024;
    private BemConfigurationLimit() {}

    static String index(String value) throws IOException {return require(value,"模型索引超出运行时上限");}
    static String configuration(String value) throws IOException {return require(value,"模型配置超出运行时上限");}

    private static String require(String value,String error) throws IOException {
        if(value==null||value.length()>MAX_BYTES||
                value.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES) throw new IOException(error);
        return value;
    }
}
