import json
import socket
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
        server.GATE = AccountGate(
            str(Path(self.directory.name) / "accounts.sqlite"),
            mailer=lambda address, code: self.sent.append((address, code)))

    def tearDown(self):
        server.GATE.close()
        self.directory.cleanup()

    def test_registration_is_required_for_this_server(self):
        denied = exchange({"op": "list", "game": "endfield"})
        self.assertEqual(denied["error"], "login-required")
        self.assertEqual(exchange({"op": "code", "game": "endfield", "email": "user@example.com"})["error"], "qq-mail-only")
        self.assertEqual(exchange({"op": "code", "game": "endfield", "email": "10001@qq.com"}, ("8.8.8.8", 1))["error"], "not-mainland")
        sent = exchange({"op": "code", "game": "endfield", "email": "Player@QQ.com"})
        self.assertEqual(sent["op"], "sent")
        self.assertEqual(self.sent, [("player@qq.com", self.sent[0][1])])
        again = exchange({"op": "code", "game": "endfield", "email": "10002@qq.com"})
        self.assertEqual(again["error"], "slow-down")
        moved = exchange({"op": "verify", "game": "endfield", "email": "player@qq.com", "code": self.sent[0][1]}, ("223.5.5.5", 1))
        self.assertEqual(moved["error"], "same-network")
        session = exchange({"op": "verify", "game": "endfield", "email": "player@qq.com", "code": self.sent[0][1]})
        self.assertEqual(session["op"], "session")
        listed = exchange({"op": "list", "game": "endfield", "account": session["token"]})
        self.assertEqual(listed["op"], "rooms")
        self.assertEqual(listed["rooms"], [])
        abroad = exchange({"op": "list", "game": "endfield", "account": session["token"]}, ("8.8.8.8", 1))
        self.assertEqual(abroad["error"], "login-required")

    def test_backup_is_a_consistent_snapshot(self):
        database = Path(self.directory.name) / "accounts.sqlite"
        server.GATE.db.execute("INSERT INTO accounts(email,created_at) VALUES ('10001@qq.com',1)")
        server.GATE.db.commit()
        directory = Path(self.directory.name) / "backups"
        directory.mkdir()
        (directory / "accounts-20000101T000000000Z.sqlite").write_text("obsolete")
        snapshot = backup(database, directory, keep=1)
        self.assertEqual(list(directory.glob("accounts-*.sqlite")), [snapshot])
        self.assertEqual(snapshot.stat().st_mode & 0o777, 0o600)
        copy = __import__("sqlite3").connect(snapshot)
        self.assertEqual(copy.execute("SELECT email FROM accounts").fetchone()[0], "10001@qq.com")
        copy.close()


if __name__ == "__main__":
    unittest.main()
