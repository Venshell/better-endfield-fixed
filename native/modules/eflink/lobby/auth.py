"""QQ-mail accounts for the EF Link lobby.

The process that sends mail is configured on the server (SMTP_*). Callers must
already be on a mainland China address, and the code can only be entered from
the same address that requested it.
"""

import hashlib
import hmac
import os
import re
import secrets
import smtplib
import sqlite3
import threading
import time
from email.message import EmailMessage
from pathlib import Path

QQ_MAIL = re.compile(r"^[^\s@]+@qq\.com$")
RANGES = Path(__file__).with_name("cn-mainland.txt")
SEND_GAP = 60
EMAIL_WINDOW = 15 * 60
EMAIL_MAX = 3
DAY = 24 * 60 * 60
DAY_MAX = 100
VERIFY_WINDOW = 10 * 60
VERIFY_MAX = 20
CODE_TTL = 10 * 60
SESSION_TTL = 30 * DAY


def ipv4(value):
    parts = str(value).split(".")
    if len(parts) != 4:
        return None
    number = 0
    for part in parts:
        if not part.isdigit() or int(part) > 255:
            return None
        number = number * 256 + int(part)
    return number


def load_ranges(path=RANGES):
    ranges = []
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#") or not line.startswith("4 "):
            continue
        _, start, end = line.split()
        ranges.append((int(start), int(end)))
    if not ranges:
        raise RuntimeError("mainland IPv4 ranges are missing")
    return ranges


def contains(ranges, value):
    low, high, found = 0, len(ranges) - 1, -1
    while low <= high:
        mid = (low + high) // 2
        if ranges[mid][0] <= value:
            found = mid
            low = mid + 1
        else:
            high = mid - 1
    return found >= 0 and value <= ranges[found][1]


