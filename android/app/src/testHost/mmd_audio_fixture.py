"""Run the real MMD audio transport against a deterministic host worker.

The production `MmdAudio.java` is compiled unchanged against a stub
`MediaPlayer` whose callbacks are delivered by hand, so every ordering the
player can produce - prepare, seek completion, completion, error, and the
callbacks that arrive after the token has already moved on - is reproducible
without a device. The stub worker is a manual clock: nothing posted runs
inline, and the test decides when it runs.

Each case gets a fresh JVM because a case is allowed to leave the static
worker populated; the runner enumerates the case names first, then runs them
one process at a time and reports the failing set.
"""
from pathlib import Path
import argparse
import hashlib
import shutil
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--cases", default="", help="comma-separated subset; default is all")
    args = parser.parse_args()

    output = args.output.resolve()
    classes = output / "classes"
    snapshot = output / "MmdAudio.java"
    shutil.rmtree(output, ignore_errors=True)
    classes.mkdir(parents=True, exist_ok=True)

    android = Path(__file__).resolve().parents[3]
    fixture = Path(__file__).resolve().parent / "mmd_audio"
    production = android / "app/src/main/java/dev/betterendfield/next/MmdAudio.java"
    # Compiled from a copy so the digest below names the exact bytes that were
    # compiled, not whatever the working tree holds a moment later.
    snapshot.write_bytes(production.read_bytes())
    print("Production source SHA256: " + hashlib.sha256(snapshot.read_bytes()).hexdigest())

    sources = sorted((fixture / "stubs").rglob("*.java")) + \
        sorted((fixture / "src").rglob("*.java"))
    subprocess.run(
        ["javac", "-encoding", "UTF-8", "--release", "17", "-d", str(classes),
         str(snapshot)] + [str(path) for path in sources],
        check=True)

    tests = "dev.betterendfield.next.MmdAudioHostTest"
    if args.cases.strip():
        cases = [name.strip() for name in args.cases.split(",") if name.strip()]
    else:
        listed = subprocess.run(["java", "-cp", str(classes), tests, "--list"],
                                check=True, capture_output=True, text=True)
        cases = [line.strip() for line in listed.stdout.splitlines() if line.strip()]

    failed = []
    for case in cases:
        result = subprocess.run(["java", "-cp", str(classes), tests, case],
                                capture_output=True, text=True)
        if result.returncode != 0:
            failed.append(case)
            sys.stderr.write(result.stderr)
        else:
            sys.stdout.write(result.stdout)

    print("Audio host cases: " + str(len(cases) - len(failed)) + "/" + str(len(cases)) + " passed")
    print("Compiled classes: " + str(classes))
    if failed:
        print("Failing cases: " + ", ".join(failed))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
