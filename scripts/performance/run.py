#!/usr/bin/env python3
"""Start isolated Luna benchmark attempts and retain evidence after each one."""
import argparse
import csv
import fcntl
import hashlib
import json
import os
import platform
import re
import shlex
import shutil
import signal
import subprocess
import sys
import time
from datetime import UTC, datetime
from pathlib import Path

from prepare import CACHE_SHA256, install_cache, prepare, replay
from results import save_json
import reports

SCRIPTS = Path(__file__).resolve().parent
REPO = SCRIPTS.parents[1]
REPORTS = reports.REPORTS
CHECKPOINTS = json.loads((SCRIPTS / "checkpoints.json").read_text())
WORK = Path("/workspace/luna-benchmark-work")
CACHE = Path("/workspace/.onboarding/luna-cache-377-openrs2-657.zip")
JDK21 = Path("/workspace/.tools/jdk-21.0.8+9")
JDK11 = WORK / "jdk-11.0.33.1+1"
GRADLE = Path("/workspace/.gradle")
BUILD = ["./gradlew", "--no-daemon", "--console=plain", "installDist"]
JVM = ["-Xms2g", "-Xmx6g", "-XX:+UseG1GC"]
STARTUP_LIMIT = 20 * 60
SPAWN_LIMIT = 12 * 60
TICK_SILENCE_LIMIT = 120
TARGET = 800
WARMUP = 15 * 60
MEASUREMENT = 60 * 60


def now():
    return datetime.now(UTC).isoformat(timespec="milliseconds")


def text(path):
    try:
        return Path(path).read_text().strip()
    except OSError:
        return None


def capture(args, cwd=REPO):
    return subprocess.check_output(args, cwd=cwd, text=True, stderr=subprocess.STDOUT).strip()


def environment(jdk):
    cg = Path("/sys/fs/cgroup")
    cpuinfo = text("/proc/cpuinfo") or ""
    return {
        "runner": "Managed Codex Cloud Linux allocation",
        "cpu_model": re.search(r"^model name\s*:\s*(.+)", cpuinfo, flags=re.M)[1],
        "sched_getaffinity": sorted(os.sched_getaffinity(0)),
        "cpu_max": text(cg / "cpu.max"),
        "effective_cpuset": text(cg / "cpuset.cpus.effective"),
        "memory_max_bytes": text(cg / "memory.max"),
        "memory_swap_max_bytes": text(cg / "memory.swap.max"),
        "cgroup_membership": text("/proc/self/cgroup"),
        "kernel": platform.platform(),
        "os_release": text("/etc/os-release"),
        "java": capture([str(jdk / "bin/java"), "-version"]) if jdk.exists() else None,
        "heap_and_gc_args": JVM,
        "cgroup_cpu_pressure_available": (cg / "cpu.pressure").exists(),
        "cgroup_memory_pressure_available": (cg / "memory.pressure").exists(),
        "disk_available_bytes_at_start": os.statvfs(WORK).f_bavail * os.statvfs(WORK).f_frsize,
    }


def config_workload():
    return {
        "target_active_bots": TARGET,
        "runtime": "DEVELOPMENT, through the revision's betaMode() flag",
        "network": "Native in-process Luna Bot clients, registered as active world players. No human connections.",
        "spawn": "50 bots per 16 ground-level overworld centers. Seeded sampling among viable tiles.",
        "spawns_seed": 3772026,
        "wandering_random_seed": None,
        "seed_unavailable_reason": "Native wandering uses revision-provided random sources without a seed setting.",
        "wander_action": "SmartWanderingAction, NORMAL frequency, 18-tile rectangular radius. Native pathfinder and queue.",
        "wander_action_gate": "Disables autonomous bot decisions for these temporary bots through the saved source patch.",
        "spawning": "Up to 40 native login submissions each tick.",
        "background": "Revision-supplied world and NPC definitions, actions and tasks. Auto-scheduled bot population zero.",
        "initial_data": "Fresh detached worktree, verified cache, empty saves, JSON serializer, no database.",
        "cache_archive_sha256": CACHE_SHA256,
        "cache_source": "OpenRS2 cache 657, RuneScape live #377, 2006-05-02 (provided cache archive)",
        "warmup_required_seconds": WARMUP,
        "measurement_required_seconds": MEASUREMENT,
        "resource_sample_seconds": 5,
        "movement_window_seconds": [60, 300],
        "warmup_qualification": "800 active bots with a native wandering action in the action queue and observed "
                                "movement progress. Distinct participants are verified during measurement.",
    }


