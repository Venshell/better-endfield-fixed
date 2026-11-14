#!/usr/bin/env python3
"""EF Link lobby for game.venshell.top/endfield.

Players dial TCP and UDP on the same port. Each TCP line is JSON and carries
"game":"endfield". This process lists rooms, splices each guest's TCP stream
to the host, and relays UDP the same way. Phones only talk to this server, so
symmetric NAT does not matter. Bytes after the handshake are EF Link frames.
A UDP datagram is an 8-byte channel token plus one EF Link datagram.

    python3 server.py --bind 0.0.0.0 --port 47617
"""

import argparse
import json
import secrets
import socket
import threading

GAME = "endfield"
MAX_LINE = 8192
MAX_ROOMS = 64
JOIN_WAIT = 8


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
            self.rooms[room.id] = room
            self.by_control[room.control] = room
            return True

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
HOST_TOKENS = {}
RELAYS = {}


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
            try:
                dst.shutdown(socket.SHUT_WR)
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
        token, payload = data[:8], data[8:]
        with RELAY_LOCK:
            room = HOST_TOKENS.get(token)
            if room is not None:
                room.host_udp = addr
                continue
            channel = RELAYS.get(token)
            if channel is None:
                continue
            host = channel["room"].host_udp
            if host is not None and addr == host:
                target = channel["guest"]
            else:
                channel["guest"] = addr
                target = host
        if target is not None and payload:
            try:
                sock.sendto(data, target)
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


def handle(conn, peer):
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
        if op == "list":
            send_line(conn, {"op": "rooms", "rooms": LOBBY.snapshot()})
            conn.close()
            return
        if op == "host":
            host(conn, peer, message)
            return
        if op == "join":
            join(conn, message)
            return
        if op == "accept":
            accept(conn, message)
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


def host(conn, peer, message):
    name = clip(message.get("room_name"), 64) or "房间"
    host_name = clip(message.get("name"), 64) or "房主"
    version = clip(message.get("version"), 32)
    try:
        max_players = int(message.get("max_players", 8))
    except (TypeError, ValueError):
        max_players = 8
    max_players = min(16, max(2, max_players))
    room = Room(secrets.randbits(31) or 1, name, host_name, version, max_players,
                bool(message.get("password")), conn, peer)
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


def join(conn, message):
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
    token = secrets.token_hex(16)
    relay = secrets.token_hex(8)
    pending = {"event": threading.Event(), "socket": None}
    relay_key = bytes.fromhex(relay)
    with RELAY_LOCK:
        room.relays.append(relay_key)
        RELAYS[relay_key] = {"room": room, "guest": None}
    with room.lock:
        room.pending[token] = pending
    try:
        send_line(room.control, {"op": "guest", "token": token, "from": room.peer[0], "relay": relay}, room.write_lock)
    except OSError:
        with room.lock:
            room.pending.pop(token, None)
        forget_relay(room, relay_key)
        fail(conn, "host-offline")
        return
    if not pending["event"].wait(JOIN_WAIT) or pending["socket"] is None:
        with room.lock:
            room.pending.pop(token, None)
        forget_relay(room, relay_key)
        fail(conn, "host-offline")
        return
    other = pending["socket"]
    try:
        send_line(conn, {"op": "bridged", "relay": relay})
        send_line(other, {"op": "bridged", "relay": relay})
    except OSError:
        conn.close()
        other.close()
        return
    conn.settimeout(None)
    other.settimeout(None)
    splice(conn, other)


def accept(conn, message):
    token = message.get("token")
    if not isinstance(token, str) or len(token) != 32:
        fail(conn, "bad-token")
        return
    with LOBBY.lock:
        rooms = list(LOBBY.rooms.values())
    pending = None
    owner = None
    for room in rooms:
        with room.lock:
            found = room.pending.pop(token, None)
        if found is not None:
            pending = found
            owner = room
            break
    if pending is None:
        fail(conn, "bad-token")
        return
    pending["socket"] = conn
    pending["event"].set()
    # The join thread owns this socket from here. Do not close it.
    del owner


def serve(bind, port):
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
        threading.Thread(target=handle, args=(conn, peer), daemon=True).start()


def main():
    parser = argparse.ArgumentParser(description="EF Link room lobby and TCP relay")
    parser.add_argument("--bind", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=47617)
    args = parser.parse_args()
    if args.port < 1 or args.port > 65535:
        raise SystemExit("port must be 1-65535")
    serve(args.bind, args.port)


if __name__ == "__main__":
    main()
