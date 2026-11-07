"""Packaging gate against the complete source-generated Android catalog."""
import os
import hashlib
from pathlib import Path
import tempfile
import shutil
import unittest
import zipfile

import fetch_android_headwear_catalog as catalog_import

import package_android_headwear_assets as assets

EXPECTED_COVERAGE_SHA256 = "7e4d833ded5b2d96ef0d2408aa62181dc39ebf7d104f7541bff89edf005ec0c6"


class CatalogImportBoundary(unittest.TestCase):
    def test_requires_explicit_current_source_without_creating_output(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "catalog"
            with self.assertRaisesRegex(ValueError, "provide --archive"):
                catalog_import.fetch(output)
            self.assertFalse(output.exists())

    def test_rejects_non_https_before_network_or_output(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "catalog"
            with self.assertRaisesRegex(ValueError, "must use HTTPS"):
                catalog_import.fetch(output, url="http://example.invalid/catalog.zip")
            self.assertFalse(output.exists())

    def test_archive_path_escape_rejected_before_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / "catalog.zip"
            with zipfile.ZipFile(archive, "w") as package:
                package.writestr("catalog-bundled/coverage.json", "{}")
                for index in range(assets.MAX_COUNT - 1):
                    package.writestr(f"catalog-bundled/fixture_{index}.behw", b"fixture")
                package.writestr("catalog-bundled/../escape.behw", b"escape")
            output = root / "catalog"
            with self.assertRaisesRegex(ValueError, "unsafe headwear archive"):
                catalog_import.fetch(output, archive=archive)
            self.assertFalse(output.exists())
            self.assertFalse((root / "escape.behw").exists())


@unittest.skipUnless(os.environ.get("HEADWEAR_PACKAGE_CATALOG"), "complete catalog not configured")
class AssetPackaging(unittest.TestCase):
    def test_coverage_must_be_the_pinned_v61_catalog_before_output_changes(self):
        catalog = Path(os.environ["HEADWEAR_PACKAGE_CATALOG"])
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            copied = root / "catalog"
            shutil.copytree(catalog, copied)
            output = root / "output/headwear-v3"
            output.mkdir(parents=True)
            marker = output / "manifest.tsv"
            marker.write_bytes(b"previous valid manifest")
            coverage = copied / "coverage.json"
            coverage.write_bytes(coverage.read_bytes() + b" ")
            with self.assertRaisesRegex(ValueError, "coverage fingerprint"):
                assets.stage(copied, output.parent)
            self.assertEqual(marker.read_bytes(), b"previous valid manifest")
            coverage.write_bytes((catalog / "coverage.json").read_bytes())
            (copied / "Unexpected_extra.behw").write_bytes(b"extra")
            with self.assertRaisesRegex(ValueError, "identities differ"):
                assets.stage(copied, output.parent)
            self.assertEqual(marker.read_bytes(), b"previous valid manifest")
            (copied / "Unexpected_extra.behw").unlink()
            source = next(copied.glob("*.behw"))
            original = source.read_bytes()
            source.write_bytes(bytes([original[0] ^ 1]) + original[1:])
            with self.assertRaisesRegex(ValueError, "fingerprint differs"):
                assets.stage(copied, output.parent)
            self.assertEqual(marker.read_bytes(), b"previous valid manifest")
            source.unlink()
            with self.assertRaisesRegex(ValueError, "identities differ"):
                assets.stage(copied, output.parent)
            self.assertEqual(list(output.iterdir()), [marker])

    def test_complete_catalog_stages_all_898_pinned_items_and_bounded_manifest(self):
        catalog = Path(os.environ["HEADWEAR_PACKAGE_CATALOG"])
        coverage_bytes = (catalog / "coverage.json").read_bytes()
        self.assertEqual(hashlib.sha256(coverage_bytes).hexdigest(), EXPECTED_COVERAGE_SHA256)
        coverage = assets.bounded_json(catalog / "coverage.json")
        self.assertEqual(len(coverage["included"]), 898)
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            bundle = assets.stage(catalog, output)
            manifest = (output / "headwear-v3/manifest.tsv").read_bytes()
            self.assertTrue(manifest.startswith(f"BEHWASSETS\t1\t61\t{bundle}\n".encode()))
            self.assertEqual(len(manifest.splitlines()), 899)
            names = [row.split(b"\t")[0] for row in manifest.splitlines()[1:]]
            self.assertEqual(names, sorted(names))
            self.assertEqual(len(names), 898)
            self.assertEqual(set(names), {entry["mesh"].encode() + b".behw" for entry in coverage["included"]})
            rows = {row.split(b"\t")[0].decode(): row.split(b"\t") for row in manifest.splitlines()[1:]}
            self.assertEqual(sum(int(row[1]) for row in rows.values()), 83308869)
            for entry in coverage["included"]:
                row = rows[entry["mesh"] + ".behw"]
                source = catalog / (entry["mesh"] + ".behw")
                self.assertEqual(int(row[1]), entry["fixture_bytes"])
                self.assertEqual(row[2].decode(), entry["fixture_sha256"])
                self.assertEqual(hashlib.sha256(source.read_bytes()).hexdigest(), entry["fixture_sha256"])
            for source in catalog.glob("*.behw"):
                self.assertEqual(source.read_bytes(), (output / "headwear-v3" / source.name).read_bytes())


if __name__ == "__main__":
    unittest.main()
