"""Stage a complete verified v3 catalog as compressed APK assets."""
import argparse
import hashlib
import re
from pathlib import Path
import shutil

from build_android_headwear_fixture import asset_profiles, bounded_json

MAX_COUNT = 898
MAX_TOTAL = 96 * 1024 * 1024
EXPECTED_COVERAGE_SHA256 = "7e4d833ded5b2d96ef0d2408aa62181dc39ebf7d104f7541bff89edf005ec0c6"


def stage(catalog, output):
    coverage_path = catalog / "coverage.json"
    if coverage_path.stat().st_size > 64 * 1024 * 1024:
        raise ValueError("metadata exceeds 64 MiB")
    coverage_bytes = coverage_path.read_bytes()
    if hashlib.sha256(coverage_bytes).hexdigest() != EXPECTED_COVERAGE_SHA256:
        raise ValueError("headwear v61 coverage fingerprint differs")
    coverage = bounded_json(catalog / "coverage.json")
    if coverage.get("schema") != 3 or len(coverage.get("included", [])) != MAX_COUNT:
        raise ValueError("headwear catalog must contain the complete accepted 898 v3 fixtures")
    # Windows Path ordering folds case; the shared ASCII manifest contract
    # requires ordinal filename ordering, matching Java String.compareTo.
    files = sorted(catalog.glob("*.behw"), key=lambda path: path.name)
    names = {entry["mesh"] + ".behw" for entry in coverage["included"]}
    if len(names) != MAX_COUNT or names != {p.name for p in files}:
        raise ValueError("catalog coverage/file identities differ")
    rows = []
    hashes = {}
    expected_hashes = {entry["mesh"]:entry.get("fixture_sha256") for entry in coverage["included"]}
    total = 0
    for path in files:
        if not re.fullmatch(r"[A-Za-z0-9_]{1,160}\.behw", path.name) or path.is_symlink():
            raise ValueError("unsafe asset identity")
        size = path.stat().st_size
        if not 0 < size <= 16 * 1024 * 1024:
            raise ValueError("asset size exceeds bound")
        total += size
        hashes[path.stem] = hashlib.sha256(path.read_bytes()).hexdigest()
        if hashes[path.stem] != expected_hashes[path.stem]:
            raise ValueError("catalog fixture fingerprint differs from coverage")
        rows.append(f"{path.name}\t{size}\t{hashes[path.stem]}\n")
    if total > MAX_TOTAL:
        raise ValueError("catalog exceeds 96 MiB")
    for profile in asset_profiles():
        if hashes.get(profile["mesh"]) != profile["accepted_fixture_sha256"]:
            raise ValueError("packaged asset differs from accepted profile")
    payload = "".join(rows).encode("utf-8")
    bundle_id = hashlib.sha256(payload).hexdigest()
    manifest = f"BEHWASSETS\t1\t61\t{bundle_id}\n".encode("utf-8") + payload
    if len(manifest) > 256 * 1024:
        raise ValueError("manifest exceeds bound")
    # This path is the sole Gradle-generated output, never a user resource tree.
    target = output / "headwear-v3"
    if target.is_symlink():
        raise ValueError("generated asset directory must not be a symlink")
    target.mkdir(parents=True, exist_ok=True)
    for path in files:
        if (target / path.name).is_symlink():
            raise ValueError("generated asset destination must not be a symlink")
        shutil.copyfile(path, target / path.name)
    for path in target.iterdir():
        if path.is_file() and path.name not in names and path.name != "manifest.tsv":
            path.unlink()
    (target / "manifest.tsv").write_bytes(manifest)
    print(f"headwear assets: count={len(files)} bytes={total} bundle={bundle_id}")
    return bundle_id


if __name__ == "__main__":
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument("--catalog", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    stage(args.catalog, args.output)