def commit_info(sha):
    sha, subject, author, author_time, committer, commit_time = capture(
        ["git", "show", "-s", "--format=%H%n%s%n%an%n%aI%n%cn%n%cI", sha]).splitlines()
    commit_utc = datetime.fromisoformat(commit_time).astimezone(UTC)
    return {
        "sha": sha, "subject": subject, "author": author, "author_timestamp": author_time,
        "committer": committer, "committer_timestamp": commit_time, "committer_timestamp_utc": commit_utc.isoformat(),
        "date": commit_utc.date().isoformat(), "date_convention": "UTC calendar date of base commit committer timestamp",
    }


def folder_for(code, stamp):
    first = REPORTS / f"benchmark-{code['date']}"
    if not first.exists():
        return first, None
    previous = first / "manifest.json"
    if previous.exists() and json.loads(previous.read_text())["code"]["sha"] != code["sha"]:
        candidate = REPORTS / f"{first.name}-{code['sha'][:8]}"
        if not candidate.exists():
            return candidate, "Another commit already uses the unsuffixed code date."
        first = candidate
    return REPORTS / f"{first.name}-run-{stamp}", "Another attempt already uses this code date and commit."


def initialize(key, *, staged=False, archived_manifest=None):
    checkpoint = archived_manifest["checkpoint"] if archived_manifest else CHECKPOINTS["checkpoints"][key]
    code = commit_info(checkpoint["sha"])
    stamp = datetime.now(UTC).strftime("%Y%m%dT%H%M%S%fZ")
    folder, suffix = folder_for(code, stamp)
    if staged:
        folder = REPORTS / f"benchmark-{code['date']}"
        suffix = None
        if (folder / "manifest.json").exists():
            raise ValueError("Staged folder already has a manifest")
    for child in ("raw", "logs", "config", "patches"):
        (folder / child).mkdir(parents=True, exist_ok=staged)
    jdk = JDK11 if checkpoint.get("jdk") == 11 else JDK21
    manifest = {
        "run_id": stamp, "folder_suffix_reason": suffix, "checkpoint_key": key, "checkpoint": checkpoint, "code": code,
        "selection": {k: v for k, v in CHECKPOINTS.items() if k != "checkpoints"},
        "environment": environment(jdk),
        "workload": config_workload() | {
            "spawn_validation": "Terrain and live collision." if staged else
                                "Terrain and live collision. Prefer eight open directions within six tiles or scan up to sixteen tiles "
                                "inside the roam box. Require four open cardinal directions as a fallback.",
            "participant_random_encounters": "Revision defaults." if staged else
                                             "Benchmark-only guard where the revision has a random encounter tick hook.",
            "combat_engine": key != "native-wander",
        },
        "tick_budget_ms": 600,
        "attempt_started_utc": now(),
        "status": "PARTIAL",
        "runner_stage": "preparing",
        "process_limits_seconds": {
            "startup_before_first_tick": STARTUP_LIMIT, "population_and_first_movement": SPAWN_LIMIT,
            "tick_silence": TICK_SILENCE_LIMIT,
        },
        "cloud_lifetime": "This allocation exposes no confirmed absolute lifetime in its status. Runs checkpoint evidence "
                          "locally and interrupted measurement windows require a new attempt.",
    }
    if archived_manifest:
        manifest["workload"] = archived_manifest["workload"]
        manifest["replayed_from_run_id"] = archived_manifest["run_id"]
    return folder, manifest, jdk


def configure_commands(folder, manifest, jdk, tree):
    env = [f"JAVA_HOME={jdk}", f"GRADLE_USER_HOME={GRADLE}"]
    if jdk == JDK11:
        env.append("JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts")
    classpath = str(tree / "build/install/luna/lib/*")
    if manifest["checkpoint"].get("launch_from_class_directories"):
        entries = ["build/classes/java/main", "build/classes/kotlin/main", "build/resources/main"]
        classpath = os.pathsep.join([*(str(tree / entry) for entry in entries), classpath])
    checkpoint = manifest["checkpoint"]
    spawn_tiles = checkpoint.get("excluded_spawn_tiles")
    if spawn_tiles is None:
        spawn_tiles = [checkpoint["excluded_spawn_tile"]] if checkpoint.get("excluded_spawn_tile") else []
    launch_args = [f"-Dluna.benchmark.excludeSpawnTile={';'.join(spawn_tiles)}"] if spawn_tiles else []
    server = [
        str(jdk / "bin/java"), *manifest["environment"]["heap_and_gc_args"], *launch_args,
        f"-Xlog:gc*,safepoint:file={folder / 'logs/gc.txt'}:utctime,uptimenanos,level,tags",
        f"-Dluna.benchmark.raw={folder / 'raw'}", "-cp", classpath, "io.luna.Luna",
    ]
    manifest["commands"] = {
        "isolated_checkout": shlex.join(["git", "worktree", "add", "--detach", str(tree), manifest["code"]["sha"]]),
        "prepare_worktree": shlex.join(["python3", str(SCRIPTS / "prepare.py"), str(tree), str(folder), str(CACHE)]),
        "build": f"cd {shlex.quote(str(tree))} && {shlex.join(['env', *env, *BUILD])}",
        "start": f"cd {shlex.quote(str(tree))} && {shlex.join(server)}",
        "regenerate": shlex.join(["python3", str(SCRIPTS / "reports.py"), str(folder)]),
        "new_attempt": shlex.join(["python3", str(SCRIPTS / "run.py"), "run", manifest["checkpoint_key"]]),
    }
    return server, os.environ | dict(setting.split("=", 1) for setting in env)


