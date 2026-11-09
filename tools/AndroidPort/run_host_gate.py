"""Android 移植宿主门禁；只编译纯 Java owner，不加载 Android SDK 或游戏。"""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile


JAVA_OWNERS = (
    "BemConfigurationLimit", "BemHotSwitchUpdate", "BemImportStream",
    "BemImportArchive", "BemIndexCommit", "FrameworkServiceWait",
    "OverlayReconnectPolicy", "BemIndexRevision", "BemTransformPublication", "BemPackageCopy", "ModelDriverControl",
)
JAVA_SUITES = (
    "BemHotSwitchUpdateTest", "BemImportArchiveTest", "BemIndexCommitTest",
    "FrameworkServiceWaitTest", "OverlayReconnectPolicyTest", "BemIndexRevisionTest", "BemTransformPublicationTest",
    "BemPackageCopyTest", "ModelDriverControlTest",
)
PYTHON_SUITES = (
    "test_bem_v14", "test_bem_upload_report", "test_build_bem14_target",
    "test_android_lod_relations",
)
JSON_VERSION = "20240303"
JSON_SHA256 = "3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed"


class GateFailure(Exception):
    """携带具体门禁阶段的可恢复失败。"""


def run(stage: str, command: list[str], cwd: Path, env: dict[str, str]) -> str:
    print(f"RUN {stage}", flush=True)
    try:
        result = subprocess.run(
            command, cwd=cwd, env=env, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, encoding="utf-8",
            errors="replace", timeout=180, check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise GateFailure(f"{stage}: {error}") from error
    print(result.stdout, end="" if result.stdout.endswith("\n") else "\n", flush=True)
    if result.returncode:
        raise GateFailure(f"{stage}: exit={result.returncode}")
    return result.stdout


def gate(root: Path, java_home: Path, work_dir: Path, json_jar: Path) -> None:
    package = Path("dev/betterendfield/next")
    java_root = root / "android/app/src"
    sources = [java_root / "main/java" / package / f"{name}.java" for name in JAVA_OWNERS]
    sources += [java_root / "main/java" / package / "Text.java",
                root / "tools/AndroidPort/fixtures/AppLanguage.java"]
    sources += [java_root / "testHost/java" / package / f"{name}.java" for name in JAVA_SUITES]
    python_root = root / "tools/CustomModel"
    required = sources + [python_root / f"{name}.py" for name in PYTHON_SUITES]
    suffix = ".exe" if os.name == "nt" else ""
    javac, java = [java_home / "bin" / (name + suffix) for name in ("javac", "java")]
    required += [javac, java, json_jar]
    for path in required:
        if not path.is_file():
            raise GateFailure(f"input: missing file {path}")
    if json_jar.stat().st_size > 4 * 1024 * 1024 or hashlib.sha256(json_jar.read_bytes()).hexdigest() != JSON_SHA256:
        raise GateFailure(f"input: expected org.json:json:{JSON_VERSION} SHA-256 {JSON_SHA256}")
    work_dir.mkdir(parents=True, exist_ok=True)
    # 每轮独立且自动释放，禁止从旧 class 输出中取得通过结果。
    with tempfile.TemporaryDirectory(prefix="port-host-", dir=work_dir) as temporary:
        workspace = Path(temporary)
        classes = workspace / "classes"
        classes.mkdir()
        env = dict(os.environ, PYTHONDONTWRITEBYTECODE="1", PYTHONUTF8="1")
        for key in ("TMP", "TEMP", "TMPDIR"):
            env[key] = str(workspace)
        run("javac", [str(javac), "--release", "17", "-encoding", "UTF-8",
                      "-cp", str(json_jar), "-d", str(classes), *map(str, sources)], root, env)
        classpath = os.pathsep.join((str(classes), str(json_jar)))
        checks = 0
        for suite in JAVA_SUITES:
            output = run(suite, [str(java), "-Dfile.encoding=UTF-8",
                         f"-Djava.io.tmpdir={workspace}", "-cp", classpath,
                         f"dev.betterendfield.next.{suite}"], root, env)
            counts = re.findall(r"\b(\d+) checks\b", output)
            if len(counts) != 1 or int(counts[0]) <= 0:
                raise GateFailure(f"{suite}: missing nonzero check count")
            checks += int(counts[0])
        output = run("Python BEM tools", [sys.executable, "-B", "-m", "unittest",
                     "-v", *PYTHON_SUITES], python_root, env)
        counts = re.findall(r"^Ran (\d+) tests? in ", output, flags=re.MULTILINE)
        if len(counts) != 1 or int(counts[0]) <= 0:
            raise GateFailure("Python BEM tools: missing nonzero test count")
        print(f"PASS Android port host gate: {len(JAVA_SUITES)} Java suites, "
              f"{checks} Java checks; {len(PYTHON_SUITES)} Python modules, "
              f"{counts[0]} Python tests", flush=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", type=Path, default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--json-jar", type=Path, default=os.environ.get("BEM_PORT_JSON_JAR"),
                        help=f"Verified org.json:json:{JSON_VERSION} JAR (or BEM_PORT_JSON_JAR)")
    parser.add_argument("--work-dir", type=Path, default=(
        Path("D:/codexdata/bem-port-host-work") if os.name == "nt"
        else Path(tempfile.gettempdir()) / "bem-port-host-work"))
    args = parser.parse_args()
    if args.java_home is None:
        parser.error("set JAVA_HOME or pass --java-home (JDK 17+)")
    if args.json_jar is None:
        parser.error(f"set BEM_PORT_JSON_JAR or pass --json-jar (org.json:json:{JSON_VERSION})")
    try:
        gate(Path(__file__).resolve().parents[2], args.java_home.resolve(), args.work_dir.resolve(), args.json_jar.resolve())
    except (GateFailure, OSError) as error:
        print(f"FAIL Android port host gate: {error}", file=sys.stderr, flush=True)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
