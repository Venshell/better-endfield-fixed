import json
import socket
import sqlite3
import tempfile
import threading
import unittest
from pathlib import Path

import server
from auth import AccountGate
from backup_accounts import backup

PEER = ("114.114.114.114", 47617)


def read_json(sock):
    data = bytearray()
    while b"\n" not in data:
        chunk = sock.recv(4096)
        if not chunk:
            break
        data += chunk
    return json.loads(data.decode().split("\n", 1)[0])


def exchange(message, peer=PEER, secure=True):
    left, right = socket.socketpair()
    thread = threading.Thread(target=server.handle, args=(right, peer, secure), daemon=True)
    thread.start()
    left.sendall((json.dumps(message) + "\n").encode())
    data = bytearray()
    while b"\n" not in data:
        chunk = left.recv(4096)
        if not chunk:
            break
        data += chunk
    try:
        left.shutdown(socket.SHUT_RDWR)
    except OSError:
        pass
    left.close()
    thread.join(2)
    return json.loads(data.decode().split("\n", 1)[0])


class AccountTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.sent = []
        self.database = Path(self.directory.name) / "accounts.sqlite"
        server.LOBBY = server.Lobby()
        with server.RELAY_LOCK:
            server.RELAYS.clear()
            server.HOST_TOKENS.clear()
        with server.BRIDGE_LOCK:
            server.BRIDGES.clear()
        server.GATE = AccountGate(
            str(self.database),
            mailer=lambda address, code: self.sent.append((address, code)),
            rounds=1000)

    def tearDown(self):
        with server.BRIDGE_LOCK:
            pending = list(server.BRIDGES.values())
        for item in pending:
            item["timer"].cancel()
        server.GATE.close()
        self.directory.cleanup()

    def test_register_login_and_room_numbers(self):
        denied = exchange({"op": "list", "game": "endfield"})
        self.assertEqual(denied["error"], "login-required")
        duplicate_shape = exchange({
            "op": "register", "game": "endfield",
            "username": "End", "password": "correct horse", "email": "user@example.com",
        })
        self.assertEqual(duplicate_shape["error"], "目前仅支持 QQ 邮箱")
        sent = exchange({
            "op": "register", "game": "endfield",
            "username": "Ender", "password": "correct horse", "email": "Player@QQ.com",
        })
        self.assertEqual(sent["op"], "sent")
        again = exchange({
            "op": "register", "game": "endfield",
            "username": "Other", "password": "correct horse", "email": "10002@qq.com",
        })
        self.assertIn("60 秒", again["error"])
        connection = sqlite3.connect(self.database)
        try:
            stored = connection.execute(
                "SELECT salt, password_hash FROM challenges").fetchone()
        finally:
            connection.close()
        self.assertNotIn("correct horse", stored[0] + stored[1])
        session = exchange({
            "op": "verify", "game": "endfield", "username": "ender", "code": self.sent[0][1],
        })
        self.assertEqual(session["username"], "Ender")
        self.assertEqual(session["email"], "player@qq.com")
        clash = exchange({
            "op": "register", "game": "endfield",
            "username": "ENDER", "password": "another secret", "email": "10003@qq.com",
        })
        self.assertEqual(clash["error"], "用户名或邮箱已被注册")
        wrong = exchange({
            "op": "login", "game": "endfield", "username": "Ender", "password": "wrong password",
        })
        self.assertEqual(wrong["error"], "账号或密码不正确")
        logged = exchange({
            "op": "login", "game": "endfield", "username": "Ender", "password": "correct horse",
        })
        self.assertEqual(logged["op"], "session")
        listed = exchange({"op": "list", "game": "endfield", "account": logged["token"]})
        self.assertEqual(listed["rooms"], [])
        hosted = exchange({
            "op": "host", "game": "endfield", "account": logged["token"],
            "name": "Ender", "room_name": "大厅", "max_players": 4, "password": True, "version": "test",
        })
        self.assertEqual(hosted["op"], "hosted")
        self.assertGreaterEqual(hosted["room_id"], 100000)
        self.assertLessEqual(hosted["room_id"], 999999)

    def test_guest_leave_forgets_relay(self):
        sent = exchange({
            "op": "register", "game": "endfield",
            "username": "Ender", "password": "correct horse", "email": "10001@qq.com",
        })
        self.assertEqual(sent["op"], "sent")
        session = exchange({
            "op": "verify", "game": "endfield", "username": "Ender", "code": self.sent[0][1],
        })
        token = session["token"]
        control, remote = socket.socketpair()
        host_thread = threading.Thread(
            target=server.handle, args=(remote, PEER, True), daemon=True)
        host_thread.start()
        control.sendall((json.dumps({
            "op": "host", "game": "endfield", "account": token,
            "name": "Ender", "room_name": "大厅", "max_players": 4, "version": "test",
        }) + "\n").encode())
        hosted = read_json(control)
        self.assertEqual(hosted["op"], "hosted")
        guest = host_side = None
        try:
            joined = exchange({
                "op": "join", "game": "endfield", "account": token, "room_id": hosted["room_id"],
            })
            self.assertEqual(joined["op"], "ticket")
            relay_key = bytes.fromhex(joined["relay"])
            self.assertIn(relay_key, server.RELAYS)
            notice = read_json(control)
            self.assertEqual(notice["op"], "guest")
            self.assertEqual(notice["token"], joined["ticket"])

            def open_side(side):
                client, server_sock = socket.socketpair()
                worker = threading.Thread(
                    target=server.handle, args=(server_sock, PEER, False), daemon=True)
                worker.start()
                client.sendall((json.dumps({
                    "op": "bridge", "game": "endfield",
                    "ticket": joined["ticket"], "side": side,
                }) + "\n").encode())
                return client, worker

            guest, guest_worker = open_side("guest")
            host_side, host_worker = open_side("host")
            self.assertEqual(read_json(guest)["op"], "bridged")
            self.assertEqual(read_json(host_side)["op"], "bridged")
            guest.close()
            guest_worker.join(3)
            host_worker.join(3)
            self.assertFalse(guest_worker.is_alive())
            self.assertFalse(host_worker.is_alive())
            self.assertNotIn(relay_key, server.RELAYS)
            self.assertIsNotNone(server.LOBBY.get(hosted["room_id"]))
        finally:
            for held in (guest, host_side, control):
                if held is None:
                    continue
                try:
                    held.close()
                except OSError:
                    pass
            host_thread.join(2)

    def test_plaintext_rejects_account_material(self):
        denied = exchange({
            "op": "login", "game": "endfield", "username": "Ender", "password": "secret",
        }, secure=False)
        self.assertEqual(denied["error"], "tls-required")

    def test_backup_is_a_consistent_snapshot(self):
        server.GATE.db.execute(
            "INSERT INTO accounts(username,username_key,email,salt,password_hash,created_at) VALUES ('Ender','ender','10001@qq.com','aa','bb',1)")
        server.GATE.db.commit()
        directory = Path(self.directory.name) / "backups"
        directory.mkdir()
        (directory / "accounts-20000101T000000000Z.sqlite").write_text("obsolete")
        snapshot = backup(self.database, directory, keep=1)
        self.assertEqual(list(directory.glob("accounts-*.sqlite")), [snapshot])
        copy = sqlite3.connect(snapshot)
        self.assertEqual(copy.execute("SELECT username FROM accounts").fetchone()[0], "Ender")
        copy.close()


if __name__ == "__main__":
    unittest.main()
