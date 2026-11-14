import json
import socket
import sqlite3
import tempfile
import threading
import time
import unittest
from pathlib import Path

import server
from auth import AccountGate
from backup_accounts import backup

PEER = ("203.0.113.10", 47617)


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
        with server.CONNECTION_LOCK:
            server.CONNECTIONS.clear()
        server.GATE = AccountGate(
            str(self.database),
            mailer=lambda address, code: self.sent.append((address, code)),
            rounds=1000)

    def tearDown(self):
        with server.BRIDGE_LOCK:
            pending = list(server.BRIDGES.values())
        for item in pending:
            timer = item.get("timer")
            if timer is not None:
                timer.cancel()
        server.GATE.close()
        self.directory.cleanup()

    def test_register_login_and_room_numbers(self):
        denied = exchange({"op": "list", "game": "endfield"})
        self.assertEqual(denied["error"], "login-required")
        duplicate_shape = exchange({
            "op": "register", "game": "endfield",
            "username": "End", "password": "example-pass", "email": "not-an-email",
        })
        self.assertEqual(duplicate_shape["error"], "邮箱格式不正确")
        sent = exchange({
            "op": "register", "game": "endfield",
            "username": "Ender", "password": "example-pass", "email": "Player@Example.com",
        })
        self.assertEqual(sent["op"], "sent")
        again = exchange({
            "op": "register", "game": "endfield",
            "username": "Other", "password": "example-pass", "email": "other@example.com",
        })
        self.assertIn("60 秒", again["error"])
        connection = sqlite3.connect(self.database)
        try:
            stored = connection.execute(
                "SELECT salt, password_hash FROM challenges").fetchone()
        finally:
            connection.close()
        self.assertNotIn("example-pass", stored[0] + stored[1])
        session = exchange({
            "op": "verify", "game": "endfield", "username": "ender", "code": self.sent[0][1],
        })
        self.assertEqual(session["username"], "Ender")
        self.assertEqual(session["email"], "player@example.com")
        clash = exchange({
            "op": "register", "game": "endfield",
            "username": "ENDER", "password": "other-secret", "email": "third@example.com",
        })
        self.assertEqual(clash["error"], "用户名或邮箱已被注册")
        wrong = exchange({
            "op": "login", "game": "endfield", "username": "Ender", "password": "wrong-password",
        })
        self.assertEqual(wrong["error"], "账号或密码不正确")
        logged = exchange({
            "op": "login", "game": "endfield", "username": "Ender", "password": "example-pass",
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
            "username": "Ender", "password": "example-pass", "email": "player@example.com",
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
            "name": "冒名", "room_name": "大厅", "max_players": 4, "version": "test",
        }) + "\n").encode())
        hosted = read_json(control)
        self.assertEqual(hosted["op"], "hosted")
        listed = exchange({"op": "list", "game": "endfield", "account": token})
        self.assertEqual(listed["rooms"][0]["host"], "Ender")
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
                    target=server.handle, args=(server_sock, PEER, True), daemon=True)
                worker.start()
                client.sendall((json.dumps({
                    "op": "bridge", "game": "endfield", "account": token,
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
            "op": "login", "game": "endfield", "username": "Ender", "password": "example-pass",
        }, secure=False)
        self.assertEqual(denied["error"], "tls-required")
        bridged = exchange({
            "op": "bridge", "game": "endfield", "ticket": "a" * 32, "side": "guest",
        }, secure=False)
        self.assertEqual(bridged["error"], "tls-required")

    def test_backup_is_a_consistent_snapshot(self):
        server.GATE.db.execute(
            "INSERT INTO accounts(username,username_key,email,salt,password_hash,created_at) VALUES ('Ender','ender','player@example.com','aa','bb',1)")
        server.GATE.db.commit()
        directory = Path(self.directory.name) / "backups"
        directory.mkdir()
        (directory / "accounts-20000101T000000000Z.sqlite").write_text("obsolete")
        snapshot = backup(self.database, directory, keep=1)
        self.assertEqual(list(directory.glob("accounts-*.sqlite")), [snapshot])
        copy = sqlite3.connect(snapshot)
        self.assertEqual(copy.execute("SELECT username FROM accounts").fetchone()[0], "Ender")
        copy.close()

    def test_old_schema_is_left_in_place(self):
        path = Path(self.directory.name) / "legacy.sqlite"
        db = sqlite3.connect(path)
        db.execute("CREATE TABLE accounts (email TEXT PRIMARY KEY, created_at INTEGER NOT NULL)")
        db.execute("INSERT INTO accounts(email, created_at) VALUES ('player@example.com', 1)")
        db.commit()
        db.close()
        with self.assertRaises(SystemExit) as raised:
            AccountGate(str(path), mailer=lambda address, code: None, rounds=1000)
        self.assertIn("旧结构", str(raised.exception))
        kept = sqlite3.connect(path)
        try:
            self.assertEqual(kept.execute("SELECT email FROM accounts").fetchone()[0], "player@example.com")
        finally:
            kept.close()

    def test_expired_account_rows_are_removed(self):
        now = int(time.time())
        day = 24 * 60 * 60
        server.GATE.db.execute(
            "INSERT INTO events(kind,ip,email,created_at) VALUES ('send','203.0.113.10','old@example.com',?)",
            (now - day - 5,))
        server.GATE.db.execute(
            "INSERT INTO events(kind,ip,email,created_at) VALUES ('send','203.0.113.10','new@example.com',?)",
            (now,))
        server.GATE.db.execute(
            "INSERT INTO sessions(token_hash,username,email,expires_at) VALUES ('old','Ender','player@example.com',?)",
            (now - 1,))
        server.GATE.db.execute(
            "INSERT INTO sessions(token_hash,username,email,expires_at) VALUES ('new','Ender','player@example.com',?)",
            (now + day,))
        server.GATE.db.execute(
            """INSERT INTO challenges(username,username_key,email,ip,code_hash,salt,password_hash,expires_at)
               VALUES ('Ender','ender','player@example.com','203.0.113.10','aa','bb','cc',?)""",
            (now - 1,))
        server.GATE.db.commit()
        server.GATE._purge(now, force=True)
        self.assertEqual(server.GATE.db.execute("SELECT email FROM events").fetchone()[0], "new@example.com")
        self.assertEqual(server.GATE.db.execute("SELECT token_hash FROM sessions").fetchone()[0], "new")
        self.assertIsNone(server.GATE.db.execute("SELECT id FROM challenges").fetchone())

    def test_pending_joins_and_connections_are_bounded(self):
        first = {"spliced": False, "account": "Ender"}
        self.assertTrue(server.reserve_bridge("a" * 32, dict(first)))
        self.assertTrue(server.reserve_bridge("b" * 32, dict(first)))
        self.assertFalse(server.reserve_bridge("c" * 32, dict(first)))
        self.assertNotIn("c" * 32, server.BRIDGES)
        for index in range(server.MAX_PENDING_BRIDGES - 2):
            ticket = f"{index:032x}"
            self.assertTrue(server.reserve_bridge(ticket, {"spliced": False, "account": f"user{index}"}))
        self.assertFalse(server.reserve_bridge("e" * 32, {"spliced": False, "account": "other"}))
        self.assertTrue(server.reserve_connection("203.0.113.1"))
        self.assertTrue(server.reserve_connection("203.0.113.1"))
        old_per_ip = server.MAX_PER_IP
        server.MAX_PER_IP = 2
        try:
            self.assertFalse(server.reserve_connection("203.0.113.1"))
        finally:
            server.MAX_PER_IP = old_per_ip
        server.release_connection("203.0.113.1")
        server.release_connection("203.0.113.1")


if __name__ == "__main__":
    unittest.main()