def event_names(path):
    if not path.is_file():
        return set()
    with path.open() as source:
        return {row["event"] for row in csv.DictReader(source)}


def stop_server(process):
    if process.poll() is None:
        process.send_signal(signal.SIGTERM)
        try:
            process.wait(timeout=45)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()


def execute_server(folder, manifest, tree, command):
    events = folder / "raw/events.csv"
    tick_data = folder / "raw/ticks.csv"
    start = time.monotonic()
    manifest.update(server_started_utc=now(), runner_stage="startup")
    with (folder / "logs/server.txt").open("x") as log:
        process = subprocess.Popen(command, cwd=tree, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        first_probe = None
        manifest["server_pid"] = process.pid
        save_json(folder / "manifest.json", manifest)
        try:
            while True:
                names = event_names(events)
                if "measurement_finished" in names:
                    # Keep a resource sample beyond the exact end for CPU counter interpolation.
                    time.sleep(6)
                    return "Window finished and telemetry was flushed."
                if process.poll() is not None:
                    return f"Server exited with status {process.returncode} before the required window finished."
                if tick_data.exists():
                    first_probe = first_probe or time.monotonic()
                    if time.time() - tick_data.stat().st_mtime > TICK_SILENCE_LIMIT:
                        return "Tick telemetry went silent for 120 seconds."
                if first_probe is None and time.monotonic() - start >= STARTUP_LIMIT:
                    return "Startup did not reach the first instrumented tick within 20 minutes."
                if first_probe is not None and "warmup_started" not in names and time.monotonic() - first_probe >= SPAWN_LIMIT:
                    return "The 800 active-bot moving population was not demonstrated in the allotted 12-minute spawn phase."
                stage = "measurement" if "measurement_started" in names else "warmup" if "warmup_started" in names else "spawning"
                if first_probe and manifest["runner_stage"] != stage:
                    manifest["runner_stage"] = stage
                    save_json(folder / "manifest.json", manifest)
                time.sleep(2)
        finally:
            stop_server(process)
            manifest.update(server_stopped_utc=now(), server_exit_code=process.returncode)


def verify_legacy_startup(folder, manifest, tree, jdk):
    data = tree / "data"
    install_cache(data, CACHE)
    if manifest.get("checkpoint", {}).get("prepare_legacy_empty_saves"):
        for name in ("saved_bots", "saved_players"):
            (data / "game" / name).mkdir(parents=True, exist_ok=True)
    config_path = data / "luna.json"
    config = json.loads(config_path.read_text())
    config["game"].update(runtime_mode="DEVELOPMENT", serializer="JsonPlayerSerializer", port=43597)
    config["logging"].update(root_level="INFO", output_type="MIXED", active_file_logs=[])
    config_path.write_text(json.dumps(config, indent=2) + "\n")
    shutil.copyfile(config_path, folder / "config/luna.json")
    jvm_args = ["-Xms512m", "-Xmx3g", "-XX:+UseG1GC"]
    classpath = os.pathsep.join(str(tree / entry) for entry in (
        "build/classes/java/main", "build/classes/kotlin/main", "build/resources/main", "build/install/luna/lib/*"
    ))
    command = [
        "timeout", "-k", "10", "35", str(jdk / "bin/java"), *jvm_args,
        "-cp", classpath, "io.luna.Luna",
    ]
    manifest["commands"]["start"] = f"cd {shlex.quote(str(tree))} && {shlex.join(command)}"
    manifest["environment"]["heap_and_gc_args"] = jvm_args
    manifest["server_started_utc"] = now()
    path = folder / "logs/legacy-startup.txt"
    with path.open("x") as log:
        process = subprocess.run(command, cwd=tree, stdout=log, stderr=subprocess.STDOUT, check=False)
    manifest["server_stopped_utc"] = now()
    manifest["server_exit_code"] = process.returncode
    manifest["legacy_server_online"] = "Luna is now online on port 43597" in path.read_text()


def execute(key, staged=False, archived=None):
    archived_manifest = json.loads((archived / "manifest.json").read_text()) if archived else None
    folder, manifest, jdk = initialize(key, staged=staged, archived_manifest=archived_manifest)
    tree = WORK / ("current" if staged else f"{key}-{manifest['run_id']}")
    manifest["worktree"] = str(tree)
    server, env = configure_commands(folder, manifest, jdk, tree)
    path = folder / "manifest.json"
    save_json(path, manifest)
    print(f"{key} -> {folder}", flush=True)
    try:
        if not staged:
            with (folder / "logs/checkout.txt").open("x") as log:
                subprocess.run(["git", "worktree", "add", "--detach", str(tree), manifest["code"]["sha"]],
                               cwd=REPO, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=120)
        if "blocked" in manifest["checkpoint"]:
            manifest.update(status="BLOCKED", outcome=manifest["checkpoint"]["blocked"])
            if manifest["checkpoint"].get("validate_build"):
                manifest["runner_stage"] = "historical build verification"
                with (folder / "logs/build.txt").open("x") as log:
                    try:
                        result = subprocess.run(BUILD, cwd=tree, env=env, stdout=log, stderr=subprocess.STDOUT,
                                                timeout=240)
                        manifest["historical_build_exit_code"] = result.returncode
                    except subprocess.TimeoutExpired:
                        manifest["historical_build_timeout_seconds"] = 240
                manifest["commands"].pop("prepare_worktree")
                manifest["commands"].pop("start")
                if manifest["checkpoint"].get("verify_startup") and manifest.get("historical_build_exit_code") == 0:
                    verify_legacy_startup(folder, manifest, tree, jdk)
            return folder
        if archived:
            manifest["patch_sha256"] = replay(tree, folder, CACHE, archived, archived_manifest["patch_sha256"])
        elif staged:
            manifest["patch_sha256"] = hashlib.sha256((folder / "patches/instrumentation.patch").read_bytes()).hexdigest()
        else:
            manifest["patch_sha256"] = prepare(tree, folder, CACHE)
        save_json(path, manifest)
        manifest["runner_stage"] = "build"
        manifest["build_started_utc"] = now()
        save_json(path, manifest)
        if not staged:
            with (folder / "logs/build.txt").open("x") as log:
                subprocess.run(BUILD, cwd=tree, env=env, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=1800)
        manifest["build_finished_utc"] = now()
        jars = sorted(item.name for item in (tree / "build/install/luna/lib").glob("*.jar"))
        if not jars:
            raise ValueError("The Gradle application distribution contains no resolved runtime jars")
        manifest["runtime_jars"] = jars
        manifest["outcome"] = execute_server(folder, manifest, tree, server)
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        manifest.update(status="FAILED", outcome=f"{manifest['runner_stage']} failed. {error}")
        (folder / "logs/runner-error.txt").write_text(str(error) + "\n")
    finally:
        manifest["runner_stage"] = "finished"
        manifest["attempt_finished_utc"] = now()
        save_json(path, manifest)
        reports.write(folder)
    return folder


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("run", "suite", "replay"))
    parser.add_argument("checkpoints", nargs="*")
    parser.add_argument("--wait-for-lock", action="store_true", help="Queue this invocation behind the active suite.")
    parser.add_argument("--staged", action="store_true", help="Use the staged and built current worktree once.")
    args = parser.parse_args()
    archives = None
    keys = args.checkpoints or list(CHECKPOINTS["checkpoints"])
    if args.command == "replay":
        if len(args.checkpoints) != 1:
            parser.error("replay requires one report folder")
        archives = Path(args.checkpoints[0]).resolve()
        manifest = json.loads((archives / "manifest.json").read_text())
        if not manifest.get("patch_sha256"):
            parser.error("this report folder has no saved runnable source patch")
        keys = [manifest["checkpoint_key"]]
    elif any(key not in CHECKPOINTS["checkpoints"] for key in keys):
        parser.error(f"checkpoint keys: {', '.join(CHECKPOINTS['checkpoints'])}")
    if args.command == "run" and args.checkpoints == []:
        parser.error("run requires at least one checkpoint key")
    if args.staged and keys != ["current"]:
        parser.error("--staged requires a single current checkpoint")
    WORK.mkdir(parents=True, exist_ok=True)
    with (WORK / "suite.lock").open("a") as lock:
        lock_mode = fcntl.LOCK_EX if args.wait_for_lock else fcntl.LOCK_EX | fcntl.LOCK_NB
        fcntl.flock(lock, lock_mode)
        for key in keys:
            execute(key, args.staged, archives)


if __name__ == "__main__":
    main()