class AccountGate:
    def __init__(self, database, mailer=None):
        self.mailer = mailer or smtp_mail
        self.ranges = load_ranges()
        self.lock = threading.Lock()
        self.db = sqlite3.connect(database, check_same_thread=False)
        self.db.execute("PRAGMA journal_mode=WAL")
        self.db.executescript("""
            CREATE TABLE IF NOT EXISTS meta (name TEXT PRIMARY KEY, value TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS accounts (
                email TEXT PRIMARY KEY, created_at INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS challenges (
                id INTEGER PRIMARY KEY,
                email TEXT NOT NULL, ip TEXT NOT NULL, code_hash TEXT NOT NULL,
                expires_at INTEGER NOT NULL, attempts INTEGER NOT NULL DEFAULT 0);
            CREATE TABLE IF NOT EXISTS sessions (
                token_hash TEXT PRIMARY KEY, email TEXT NOT NULL,
                expires_at INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS events (
                id INTEGER PRIMARY KEY, kind TEXT NOT NULL, ip TEXT NOT NULL,
                email TEXT NOT NULL, created_at INTEGER NOT NULL);
            CREATE INDEX IF NOT EXISTS events_ip ON events(kind, ip, created_at);
            """)
        row = self.db.execute("SELECT value FROM meta WHERE name='pepper'").fetchone()
        if row is None:
            self.db.execute("INSERT INTO meta(name,value) VALUES ('pepper',?)", (secrets.token_hex(32),))
            self.db.commit()
        self.pepper = self.db.execute("SELECT value FROM meta WHERE name='pepper'").fetchone()[0]

    def close(self):
        self.db.close()

    def mainland(self, ip):
        number = ipv4(ip)
        return number is not None and contains(self.ranges, number)

    def request_code(self, email, ip):
        address = str(email or "").strip().lower()
        if not QQ_MAIL.fullmatch(address):
            return "qq-mail-only"
        if not self.mainland(ip):
            return "not-mainland"
        now = int(time.time())
        with self.lock:
            if self._count("send", "ip", ip, now - SEND_GAP):
                return "slow-down"
            if self._count("send", "email", address, now - EMAIL_WINDOW) >= EMAIL_MAX:
                return "mail-busy"
            if self._count("send", None, None, now - DAY) >= DAY_MAX:
                return "mail-daily"
            code = f"{secrets.randbelow(900000) + 100000}"
            self.db.execute("DELETE FROM challenges WHERE email=? OR expires_at<?", (address, now))
            self.db.execute(
                "INSERT INTO challenges(email,ip,code_hash,expires_at) VALUES (?,?,?,?)",
                (address, ip, self._hash(code), now + CODE_TTL))
            self.db.execute(
                "INSERT INTO events(kind,ip,email,created_at) VALUES ('send',?,?,?)",
                (ip, address, now))
            self.db.commit()
        try:
            self.mailer(address, code)
        except Exception:
            with self.lock:
                self.db.execute("DELETE FROM challenges WHERE email=?", (address,))
                self.db.commit()
            return "mail-failed"
        return ""

    def verify(self, email, code, ip):
        address = str(email or "").strip().lower()
        if not QQ_MAIL.fullmatch(address) or not re.fullmatch(r"\d{6}", str(code or "")):
            return None, "bad-code"
        if not self.mainland(ip):
            return None, "not-mainland"
        now = int(time.time())
        with self.lock:
            if self._count("verify", "ip", ip, now - VERIFY_WINDOW) >= VERIFY_MAX:
                return None, "verify-limit"
            self.db.execute(
                "INSERT INTO events(kind,ip,email,created_at) VALUES ('verify',?,?,?)",
                (ip, address, now))
            row = self.db.execute(
                "SELECT id,ip,code_hash,attempts FROM challenges WHERE email=? AND expires_at>? ORDER BY id DESC LIMIT 1",
                (address, now)).fetchone()
            if row is None or row[3] >= 5:
                self.db.commit()
                return None, "code-expired"
            if row[1] != ip:
                self.db.commit()
                return None, "same-network"
            if not hmac.compare_digest(row[2], self._hash(str(code))):
                self.db.execute("UPDATE challenges SET attempts=attempts+1 WHERE id=?", (row[0],))
                self.db.commit()
                return None, "bad-code"
            self.db.execute("DELETE FROM challenges WHERE email=?", (address,))
            self.db.execute("INSERT OR IGNORE INTO accounts(email,created_at) VALUES (?,?)", (address, now))
            token = secrets.token_urlsafe(32)
            self.db.execute(
                "INSERT INTO sessions(token_hash,email,expires_at) VALUES (?,?,?)",
                (self._hash(token), address, now + SESSION_TTL))
            self.db.commit()
            return token, ""

    def session(self, token, ip):
        if not isinstance(token, str) or not token or not self.mainland(ip):
            return None
        now = int(time.time())
        with self.lock:
            row = self.db.execute(
                "SELECT email FROM sessions WHERE token_hash=? AND expires_at>?",
                (self._hash(token), now)).fetchone()
            return row[0] if row else None

    def _count(self, kind, field, value, since):
        if field is None:
            row = self.db.execute(
                "SELECT COUNT(*) FROM events WHERE kind=? AND created_at>?", (kind, since)).fetchone()
        else:
            row = self.db.execute(
                f"SELECT COUNT(*) FROM events WHERE kind=? AND {field}=? AND created_at>?",
                (kind, value, since)).fetchone()
        return row[0]

    def _hash(self, value):
        return hashlib.sha256(f"{self.pepper}:{value}".encode()).hexdigest()


def smtp_mail(address, code):
    host = os.environ.get("SMTP_HOST", "")
    sender = os.environ.get("SMTP_FROM") or os.environ.get("SMTP_USER", "")
    if not host or not sender:
        raise RuntimeError("mail is not configured")
    port = int(os.environ.get("SMTP_PORT", "465"))
    message = EmailMessage()
    message["From"] = sender
    message["To"] = address
    message["Subject"] = "EF Link 验证码"
    message.set_content(f"你的联机注册验证码为：{code}\n\n10 分钟内有效，且只能在申请时的网络里填写。如果不是你发起的，可以忽略这封邮件。")
    with smtplib.SMTP_SSL(host, port, timeout=10) if port == 465 else smtplib.SMTP(host, port, timeout=10) as smtp:
        if port != 465:
            smtp.starttls()
        user, password = os.environ.get("SMTP_USER", ""), os.environ.get("SMTP_PASS", "")
        if user:
            smtp.login(user, password)
        smtp.send_message(message)
