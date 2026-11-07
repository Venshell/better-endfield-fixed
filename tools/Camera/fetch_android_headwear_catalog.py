"""Import the pinned v61 catalog from an explicit archive or configured HTTPS URL."""

import argparse
import os
from pathlib import Path
import re
import shutil
import tempfile
import urllib.request
import zipfile

from package_android_headwear_assets import MAX_COUNT, stage

EXPECTED_FILES = MAX_COUNT + 1
MAX_UNCOMPRESSED_BYTES = 100 * 1024 * 1024
MAX_ARCHIVE_BYTES = 100 * 1024 * 1024


def fetch(output: Path, archive: Path | None = None, url: str | None = None) -> Path:
    if output.is_symlink():
        raise ValueError("headwear output must not be a symlink")
    output = output.resolve()
    if output.exists() and any(output.iterdir()):
        raise ValueError(f"headwear output must be empty: {output}")
    if (archive is None) == (not url):
        raise ValueError("provide --archive or HEADWEAR_CATALOG_URL/--url for the pinned v61 catalog")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="headwear-import-", dir=output.parent) as temp:
        temporary = Path(temp)
        if archive is None:
            if not url.startswith("https://"):
                raise ValueError("headwear catalog URL must use HTTPS")
            archive = temporary / "catalog.zip"
            total = 0
            with urllib.request.urlopen(url, timeout=30) as source, archive.open("xb") as sink:
                if not source.geturl().startswith("https://"):
                    raise ValueError("headwear download redirected outside HTTPS")
                while chunk := source.read(1024 * 1024):
                    total += len(chunk)
                    if total > MAX_ARCHIVE_BYTES:
                        raise ValueError("headwear archive exceeds size bound")
                    sink.write(chunk)
        if archive.stat().st_size > MAX_ARCHIVE_BYTES:
            raise ValueError("headwear archive exceeds size bound")
        catalog = temporary / "catalog-bundled"
        with zipfile.ZipFile(archive) as package:
            files = [entry for entry in package.infolist() if not entry.is_dir()]
            names = [entry.filename for entry in files]
            if (len(files) != EXPECTED_FILES or len(set(names)) != EXPECTED_FILES
                    or "catalog-bundled/coverage.json" not in names
                    or sum(entry.file_size for entry in files) > MAX_UNCOMPRESSED_BYTES):
                raise ValueError("headwear archive has an invalid catalog inventory")
            for entry in files:
                name = entry.filename
                leaf = name.removeprefix("catalog-bundled/")
                if (not name.startswith("catalog-bundled/")
                        or not (leaf == "coverage.json" or re.fullmatch(r"[A-Za-z0-9_]{1,160}\.behw", leaf))
                        or entry.file_size > 16 * 1024 * 1024
                        or (entry.external_attr >> 16) & 0o170000 == 0o120000):
                    raise ValueError("unsafe headwear archive entry")
            catalog.mkdir()
            for entry in files:
                with package.open(entry) as source, (catalog / Path(entry.filename).name).open("xb") as sink:
                    shutil.copyfileobj(source, sink)
        # The producer pins coverage bytes, every fixture hash, inventory and profiles.
        # No imported catalog becomes visible before that single validation owner passes.
        stage(catalog, temporary / "verified")
        output.mkdir(exist_ok=True)
        catalog.rename(output / "catalog-bundled")
    print(f"headwear catalog: {output / 'catalog-bundled'} ({EXPECTED_FILES} files, game versionCode 61)")
    return output / "catalog-bundled"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--archive", type=Path)
    parser.add_argument("--url", default=os.environ.get("HEADWEAR_CATALOG_URL"))
    args = parser.parse_args()
    fetch(args.output, args.archive, args.url)
