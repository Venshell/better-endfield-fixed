#!/usr/bin/env python3
"""EF Link lobby. The default listen port is 47617.

Players dial TCP and UDP on the same port. Each TCP line is JSON and carries
"game":"endfield". This process lists rooms, splices each guest's TCP stream
to the host, and relays UDP the same way. Phones only talk to this server, so
symmetric NAT does not matter. Bytes after the handshake are EF Link frames.
A UDP datagram is an 8-byte channel token plus one EF Link datagram.

    python3 server.py --bind 0.0.0.0 --port 47617
"""

import argparse
import json
import os
import secrets
import socket
import ssl
import threading

from auth import AccountGate

GAME = "endfield"
MAX_LINE = 8192
MAX_ROOMS = 64
JOIN_WAIT = 8
# One room keeps a host thread plus one splice thread per guest. The extra
# slots cover list, login, and joins that have not settled yet.
MAX_CONNECTIONS = MAX_ROOMS * 16 + 256
MAX_PER_IP = 32
MAX_PENDING_BRIDGES = 64
MAX_PENDING_PER_ACCOUNT = 2


class Room:
    def __init__(self, room_id, name, host_name, version, max_players, password, control, peer):
        self.id = room_id
        self.name = name
        self.host_name = host_name
        self.version = version
        self.max_players = max_players
        self.password = password
        self.players = 1
        self.control = control
        self.peer = peer
        self.write_lock = threading.Lock()
        self.pending = {}
        self.lock = threading.Lock()
        self.udp_token = secrets.token_hex(8)
        self.host_udp = None
        self.relays = []
        self.host_account = ""

    def public(self):
        return {
            "id": self.id,
            "name": self.name,
            "host": self.host_name,
            "players": self.players,
            "max": self.max_players,
            "password": self.password,
            "version": self.version,
        }


class Lobby:
    def __init__(self):
        self.rooms = {}
        self.by_control = {}
        self.lock = threading.Lock()

    def add(self, room):
        with self.lock:
            if len(self.rooms) >= MAX_ROOMS:
                return False
            for _ in range(32):
                room_id = secrets.randbelow(900000) + 100000
                if room_id not in self.rooms:
                    room.id = room_id
                    self.rooms[room_id] = room
                    self.by_control[room.control] = room
                    return True
            return False

    def drop_control(self, control):
        with self.lock:
            room = self.by_control.pop(control, None)
            if room is not None:
                self.rooms.pop(room.id, None)
        if room is not None:
            forget_room(room)
        return room

    def get(self, room_id):
        with self.lock:
            return self.rooms.get(room_id)

    def snapshot(self):
        with self.lock:
            return [room.public() for room in self.rooms.values()]


LOBBY = Lobby()
RELAY_LOCK = threading.Lock()
BRIDGE_LOCK = threading.Lock()
CONNECTION_LOCK = threading.Lock()
HOST_TOKENS = {}
RELAYS = {}
BRIDGES = {}
CONNECTIONS = {}
GATE = None
CREDENTIAL_OPS = ("register", "verify", "login", "list", "host", "join")


def gate():
    if GATE is None:
        raise RuntimeError("account gate is not open")
    return GATE


def reserve_connection(ip):
    with CONNECTION_LOCK:
        if sum(CONNECTIONS.values()) >= MAX_CONNECTIONS:
            return False
        if CONNECTIONS.get(ip, 0) >= MAX_PER_IP:
            return False
        CONNECTIONS[ip] = CONNECTIONS.get(ip, 0) + 1
        return True


def release_connection(ip):
    with CONNECTION_LOCK:
        left = CONNECTIONS.get(ip, 0) - 1
        if left > 0:
            CONNECTIONS[ip] = left
        else:
            CONNECTIONS.pop(ip, None)


def reserve_bridge(ticket, pending):
    with BRIDGE_LOCK:
        waiting = [item for item in BRIDGES.values() if not item["spliced"]]
        if len(waiting) >= MAX_PENDING_BRIDGES:
            return False
        account = pending.get("account")
        if sum(1 for item in waiting if item.get("account") == account) >= MAX_PENDING_PER_ACCOUNT:
            return False
        BRIDGES[ticket] = pending
        return True


