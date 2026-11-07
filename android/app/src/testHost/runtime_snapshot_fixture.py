"""Run the real RuntimeSnapshot against the status string the native side emits.

No Android stubs are needed: RuntimeSnapshot only uses java.util. The fixture
therefore compiles the production source and the host test and runs them
straight, so the assertions are about the shipped parser, not a copy of it.
"""
from pathlib import Path
import argparse
import subprocess


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    classes = output / "classes"
    classes.mkdir(parents=True, exist_ok=True)
    android = Path(__file__).resolve().parents[3]
    files = [
        str(android / "app/src/main/java/dev/betterendfield/next/RuntimeSnapshot.java"),
        str(android / "app/src/testHost/java/dev/betterendfield/next/RuntimeSnapshotHostTest.java"),
    ]
    subprocess.run(["javac", "-encoding", "UTF-8", "--release", "17", "-d", str(classes), *files],
                   check=True)
    subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", str(classes),
                    "dev.betterendfield.next.RuntimeSnapshotHostTest"], check=True)


if __name__ == "__main__":
    main()
