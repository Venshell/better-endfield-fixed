import json
import tempfile
import unittest
from pathlib import Path

from generate_android_lod_relations import build_rows


class AndroidLodRelationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.native = root / "native"
        self.native.mkdir()
        self.android = root / "android.json"
        self.source = self.native / "chr_0005_chen.json"
        self.source_data = {
            "character_id": "chr_0005_chen",
            "platform": "android-arm64",
            "source_snapshot": {"manifest_version": "snapshot-A"},
            "ui_resource": "chr_0005_chen_uimodel",
            "world_resource": "chr_0005_chen_postmodel",
            "components": {"0": {
                "mesh_name": "S_actor_chen_body_01_lod0",
                "evidence": {"observations": {"chr_0005_chen_uimodel": {
                    "path": "chr_0005_chen_uimodel/Mesh_all/lod0/S_actor_chen_body_01_lod0"}}},
            }},
        }
        self.android_data = {"platform": "Android", "manifestVersion": "snapshot-A", "characters": [{
            "characterId": "chr_0005_chen", "modelAssets": [{
                "path": "asset##S_actor_chen_body_01_lod1_8"}]}]}
        self.save()

    def save(self):
        self.source.write_text(json.dumps(self.source_data), encoding="utf-8")
        self.android.write_text(json.dumps(self.android_data), encoding="utf-8")

    def test_observed_lod1_preserves_suffix(self):
        rows = build_rows(self.native, self.android, "snapshot-A")
        self.assertEqual(1, len(rows))
        self.assertEqual("S_actor_chen_body_01_lod1_8", rows[0]["target_mesh"])
        self.assertEqual("Mesh_all/lod1/S_actor_chen_body_01_lod1", rows[0]["target_path"])

    def test_no_android_observation_yields_no_relation(self):
        self.android_data["characters"][0]["modelAssets"] = []
        self.save()
        self.assertEqual([], build_rows(self.native, self.android, "snapshot-A"))

    def test_snapshot_mismatch_is_rejected(self):
        self.source_data["source_snapshot"]["manifest_version"] = "snapshot-B"
        self.save()
        with self.assertRaisesRegex(ValueError, "snapshot mismatch"):
            build_rows(self.native, self.android, "snapshot-A")

    def test_windows_catalog_is_rejected(self):
        self.android_data["platform"] = "Windows"
        self.save()
        with self.assertRaisesRegex(ValueError, "platform=Android"):
            build_rows(self.native, self.android, "snapshot-A")

    def test_windows_source_catalog_is_rejected(self):
        self.source_data["platform"] = "windows-x64"
        self.save()
        with self.assertRaisesRegex(ValueError, "platform=android-arm64"):
            build_rows(self.native, self.android, "snapshot-A")

    def test_missing_component_mesh_is_rejected(self):
        del self.source_data["components"]["0"]["mesh_name"]
        self.save()
        with self.assertRaisesRegex(ValueError, "requires mesh_name"):
            build_rows(self.native, self.android, "snapshot-A")

    def test_ambiguous_target_is_rejected(self):
        self.android_data["characters"][0]["modelAssets"].append({
            "path": "asset##S_actor_chen_body_01_lod1_20"})
        self.save()
        with self.assertRaisesRegex(ValueError, "Ambiguous"):
            build_rows(self.native, self.android, "snapshot-A")


if __name__ == "__main__":
    unittest.main()