def send_line(conn, payload, lock=None):
    data = (json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
    if lock is None:
        conn.sendall(data)
        return
    with lock:
        conn.sendall(data)


def read_line(conn):
    buf = bytearray()
    while len(buf) <= MAX_LINE:
        chunk = conn.recv(1)
        if not chunk:
            return None
        if chunk == b"\n":
            return buf.decode("utf-8", "replace")
        buf += chunk
    raise ValueError("line too long")


def splice(left, right):
    def pump(src, dst):
        try:
            while True:
                data = src.recv(65536)
                if not data:
                    break
                dst.sendall(data)
        except OSError:
            pass
        finally:
            # Closing, not only shutting down, is what wakes a blocked recv.
            # Otherwise the other direction stays up and the UDP token remains usable.
            for held in (src, dst):
                try:
                    held.close()
                except OSError:
                    pass

    threads = (
        threading.Thread(target=pump, args=(left, right), daemon=True),
        threading.Thread(target=pump, args=(right, left), daemon=True),
    )
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    for conn in (left, right):
        try:
            conn.close()
        except OSError:
            pass


def forget_relay(room, token):
    with RELAY_LOCK:
        RELAYS.pop(token, None)
        try:
            room.relays.remove(token)
        except ValueError:
            pass


def forget_room(room):
    with RELAY_LOCK:
        HOST_TOKENS.pop(bytes.fromhex(room.udp_token), None)
        for token in room.relays:
            RELAYS.pop(token, None)
        room.host_udp = None


def relay_udp(sock):
    while True:
        try:
            data, addr = sock.recvfrom(2048)
        except OSError:
            return
        if len(data) < 8:
            continue
        token, rest = data[:8], data[8:]
        with RELAY_LOCK:
            room = HOST_TOKENS.get(token)
            if room is not None:
                room.host_udp = addr
                continue
            channel = RELAYS.get(token)
            if channel is None or not rest:
                continue
            # H/G is which end sent this datagram. Comparing source addresses
            # fails when a phone's NAT port changes between packets.
            if rest[:1] in (b"H", b"G"):
                direction, payload = rest[:1], rest[1:]
                if direction == b"H":
                    channel["host"] = addr
                    channel["room"].host_udp = addr
                    target = channel.get("guest")
                else:
                    channel["guest"] = addr
                    target = channel.get("host") or channel["room"].host_udp
            else:
                payload = rest
                host = channel.get("host") or channel["room"].host_udp
                if host is not None and addr == host:
                    target = channel.get("guest")
                else:
                    channel["guest"] = addr
                    target = host
            forwarded = token + payload if target is not None and payload else None
        if forwarded is not None:
            try:
                sock.sendto(forwarded, target)
            except OSError:
                pass


def fail(conn, error):
    try:
        send_line(conn, {"op": "error", "error": error})
    except OSError:
        pass
    try:
        conn.close()
    except OSError:
        pass


def handle(conn, peer, secure=False):
    try:
        conn.settimeout(20)
        raw = read_line(conn)
        if not raw:
            conn.close()
            return
        message = json.loads(raw)
        if message.get("game") != GAME:
            fail(conn, "wrong-game")
            return
        op = message.get("op")
        # Passwords, verification codes, the account token, and the game bridge
        # are only accepted after the TLS handshake has verified this process.
        if (op in CREDENTIAL_OPS or op == "bridge") and not secure:
            fail(conn, "tls-required")
            return
        ip = peer[0]
        if op == "register":
            reason = gate().register(message.get("username"), message.get("password"), message.get("email"), ip)
            if reason:
                fail(conn, reason)
                return
            send_line(conn, {"op": "sent"})
            conn.close()
            return
        if op == "verify":
            token, username, email, reason = gate().verify(message.get("username"), message.get("code"), ip)
            if reason:
                fail(conn, reason)
                return
            send_line(conn, {"op": "session", "token": token, "username": username, "email": email,
                             "expires": gate().session_deadline()})
            conn.close()
            return
        if op == "login":
            token, username, email, reason = gate().login(message.get("username"), message.get("password"), ip)
            if reason:
                fail(conn, reason)
                return
            send_line(conn, {"op": "session", "token": token, "username": username, "email": email,
                             "expires": gate().session_deadline()})
            conn.close()
            return
        account = None
        if op in ("list", "host", "join"):
            account = gate().session(message.get("account"), ip)
            if account is None:
                fail(conn, "login-required")
                return
        if op == "list":
            send_line(conn, {"op": "rooms", "rooms": LOBBY.snapshot()})
            conn.close()
            return
        if op == "host":
            host(conn, peer, message, account)
            return
        if op == "join":
            issue_ticket(conn, peer, message, account)
            return
        if op == "bridge":
            bridge(conn, peer, message)
            return
        fail(conn, "unknown-op")
    except (OSError, ValueError, json.JSONDecodeError):
        try:
            conn.close()
        except OSError:
            pass


def clip(value, limit):
    text = value if isinstance(value, str) else ""
    return text[:limit]


def host(conn, peer, message, account):
    name = clip(message.get("room_name"), 64) or "房间"
    host_name = clip(account, 64) or "房主"
    version = clip(message.get("version"), 32)
    try:
        max_players = int(message.get("max_players", 8))
    except (TypeError, ValueError):
        max_players = 8
    max_players = min(16, max(2, max_players))
    room = Room(0, name, host_name, version, max_players,
                bool(message.get("password")), conn, peer)
    room.host_account = host_name
    if not LOBBY.add(room):
        fail(conn, "full")
        return
    try:
        with RELAY_LOCK:
            HOST_TOKENS[bytes.fromhex(room.udp_token)] = room
        send_line(conn, {"op": "hosted", "room_id": room.id, "udp": room.udp_token}, room.write_lock)
        conn.settimeout(30)
        while True:
            try:
                raw = read_line(conn)
            except socket.timeout:
                continue
            if not raw:
                break
            beat = json.loads(raw)
            if beat.get("op") == "beat":
                try:
                    players = int(beat.get("players", 1))
                except (TypeError, ValueError):
                    players = 1
                room.players = min(room.max_players, max(1, players))
    except (OSError, ValueError, json.JSONDecodeError):
        pass
    finally:
        LOBBY.drop_control(conn)
        try:
            conn.close()
        except OSError:
            pass


def drop_bridge(ticket, tell):
    with BRIDGE_LOCK:
        pending = BRIDGES.pop(ticket, None)
    if pending is None:
        return None
    pending["timer"].cancel()
    forget_relay(pending["room"], pending["relay_key"])
    for conn in tell:
        fail(conn, "host-offline")
    return pending


def expire_bridge(ticket):
    with BRIDGE_LOCK:
        pending = BRIDGES.get(ticket)
    if pending is None:
        return
    with pending["lock"]:
        if pending["spliced"]:
            return
        pending["spliced"] = True
        leftover = list(pending["sides"].values())
        pending["sides"].clear()
    drop_bridge(ticket, leftover)
    pending["ready"].set()


def issue_ticket(conn, peer, message, account):
    try:
        room_id = int(message.get("room_id", 0))
    except (TypeError, ValueError):
        fail(conn, "bad-room")
        return
    room = LOBBY.get(room_id)
    if room is None:
        fail(conn, "no-room")
        return
    if room.players >= room.max_players:
        fail(conn, "full")
        return
    ticket = secrets.token_hex(16)
    relay = secrets.token_hex(8)
    relay_key = bytes.fromhex(relay)
    pending = {
        "room": room,
        "relay": relay,
        "relay_key": relay_key,
        "sides": {},
        "lock": threading.Lock(),
        "ready": threading.Event(),
        "spliced": False,
        "timer": None,
        "account": account,
    }
    if not reserve_bridge(ticket, pending):
        fail(conn, "busy")
        return
    with RELAY_LOCK:
        room.relays.append(relay_key)
        RELAYS[relay_key] = {"room": room, "guest": None}
    pending["timer"] = threading.Timer(JOIN_WAIT, expire_bridge, args=(ticket,))
    pending["timer"].daemon = True
    pending["timer"].start()
    try:
        send_line(room.control, {"op": "guest", "token": ticket, "from": room.peer[0], "relay": relay}, room.write_lock)
        send_line(conn, {"op": "ticket", "ticket": ticket, "relay": relay})
    except OSError:
        expire_bridge(ticket)
        fail(conn, "host-offline")
        return
    try:
        conn.close()
    except OSError:
        pass


def bridge(conn, peer, message):
    ticket = message.get("ticket")
    side = message.get("side")
    if not isinstance(ticket, str) or len(ticket) != 32 or side not in ("guest", "host"):
        fail(conn, "bad-ticket")
        return
    account = gate().session(message.get("account"), peer[0])
    if account is None:
        fail(conn, "login-required")
        return
    with BRIDGE_LOCK:
        pending = BRIDGES.get(ticket)
    if pending is None:
        fail(conn, "bad-ticket")
        return
    # The ticket is bound to the account that asked to join, and to the account
    # that owns the room. A shared public address is not a credential.
    expected = pending["account"] if side == "guest" else pending["room"].host_account
    if account != expected:
        fail(conn, "bad-ticket")
        return
    with pending["lock"]:
        if pending["spliced"] or side in pending["sides"]:
            fail(conn, "bad-ticket")
            return
        pending["sides"][side] = conn
        if len(pending["sides"]) < 2:
            owner = False
        else:
            pending["spliced"] = True
            pair = (pending["sides"]["guest"], pending["sides"]["host"])
            owner = True
    if not owner:
        # The second arrival owns both sockets from here.
        pending["ready"].wait(JOIN_WAIT + 1)
        return
    pending["timer"].cancel()
    pending["ready"].set()
    relay = pending["relay"]
    try:
        send_line(pair[0], {"op": "bridged", "relay": relay})
        send_line(pair[1], {"op": "bridged", "relay": relay})
        pair[0].settimeout(None)
        pair[1].settimeout(None)
        splice(pair[0], pair[1])
    except OSError:
        for held in pair:
            try:
                held.close()
            except OSError:
                pass
    finally:
        drop_bridge(ticket, [])


def dispatch(conn, peer, context):
    try:
        conn.settimeout(20)
        lead = conn.recv(1, socket.MSG_PEEK)
    except OSError:
        try:
            conn.close()
        except OSError:
            pass
        return
    if lead[:1] == b"\x16":
        try:
            conn = context.wrap_socket(conn, server_side=True)
        except ssl.SSLError:
            try:
                conn.close()
            except OSError:
                pass
            return
        handle(conn, peer, True)
        return
    handle(conn, peer, False)


def serve(bind, port, database=None, tls_cert=None, tls_key=None):
    global GATE
    if not tls_cert or not tls_key:
        raise SystemExit("TLS certificate and key are required")
    GATE = AccountGate(database or os.environ.get("EFLINK_AUTH_DB", "accounts.sqlite"))
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(tls_cert, tls_key)
    listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    listener.bind((bind, port))
    listener.listen(64)
    udp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    udp.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    udp.bind((bind, port))
    threading.Thread(target=relay_udp, args=(udp,), daemon=True).start()
    print(f"EF Link lobby on {bind}:{port} tcp+udp", flush=True)
    while True:
        conn, peer = listener.accept()
        if not reserve_connection(peer[0]):
            try:
                conn.close()
            except OSError:
                pass
            continue
        threading.Thread(target=serve_connection, args=(conn, peer, context), daemon=True).start()


def serve_connection(conn, peer, context):
    try:
        dispatch(conn, peer, context)
    finally:
        release_connection(peer[0])


def main():
    parser = argparse.ArgumentParser(description="EF Link room lobby and TCP relay")
    parser.add_argument("--bind", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=47617)
    parser.add_argument("--auth-db", default=os.environ.get("EFLINK_AUTH_DB", "accounts.sqlite"))
    parser.add_argument("--tls-cert", default=os.environ.get("EFLINK_TLS_CERT"))
    parser.add_argument("--tls-key", default=os.environ.get("EFLINK_TLS_KEY"))
    args = parser.parse_args()
    if args.port < 1 or args.port > 65535:
        raise SystemExit("port must be 1-65535")
    serve(args.bind, args.port, args.auth_db, args.tls_cert, args.tls_key)


if __name__ == "__main__":
    main()
