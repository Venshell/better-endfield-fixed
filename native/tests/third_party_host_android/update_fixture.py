"""Exercise native index receipts and pinned binary updates on a host build."""
import json
import pathlib
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import uuid
import urllib.request


def check(host_exe: pathlib.Path, module_library: pathlib.Path) -> None:
    with tempfile.TemporaryDirectory(dir="D:/codexdata") as temporary:
        root = pathlib.Path(temporary)
        index_path = root / "index.json"
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", 0))
            port = listener.getsockname()[1]
        token = "a" * 64
        first_publication = str(uuid.uuid4())
        index = {"schema": 1, "port": port, "token": token, "modules": [],
                 "publication_id": first_publication}
        index_path.write_text(json.dumps(index), encoding="utf-8")
        process = subprocess.Popen([str(host_exe), str(index_path)], stdin=subprocess.PIPE,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            assert process.stdout.readline().strip() == "ready"

            def receipt(publication_id: str) -> dict:
                target = pathlib.Path(str(index_path) + ".receipt")
                deadline = time.monotonic() + 10
                while time.monotonic() < deadline:
                    try:
                        value = json.loads(target.read_text(encoding="utf-8"))
                        if value.get("publication_id") == publication_id:
                            return value
                    except (FileNotFoundError, json.JSONDecodeError):
                        pass
                    time.sleep(0.05)
                raise AssertionError(f"missing native receipt: {publication_id}")

            def publish(modules: list, **changes) -> dict:
                publication_id = str(uuid.uuid4())
                index.update(modules=modules, publication_id=publication_id, **changes)
                index_path.write_text(json.dumps(index), encoding="utf-8")
                result = receipt(publication_id)
                assert result["publication_id"] == publication_id
                return result

            def status() -> dict:
                request = urllib.request.Request(f"http://127.0.0.1:{port}/status",
                                                 headers={"Authorization": f"Bearer {token}"})
                with urllib.request.urlopen(request, timeout=3) as response:
                    return json.load(response)

            assert receipt(first_publication)["accepted"] is True
            first = root / "first"
            first.mkdir()
            shutil.copyfile(module_library, first / "module.dll")
            (first / "module.json").write_text(json.dumps({"format": 1, "abi": 1,
                "id": "fixture.native", "libraries": {"android-arm64": "module.dll"}}), encoding="utf-8")
            record = {"id": "fixture.native", "directory": str(first),
                      "generation": str(uuid.uuid4()), "configuration": {}, "enabled": True}
            first_a = publish([record])
            assert first_a["accepted"] is True
            assert status()["modules"][0]["status"] == "ready", status()
            assert publish([record | {"enabled": False}])["accepted"] is True
            assert status()["modules"][0]["status"] == "disabled"
            second_a = publish([record])
            assert second_a["accepted"] is True
            assert second_a["publication_id"] != first_a["publication_id"]
            assert status()["modules"][0]["status"] == "ready"
            second = root / "second"
            shutil.copytree(first, second)
            next_record = record | {"directory": str(second), "generation": str(uuid.uuid4())}
            assert publish([next_record])["accepted"] is True
            assert status()["modules"][0]["restart_required"] is True
            bad = next_record | {"id": "bad/id"}
            failed = publish([bad])
            assert failed["accepted"] is False
            assert status()["modules"][0]["id"] == "fixture.native"
            assert publish([next_record | {"enabled": False}])["accepted"] is True
            # A failed receipt must not masquerade as a later successful publication.
            recovered = publish([record | {"enabled": False}])
            assert recovered["accepted"] is True
            assert recovered["publication_id"] != failed["publication_id"]
            # The same requested module can recover when its files appear later.
            ui_dir = root / "late-ui"
            ui_record = {"id": "fixture.late", "directory": str(ui_dir),
                         "generation": str(uuid.uuid4()), "configuration": {}, "enabled": True}
            assert publish([ui_record])["accepted"] is True
            assert next(m for m in status()["modules"] if m["id"] == "fixture.late")["status"] == "failed"
            ui_dir.mkdir()
            (ui_dir / "index.html").write_text("ready", encoding="utf-8")
            (ui_dir / "module.json").write_text(json.dumps({"format": 1, "abi": 1,
                "id": "fixture.late", "libraries": {}, "ui": "index.html"}), encoding="utf-8")
            assert publish([ui_record])["accepted"] is True
            assert next(m for m in status()["modules"] if m["id"] == "fixture.late")["status"] == "ui_only"
            index.update(modules=[record | {"enabled": False}], publication_id=str(uuid.uuid4()))
            index_path.write_text(json.dumps(index), encoding="utf-8")
            latest = str(uuid.uuid4())
            index.update(modules=[record | {"enabled": False}], publication_id=latest)
            index_path.write_text(json.dumps(index), encoding="utf-8")
            assert receipt(latest)["accepted"] is True
            assert publish([])["accepted"] is True
            assert status()["modules"][0]["requested_enabled"] is False
            print("native publication identity, A-B-A, recovery, rapid updates, removal, restart: PASS")
        finally:
            process.stdin.write("\n")
            process.stdin.flush()
            process.communicate(timeout=5)


if __name__ == "__main__":
    check(pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]))
