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


def exchange(message, peer=PEER):
    left, right = socket.socketpair()
    thread = threading.Thread(target=server.handle, args=(right, peer), daemon=True)
    thread.start()
    left.sendall((json.dumps(message) + "\n").encode())
    data = bytearray()
    while b"\n" not in data:
        chunk = left.recv(4096)
        if not chunk:
            break
        data += chunk
    left.close()
    thread.join(2)
    return json.loads(data.decode().split("\n", 1)[0])


class AccountTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.sent = []
        self.database = Path(self.directory.name) / "accounts.sqlite"
        server.GATE = AccountGate(
            str(self.database),
            mailer=lambda address, code: self.sent.append((address, code)),
            rounds=1000)

    def tearDown(self):
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
        stored = sqlite3.connect(self.database).execute(
            "SELECT salt, password_hash FROM challenges").fetchone()
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
