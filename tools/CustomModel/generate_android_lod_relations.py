"""Generate candidate Android UI LOD0 to world LOD1 relations from two catalogs.

This is an offline authoring tool. Its output is not runtime verification.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path


def _read_json(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(data, dict):
        raise ValueError(f"Expected JSON object: {path}")
    return data


def _base(name: str) -> str:
    return name.removesuffix("_20").removesuffix("_8")


def build_rows(native_catalog: Path, android_catalog: Path, snapshot: str) -> list[dict[str, str]]:
    if not snapshot:
        raise ValueError("A nonempty manifest snapshot is required")
    android_data = _read_json(android_catalog)
    if android_data.get("platform") != "Android":
        raise ValueError("Android catalog must declare platform=Android")
    if android_data.get("manifestVersion") != snapshot:
        raise ValueError("Android catalog manifestVersion does not match --snapshot")
    characters = android_data.get("characters")
    if not isinstance(characters, list):
        raise ValueError("Android catalog requires characters array")
    if any(not isinstance(entry, dict) or not isinstance(entry.get("characterId"), str)
           for entry in characters):
        raise ValueError("Android characters require characterId")
    android = {entry["characterId"]: entry for entry in characters}
    if len(android) != len(characters):
        raise ValueError("Duplicate Android characterId")
    rows: list[dict[str, str]] = []
    catalog_paths = sorted(native_catalog.glob("chr_*.json"))
    if not catalog_paths:
        raise ValueError(f"No native character catalogs found: {native_catalog}")
    for path in catalog_paths:
        source = _read_json(path)
        if source.get("platform") != "android-arm64":
            raise ValueError(f"Native catalog must declare platform=android-arm64: {path}")
        character = source.get("character_id")
        if not isinstance(character, str) or not character:
            raise ValueError(f"Native catalog requires character_id: {path}")
        observed = android.get(character)
        if observed is None:
            continue
        source_snapshot = source.get("source_snapshot")
        if not isinstance(source_snapshot, dict) or source_snapshot.get("manifest_version") != snapshot:
            raise ValueError(f"Native catalog snapshot mismatch: {path}")
        if not all(isinstance(source.get(key), str) and source[key]
                   for key in ("ui_resource", "world_resource")):
            raise ValueError(f"Native catalog requires UI and world resources: {path}")
        if not isinstance(source.get("components"), dict):
            raise ValueError(f"Native catalog requires components object: {path}")
        if any(not str(component_id).isdigit() for component_id in source["components"]):
            raise ValueError(f"Component IDs must be numeric: {path}")
        assets = observed.get("modelAssets", [])
        lod1: dict[str, str] = {}
        for asset in assets:
            asset_path = asset.get("path", "")
            if "_lod1" not in asset_path.casefold():
                continue
            name = asset_path.rsplit("##", 1)[-1] if "##" in asset_path else Path(asset_path).stem
            key = _base(name).casefold()
            if key in lod1 and lod1[key] != name:
                raise ValueError(f"Ambiguous Android LOD1 mesh: {character} {key}")
            lod1[key] = name
        for component_id, component in sorted(source["components"].items(), key=lambda pair: int(pair[0])):
            if not isinstance(component, dict) or not isinstance(component.get("mesh_name"), str):
                raise ValueError(f"Component {component_id} requires mesh_name: {path}")
            source_mesh = component["mesh_name"]
            source_base = _base(source_mesh)
            if not source_base.casefold().endswith("_lod0"):
                continue
            target_guess = source_base[:-5] + "_lod1"
            target_mesh = lod1.get(target_guess.casefold())
            observations = component.get("evidence", {}).get("observations", {})
            ui = next((item for name, item in observations.items()
                       if name.endswith("_uimodel") and item.get("path")), None)
            if target_mesh is None or ui is None:
                continue
            source_path = ui["path"]
            if not source_path.startswith(source["ui_resource"] + "/"):
                raise ValueError(f"UI evidence resource mismatch: {path} component {component_id}")
            rows.append({
                "character": character,
                "source_resource": source["ui_resource"],
                "target_resource": source["world_resource"],
                "source_path": source_path.split("/", 1)[1],
                "source_mesh": source_mesh,
                "target_path": "Mesh_all/lod1/" + _base(target_mesh),
                "target_mesh": target_mesh,
            })
    rows.sort(key=lambda row: tuple(row.values()))
    return rows


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--native-catalog", type=Path, required=True)
    parser.add_argument("--android-catalog", type=Path, required=True)
    parser.add_argument("--snapshot", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    rows = build_rows(args.native_catalog, args.android_catalog, args.snapshot)
    result = {"schema": 1, "platform": "Android", "snapshot": args.snapshot,
              "evidence_status": "candidate-requires-runtime-validation", "relations": rows}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"generated {len(rows)} candidate Android LOD relations -> {args.output}")


if __name__ == "__main__":
    main()
