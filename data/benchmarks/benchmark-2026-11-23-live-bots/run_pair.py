#!/usr/bin/env python3
"""Run the archived 1,500-bot workloads sequentially in independent data directories."""
import csv
import gzip
import hashlib
import os
import shutil
import subprocess
import sys
import time
from datetime import UTC, datetime
from pathlib import Path


MODES = ("live", "wandering")
JVM = ("-Xms2g", "-Xmx6g", "-XX:+UseG1GC")


def stop(process):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=65)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()


def announce(message):
    print(f"{datetime.now(UTC).isoformat(timespec='seconds')} {message}", flush=True)


def run(runtime, root, java, mode):
    folder = root / mode
    raw = folder / "raw"
    logs = folder / "logs"
    raw.mkdir(parents=True)
    logs.mkdir()
    world = folder / "world"
    shutil.copytree(runtime / "data", world / "data", ignore=shutil.ignore_patterns("benchmarks"))
    (world / "data/game/saved_players").mkdir()
    (world / "data/game/bots/saved_bots").mkdir()
    command = [
        str(java), *JVM, f"-Xlog:gc*,safepoint:file={logs / 'gc.txt'}:utctime,uptimenanos,level,tags",
        f"-Dluna.benchmark.raw={raw}", f"-Dluna.benchmark.workload={mode}",
        "-cp", str(runtime / "build/install/luna/lib/*"), "io.luna.Luna",
    ]
    ticks = raw / "ticks.csv"
    events = raw / "events.csv"
    with (logs / "server.txt").open("x") as log:
        process = subprocess.Popen(command, cwd=world, stdout=log, stderr=subprocess.STDOUT)
        launch = time.monotonic()
        first_tick = None
        phase = None
        announce(f"{mode}: JVM started (PID {process.pid})")
        try:
            while True:
                if process.poll() is not None:
                    raise RuntimeError(f"{mode} exited before measurement finished (status {process.returncode})")
                if events.exists():
                    with events.open() as stream:
                        markers = {row["event"] for row in csv.DictReader(stream)}
                    if "measurement_finished" in markers:
                        # Keep a resource sample after the boundary for CPU counter interpolation.
                        time.sleep(6)
                        announce(f"{mode}: measurement finished")
                        break
                    next_phase = "measurement" if "measurement_started" in markers else (
                        "warmup" if "warmup_started" in markers else "spawning")
                    if phase != next_phase:
                        phase = next_phase
                        announce(f"{mode}: {phase}")
                if ticks.exists():
                    first_tick = first_tick or time.monotonic()
                    if time.time() - ticks.stat().st_mtime > 120:
                        raise RuntimeError(f"{mode} tick telemetry stalled for 120 seconds")
                if first_tick is None and time.monotonic() - launch >= 1_200:
                    raise RuntimeError(f"{mode} produced no ticks within 20 minutes")
                if first_tick is not None and phase == "spawning" and time.monotonic() - first_tick >= 720:
                    raise RuntimeError(f"{mode} did not reach the full cohort within 12 minutes")
                time.sleep(2)
        finally:
            stop(process)
            announce(f"{mode}: JVM stopped (status {process.returncode})")

    with (logs / "gc.txt").open("rb") as source, gzip.open(logs / "gc.txt.gz", "wb") as target:
        shutil.copyfileobj(source, target)
    (logs / "gc.txt").unlink()
    with (raw / "movement-by-bot.csv").open("rb") as source, gzip.open(raw / "movement-by-bot.csv.gz", "wb") as target:
        shutil.copyfileobj(source, target)
    (raw / "movement-by-bot.csv").unlink()

    with (folder / "webwalk.sha256").open("w") as output:
        for file in sorted((world / "data/game/bots/webwalk").iterdir()):
            if file.is_file():
                output.write(f"{hashlib.sha256(file.read_bytes()).hexdigest()}  {file.name}\n")


if __name__ == "__main__":
    if len(sys.argv) not in (3, 4):
        raise SystemExit("usage: run_pair.py BUILT_WORKTREE NEW_OUTPUT_DIR [live|wandering]")
    runtime, output = (Path(arg).resolve() for arg in sys.argv[1:3])
    java = Path(os.environ["JAVA_HOME"]) / "bin/java" if "JAVA_HOME" in os.environ else Path(shutil.which("java"))
    modes = MODES if len(sys.argv) == 3 else (sys.argv[3],)
    if not all(mode in MODES for mode in modes):
        raise SystemExit("Workload must be live or wandering")
    for workload in modes:
        run(runtime, output, java.resolve(), workload)
