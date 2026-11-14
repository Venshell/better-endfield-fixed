#!/usr/bin/env python3
"""Consistent snapshot of the EF Link account database."""

import argparse
import os
import sqlite3
import time
from pathlib import Path

NAME = "accounts-{stamp}.sqlite"


def backup(database, directory, keep=28):
    source_path = Path(database)
    if not source_path.is_file():
        raise SystemExit(f"account database not found: {source_path}")
    target_dir = Path(directory)
    target_dir.mkdir(parents=True, exist_ok=True)
    os.chmod(target_dir, 0o700)
    stamp = time.strftime("%Y%m%dT%H%M%S", time.gmtime()) + f"{time.time_ns() % 1000:03d}Z"
    final = target_dir / NAME.format(stamp=stamp)
    temporary = target_dir / f".{final.name}.partial"
    source = sqlite3.connect(source_path)
    try:
        copy = sqlite3.connect(temporary)
        try:
            source.backup(copy)
        finally:
            copy.close()
    finally:
        source.close()
    check = sqlite3.connect(f"file:{temporary}?mode=ro", uri=True)
    try:
        result = check.execute("PRAGMA integrity_check").fetchone()[0]
    finally:
        check.close()
    if result != "ok":
        temporary.unlink(missing_ok=True)
        raise SystemExit("backup failed the integrity check")
    temporary.replace(final)
    os.chmod(final, 0o600)
    older = sorted(path for path in target_dir.glob("accounts-*.sqlite") if path.is_file())
    while len(older) > keep:
        older.pop(0).unlink()
    return final


def main():
    parser = argparse.ArgumentParser(description="Back up the EF Link account database")
    parser.add_argument("--database", default=os.environ.get("EFLINK_AUTH_DB", "accounts.sqlite"))
    parser.add_argument("--directory", default=os.environ.get("EFLINK_BACKUP_DIR", "backups"))
    parser.add_argument("--keep", type=int, default=int(os.environ.get("EFLINK_BACKUP_KEEP", "28")))
    args = parser.parse_args()
    print(backup(args.database, args.directory, args.keep))


if __name__ == "__main__":
    main()
