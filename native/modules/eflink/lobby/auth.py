"""Accounts for the EF Link lobby.

Passwords are stored as PBKDF2-HMAC-SHA256 with a unique salt. The mail code is
checked by this process; the app only submits what the user typed.
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

USERNAME = re.compile(r"^[A-Za-z0-9_\u4e00-\u9fff]{2,16}$")
QQ_MAIL = re.compile(r"^[^\s@]+@qq\.com$")
RANGES = Path(__file__).with_name("cn-mainland.txt")
SEND_GAP = 60
EMAIL_WINDOW = 15 * 60
EMAIL_MAX = 3
DAY = 24 * 60 * 60
DAY_MAX = 100
VERIFY_WINDOW = 10 * 60
VERIFY_MAX = 20
LOGIN_MAX = 10
CODE_TTL = 10 * 60
SESSION_TTL = 30 * DAY
PBKDF2_ROUNDS = 600_000


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


def hash_password(password, salt=None, rounds=PBKDF2_ROUNDS):
    if salt is None:
        salt = secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, rounds)
    return salt.hex(), digest.hex()


def same_password(password, salt_hex, digest_hex, rounds):
    salt = bytes.fromhex(salt_hex)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, rounds)
    return hmac.compare_digest(digest.hex(), digest_hex)


class AccountGate:
    def __init__(self, database, mailer=None, rounds=PBKDF2_ROUNDS):
        self.mailer = mailer or smtp_mail
        self.rounds = rounds
        self.ranges = load_ranges()
        self.lock = threading.Lock()
        self.db = sqlite3.connect(database, check_same_thread=False)
        self.db.execute("PRAGMA journal_mode=WAL")
        self._migrate()
        self.db.executescript("""
            CREATE TABLE IF NOT EXISTS meta (name TEXT PRIMARY KEY, value TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS accounts (
                username TEXT PRIMARY KEY,
                username_key TEXT NOT NULL UNIQUE,
                email TEXT NOT NULL UNIQUE,
                salt TEXT NOT NULL,
                password_hash TEXT NOT NULL,
                created_at INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS challenges (
                id INTEGER PRIMARY KEY,
                username TEXT NOT NULL,
                username_key TEXT NOT NULL,
                email TEXT NOT NULL,
                ip TEXT NOT NULL,
                code_hash TEXT NOT NULL,
                salt TEXT NOT NULL,
                password_hash TEXT NOT NULL,
                expires_at INTEGER NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0);
            CREATE TABLE IF NOT EXISTS sessions (
                token_hash TEXT PRIMARY KEY,
                username TEXT NOT NULL,
                email TEXT NOT NULL,
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
        self.dummy_salt, self.dummy_hash = hash_password("not-a-user", rounds=rounds)

    def close(self):
        self.db.close()

    def mainland(self, ip):
        number = ipv4(ip)
        return number is not None and contains(self.ranges, number)

    def register(self, username, password, email, ip):
        name, reason = self._username(username)
        if reason:
            return reason
        reason = self._password(password, name)
        if reason:
            return reason
        address = self._email(email)
        if address is None:
            return "目前仅支持 QQ 邮箱"
        if not self.mainland(ip):
            return "目前仅限中国大陆网络"
        now = int(time.time())
        key = name.casefold()
        with self.lock:
            if self._taken(key, address):
                return "用户名或邮箱已被注册"
            if self._count("send", "ip", ip, now - SEND_GAP):
                return "同一网络 60 秒内只能发送一次验证码"
            if self._count("send", "email", address, now - EMAIL_WINDOW) >= EMAIL_MAX:
                return "该邮箱发送过于频繁，请稍后再试"
            if self._count("send", None, None, now - DAY) >= DAY_MAX:
                return "今日验证码发送已达上限"
            code = f"{secrets.randbelow(900000) + 100000}"
            salt, digest = hash_password(password, rounds=self.rounds)
            self.db.execute("DELETE FROM challenges WHERE username_key=? OR email=? OR expires_at<?", (key, address, now))
            self.db.execute(
                """INSERT INTO challenges(username,username_key,email,ip,code_hash,salt,password_hash,expires_at)
                   VALUES (?,?,?,?,?,?,?,?)""",
                (name, key, address, ip, self._hash(code), salt, digest, now + CODE_TTL))
            self.db.execute(
                "INSERT INTO events(kind,ip,email,created_at) VALUES ('send',?,?,?)",
                (ip, address, now))
            self.db.commit()
        try:
            self.mailer(address, code)
        except Exception:
            with self.lock:
                self.db.execute("DELETE FROM challenges WHERE username_key=?", (key,))
                self.db.commit()
            return "验证码邮件发送失败"
        return ""

    def verify(self, username, code, ip):
        name, reason = self._username(username)
        if reason or not re.fullmatch(r"\d{6}", str(code or "")):
            return None, "", "", "验证码无效或已过期"
        if not self.mainland(ip):
            return None, "", "", "目前仅限中国大陆网络"
        now = int(time.time())
        key = name.casefold()
        with self.lock:
            if self._count("verify", "ip", ip, now - VERIFY_WINDOW) >= VERIFY_MAX:
                return None, "", "", "当前网络验证次数过多，请稍后再试"
            self.db.execute(
                "INSERT INTO events(kind,ip,email,created_at) VALUES ('verify',?,?,?)",
                (ip, key, now))
            row = self.db.execute(
                """SELECT id,ip,code_hash,attempts,email,salt,password_hash,username
                   FROM challenges WHERE username_key=? AND expires_at>? ORDER BY id DESC LIMIT 1""",
                (key, now)).fetchone()
            if row is None or row[3] >= 5:
                self.db.commit()
                return None, "", "", "验证码无效或已过期"
            if row[1] != ip:
                self.db.commit()
                return None, "", "", "请在申请验证码的同一网络下完成验证"
            if not hmac.compare_digest(row[2], self._hash(str(code))):
                self.db.execute("UPDATE challenges SET attempts=attempts+1 WHERE id=?", (row[0],))
                self.db.commit()
                return None, "", "", "验证码无效或已过期"
            if self._taken(key, row[4]):
                self.db.execute("DELETE FROM challenges WHERE username_key=?", (key,))
                self.db.commit()
                return None, "", "", "用户名或邮箱已被注册"
            self.db.execute("DELETE FROM challenges WHERE username_key=?", (key,))
            self.db.execute(
                "INSERT INTO accounts(username,username_key,email,salt,password_hash,created_at) VALUES (?,?,?,?,?,?)",
                (row[7], key, row[4], row[5], row[6], now))
            token = secrets.token_urlsafe(32)
            self.db.execute(
                "INSERT INTO sessions(token_hash,username,email,expires_at) VALUES (?,?,?,?)",
                (self._hash(token), row[7], row[4], now + SESSION_TTL))
            self.db.commit()
            return token, row[7], row[4], ""

    def login(self, username, password, ip):
        name, reason = self._username(username)
        if reason or not isinstance(password, str):
            return None, "", "", "账号或密码不正确"
        if not self.mainland(ip):
            return None, "", "", "目前仅限中国大陆网络"
        now = int(time.time())
        key = name.casefold()
        with self.lock:
            if self._count("login", "ip", ip, now - VERIFY_WINDOW) >= LOGIN_MAX:
                return None, "", "", "当前网络登录次数过多，请稍后再试"
            self.db.execute(
                "INSERT INTO events(kind,ip,email,created_at) VALUES ('login',?,?,?)",
                (ip, key, now))
            row = self.db.execute(
                "SELECT username,email,salt,password_hash FROM accounts WHERE username_key=?",
                (key,)).fetchone()
            if row is None or not same_password(password, row[2], row[3], self.rounds):
                if row is None:
                    same_password(password, self.dummy_salt, self.dummy_hash, self.rounds)
                self.db.commit()
                return None, "", "", "账号或密码不正确"
            token = secrets.token_urlsafe(32)
            self.db.execute(
                "INSERT INTO sessions(token_hash,username,email,expires_at) VALUES (?,?,?,?)",
                (self._hash(token), row[0], row[1], now + SESSION_TTL))
            self.db.commit()
            return token, row[0], row[1], ""

    def session(self, token, ip):
        if not isinstance(token, str) or not token or not self.mainland(ip):
            return None
        now = int(time.time())
        with self.lock:
            row = self.db.execute(
                "SELECT username FROM sessions WHERE token_hash=? AND expires_at>?",
                (self._hash(token), now)).fetchone()
            return row[0] if row else None

    def _migrate(self):
        names = {row[1] for row in self.db.execute("PRAGMA table_info(accounts)")}
        if names and "username" not in names:
            self.db.executescript("""
                DROP TABLE IF EXISTS accounts;
                DROP TABLE IF EXISTS challenges;
                DROP TABLE IF EXISTS sessions;
                """)

    def _username(self, value):
        name = str(value or "").strip()
        if not USERNAME.fullmatch(name):
            return "", "用户名须为 2–16 位字母、数字、下划线或汉字"
        return name, ""

    def _password(self, value, username):
        if not isinstance(value, str) or len(value) < 8 or len(value) > 128:
            return "密码至少 8 位"
        if value.casefold() == username.casefold():
            return "密码不能和用户名相同"
        return ""

    def _email(self, value):
        address = str(value or "").strip().lower()
        return address if QQ_MAIL.fullmatch(address) else None

    def _taken(self, key, address):
        row = self.db.execute(
            "SELECT 1 FROM accounts WHERE username_key=? OR email=?", (key, address)).fetchone()
        return row is not None

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
    if port == 465:
        smtp = smtplib.SMTP_SSL(host, port, timeout=10)
    else:
        smtp = smtplib.SMTP(host, port, timeout=10)
    try:
        if port != 465:
            smtp.starttls()
        user, password = os.environ.get("SMTP_USER", ""), os.environ.get("SMTP_PASS", "")
        if user:
            smtp.login(user, password)
        smtp.send_message(message)
    finally:
        smtp.close()
