"""Convert Luna benchmark telemetry into single-run statistics."""
import csv
import gzip
import json
import math
import re
from datetime import UTC, datetime
from statistics import mean, median

MS = 1_000_000
SECOND = 1_000_000_000
MIB = 1024**2
RESOURCES = {
    "nr_throttled": "cgroup_throttled_intervals",
    "throttled_us": "cgroup_throttled_us",
    "cpu_some_us": "cgroup_cpu_some_pressure_us",
    "cpu_full_us": "cgroup_cpu_full_pressure_us",
    "memory_some_us": "cgroup_memory_some_pressure_us",
    "memory_full_us": "cgroup_memory_full_pressure_us",
    "memory_oom": "cgroup_oom_events",
    "memory_oom_kill": "cgroup_oom_kills",
}
GC_LINE = re.compile(r"^\[([^\]]+)\]\[(\d+)ns\]\[info\]\[gc\s*\] GC\((\d+)\) (.+) ([\d.]+)ms$")
HEAP_SIZES = re.compile(r"([\d.]+)([KMGT])->([\d.]+)([KMGT])\(([\d.]+)([KMGT])\)$")
UNIT_POWER = {"K": 1, "M": 2, "G": 3, "T": 4}


def save_json(path, contents):
    temp = path.with_name(path.name + ".tmp")
    temp.write_text(json.dumps(contents, indent=2, allow_nan=False) + "\n")
    temp.replace(path)


def iso_wall(millis):
    return datetime.fromtimestamp(millis / 1000, UTC).isoformat(timespec="milliseconds")


def rows(path):
    if not path.is_file():
        return []
    with path.open() as source:
        # Ignore one unterminated final row when reading a live file or after an interrupted write.
        complete = source.read().splitlines(keepends=True)
    if complete and not complete[-1].endswith("\n"):
        complete.pop()
    return list(csv.DictReader(complete))


def integer_rows(path, text_fields=()):
    return [
        {name: value if name in text_fields else int(value) if value else None for name, value in row.items()}
        for row in rows(path)
    ]


def stats(values):
    if not values:
        return None
    values = sorted(values)
    return {
        "mean": mean(values),
        "median": median(values),
        "p95": values[math.ceil(len(values) * 0.95) - 1],
        "p99": values[math.ceil(len(values) * 0.99) - 1],
        "max": values[-1],
        "min": values[0],
        "samples": len(values),
    }


def delta(samples, start, end, field):
    if not samples or start < samples[0]["mono_ns"] or end > samples[-1]["mono_ns"]:
        return None

    def at(target):
        for left, right in zip(samples, samples[1:]):
            if left["mono_ns"] <= target <= right["mono_ns"]:
                if left[field] is None or right[field] is None:
                    return None
                fraction = (target - left["mono_ns"]) / (right["mono_ns"] - left["mono_ns"])
                return left[field] + fraction * (right[field] - left[field])
        return None

    start_value, end_value = at(start), at(end)
    if start_value is None or end_value is None:
        return None
    return end_value - start_value


def resources(folder, start, end, completed):
    readings = integer_rows(folder / "raw/resources.csv")
    samples = [row for row in readings if start <= row["mono_ns"] < end]
    result = {"sample_count": len(samples)}
    for key, divisor, field in (
        ("rss_mib", 1024, "rss_kib"),
        ("heap_used_mib", MIB, "heap_used_b"),
        ("heap_committed_mib", MIB, "heap_committed_b"),
        ("heap_max_mib", MIB, "heap_max_b"),
        ("container_memory_mib", MIB, "memory_current_b"),
    ):
        result[key] = stats([sample[field] / divisor for sample in samples if sample[field] is not None])
    for key, name in RESOURCES.items():
        counters = [row[key] for row in samples if row[key] is not None]
        result[name] = counters[-1] - counters[0] if len(counters) >= 2 else None

    cpu_intervals = []
    for left, right in zip(readings, readings[1:]):
        if left["cpu_ns"] is None or right["cpu_ns"] is None:
            continue
        elapsed = right["mono_ns"] - left["mono_ns"]
        covered = min(end, right["mono_ns"]) - max(start, left["mono_ns"])
        if covered <= 0 or elapsed <= 0:
            continue
        percent = 100 * (right["cpu_ns"] - left["cpu_ns"]) / elapsed
        cpu_intervals.append((percent, covered))
    cpu_time = delta(readings, start, end, "cpu_ns")
    result["cpu_time_seconds"] = cpu_time / SECOND if cpu_time is not None else None
    result["cpu_ms_per_world_tick"] = cpu_time / MS / completed if cpu_time is not None and completed else None
    result["cpu_interval_coverage_seconds"] = sum(covered for _, covered in cpu_intervals) / SECOND
    result["process_cpu_pct"] = stats([percent for percent, _ in cpu_intervals])
    if cpu_intervals:
        result["process_cpu_pct"]["mean"] = (
            sum(percent * covered for percent, covered in cpu_intervals) / sum(covered for _, covered in cpu_intervals)
        )
    if samples:
        result["sample_start_utc"] = iso_wall(samples[0]["utc_ms"])
        result["sample_end_utc"] = iso_wall(samples[-1]["utc_ms"])
        result["heap_used_first_mib"] = samples[0]["heap_used_b"] / MIB
        result["heap_used_last_mib"] = samples[-1]["heap_used_b"] / MIB
    return result


def garbage_collection(folder, start_ms, end_ms):
    path = folder / "logs/gc.txt"
    if not path.is_file():
        path = folder / "logs/gc.txt.gz"
    if not path.is_file():
        return None, []
    open_log = gzip.open if path.suffix == ".gz" else open
    with open_log(path, "rt") as log:
        lines = list(log)
    if not any("Using G1" in line for line in lines):
        return None, []
    events = []
    for line in lines:
        match = GC_LINE.match(line)
        if not match:
            continue
        date, uptime, gc_id, description, duration = match.groups()
        end = datetime.fromisoformat(date).timestamp() * 1000
        if not start_ms <= end < end_ms or not description.startswith(("Pause ", "Concurrent ")):
            continue
        heap = HEAP_SIZES.search(description)
        sizes = {}
        if heap:
            parts = iter(heap.groups())
            for key, (value, unit) in zip(("before_b", "after_b", "capacity_b"), zip(parts, parts)):
                sizes[key] = int(float(value) * 1024 ** UNIT_POWER[unit])
        events.append({
            "end_utc": date,
            "jvm_uptime_ns": int(uptime),
            "id": int(gc_id),
            "kind": description,
            "elapsed_ms": float(duration),
            "is_pause": int(description.startswith("Pause ")),
            **sizes,
        })
    pauses = [row["elapsed_ms"] for row in events if row["is_pause"]]
    post_gc = [row for row in events if row["is_pause"] and "after_b" in row]
    after = [row["after_b"] / MIB for row in post_gc]
    quarters = [[], [], [], []]
    for row in post_gc:
        end = datetime.fromisoformat(row["end_utc"]).timestamp() * 1000
        quarter = min(3, int(4 * (end - start_ms) / (end_ms - start_ms)))
        quarters[quarter].append(row["after_b"] / MIB)
    medians = [median(quarter) if quarter else None for quarter in quarters]
    grows = all(value is not None for value in medians) and all(
        left < right for left, right in zip(medians, medians[1:])
    )
    event_ids = {row["id"] for row in events}
    old_regions = []
    for line in lines:
        match = re.search(r"GC\((\d+)\) Old regions: (\d+)->(\d+)", line)
        if match and int(match[1]) in event_ids:
            old_regions.append(int(match[3]))
    return {
        "pause_count": len(pauses),
        "collection_ids": len({row["id"] for row in events}),
        "total_pause_ms": sum(pauses),
        "longest_pause_ms": max(pauses, default=None),
        "post_gc_mib": stats(after),
        "post_gc_first_mib": after[0] if after else None,
        "post_gc_last_mib": after[-1] if after else None,
        "post_gc_quarter_medians_mib": medians,
        "post_gc_continuous_quarter_growth": grows,
        "old_regions_first": old_regions[0] if old_regions else None,
        "old_regions_last": old_regions[-1] if old_regions else None,
        "normal_young_pauses": sum(row["kind"].startswith("Pause Young (Normal)") for row in events),
        "mixed_pauses": sum(row["kind"].startswith("Pause Young (Mixed)") for row in events),
        "full_gc_pauses": sum(row["kind"].startswith("Pause Full") for row in events),
        "allocation_rate": None,
    }, events


def analysis(folder, manifest):
    tick = integer_rows(folder / "raw/ticks.csv", ("phase",))
    raw_events = integer_rows(folder / "raw/events.csv", ("event", "value"))
    events = {row["event"]: row for row in raw_events}
    warm = events.get("warmup_started")
    start = events.get("measurement_started")
    finish = events.get("measurement_finished")
    measured = [row for row in tick if row["phase"] == "measurement"]
    boundary = next((row for row in tick if row["phase"] == "boundary"), None)
    budget = manifest["tick_budget_ms"]
    gap_threshold = 1.5 * budget
    following = [*measured[1:], boundary] if measured and boundary else measured[1:]
    previous = measured[:len(following)]
    spacing = [(right["start_ns"] - left["start_ns"]) / MS for left, right in zip(previous, following)]
    gaps = [
        {"tick_sequence": right["seq"], "next_tick_end_utc": iso_wall(right["wall_end_ms"]),
         "spacing_ms": interval, "excess_ms": interval - budget}
        for right, interval in zip(following, spacing) if interval > gap_threshold
    ]
    completed = sum(row["world_completed"] for row in measured) if measured else None
    durations = [(row["end_ns"] - row["start_ns"]) / MS for row in measured]
    over_budget = sum(value > budget for value in durations) if durations else None
    end_ns = None
    if measured:
        end_ns = boundary["start_ns"] if boundary else measured[-1]["end_ns"]
    warm_end_ns = start["monotonic_ns"] if start else tick[-1]["end_ns"] if warm and tick else None
    warm_seconds = (warm_end_ns - warm["monotonic_ns"]) / SECOND if warm_end_ns and warm else None
    if warm and events.get("warmup_reset", {}).get("monotonic_ns", 0) > warm["monotonic_ns"]:
        warm_seconds = None
    duration = (end_ns - start["monotonic_ns"]) / SECOND if start and end_ns else None

    metrics = {}
    gc_events = []
    measured_span = None
    if measured and start:
        measured_span = {
            "start_utc": iso_wall(
                measured[0]["wall_end_ms"] - (measured[0]["end_ns"] - start["monotonic_ns"]) / MS),
            "end_utc": iso_wall(
                (boundary or measured[-1])["wall_end_ms"] - ((boundary or measured[-1])["end_ns"] - end_ns) / MS),
        }
        start_ms = datetime.fromisoformat(measured_span["start_utc"]).timestamp() * 1000
        end_ms = datetime.fromisoformat(measured_span["end_utc"]).timestamp() * 1000
        gc, gc_events = garbage_collection(folder, start_ms, end_ms)
        metrics = {
            "processing_ms": stats(durations),
            "spacing_ms": stats(spacing),
            "lateness_ms": stats([
                max(0, (row["start_ns"] - start["monotonic_ns"]) / MS - index * budget)
                for index, row in enumerate(measured)
            ]),
            "tick_attempts": len(measured),
            "completed_world_ticks": completed,
            "over_budget_count": over_budget,
            "over_budget_pct": over_budget / len(measured) * 100,
            "scheduling_gap_count": len(gaps),
            "scheduling_gap_spacing_ms": stats([row["spacing_ms"] for row in gaps]),
            "scheduling_gap_total_spacing_ms": sum(row["spacing_ms"] for row in gaps),
            "scheduling_gap_total_excess_ms": sum(row["excess_ms"] for row in gaps),
            "online_bots": stats([row["online"] for row in measured]),
            "registered_players": stats([row["players"] for row in measured]),
            "npc_population": stats([row["npcs"] for row in measured]),
            "bots_moving_per_tick": stats([row["moved"] for row in measured]),
            "wandering_bot_count": stats([row["wandering"] for row in measured]),
            "bots_moving_per_60s": stats([row["moved_60s"] for row in measured]),
            "bots_moving_per_300s": stats([row["moved_300s"] for row in measured]),
            "bot_tick_position_changes": sum(row["moved"] for row in measured),
            "ticks_below_target": sum(row["online"] < manifest["workload"]["target_active_bots"] for row in measured),
            "ticks_with_participants_in_combat": (
                sum(row["in_combat"] > 0 for row in measured)
                if all(row["in_combat"] is not None for row in measured) else None
            ),
            "measurement_movers": (
                boundary.get("measurement_movers") if boundary else measured[-1].get("measurement_movers")
            ),
            "ever_moved": max(row["ever_moved"] for row in measured),
            "instrumentation_ns": stats([row["previous_probe_ns"] for row in measured]),
            "resources": resources(folder, start["monotonic_ns"], end_ns, completed),
            "gc": gc,
        }

    status = manifest["status"]
    checks = {
        "server_confirmed_beta": events.get("effective_beta", {}).get("value") == "true",
        "warmup_met": warm_seconds is not None and warm_seconds >= manifest["workload"]["warmup_required_seconds"],
        "measurement_met": finish is not None and duration is not None
        and duration >= manifest["workload"]["measurement_required_seconds"],
        "target_held": metrics.get("ticks_below_target") == 0 and boundary is not None
        and boundary["online"] == manifest["workload"]["target_active_bots"],
        "only_benchmark_players": bool(metrics.get("registered_players"))
        and metrics["registered_players"]["min"] == manifest["workload"]["target_active_bots"]
        and metrics["registered_players"]["max"] == manifest["workload"]["target_active_bots"],
        "continuous_tick_sequence": bool(measured and boundary)
        and boundary["seq"] - measured[0]["seq"] == len(measured)
        and all(right["seq"] - left["seq"] == 1 for left, right in zip(previous, following)),
        "native_wandering_population_observed": bool(metrics.get("wandering_bot_count"))
        and metrics["wandering_bot_count"]["min"] == manifest["workload"]["target_active_bots"],
        "movement_verified": metrics.get("measurement_movers") == manifest["workload"]["target_active_bots"]
        and bool(metrics.get("bots_moving_per_300s")) and metrics["bots_moving_per_300s"]["min"] > 0,
        "no_bot_combat": metrics.get("ticks_with_participants_in_combat") == 0
        or not manifest["workload"].get("combat_engine", True),
        "npc_background_present": bool(metrics.get("npc_population")) and metrics["npc_population"]["min"] > 0,
        "resource_samples_saved": (metrics.get("resources", {}).get("sample_count") or 0) > 0,
        "completed_world_ticks_recorded": bool(completed),
    }
    if manifest["runner_stage"] == "finished" and status != "BLOCKED":
        if all(checks.values()):
            status = "COMPLETE"
        elif not measured:
            status = "FAILED"
        else:
            status = "PARTIAL"
    summary = {
        "status": status,
        "outcome": manifest.get("outcome"),
        "effective_beta": events.get("effective_beta", {}).get("value"),
        "runtime_mode": events.get("runtime_mode", {}).get("value"),
        "duration_seconds": duration,
        "warmup_seconds": warm_seconds,
        "measurement_utc": measured_span,
        "event_timestamps_utc": {name: iso_wall(row["utc_ms"]) for name, row in events.items()},
        "tick_budget_ms": budget,
        "gap_threshold_ms_strictly_greater_than": gap_threshold,
        "target_bots": manifest["workload"]["target_active_bots"],
        "peak_active_bots_any_phase": max((row["online"] for row in tick), default=None),
        "highest_bot_movement_ever_count": max((row["ever_moved"] for row in tick), default=None),
        "pre_measurement_ticks_with_bot_combat": (
            sum((row["in_combat"] or 0) > 0 for row in tick if row["phase"] in ("spawning", "warmup"))
            if any(row["in_combat"] is not None for row in tick) else None
        ),
        "npc_population_any_phase": stats([row["npcs"] for row in tick]),
        "latest_observed_utc": iso_wall(tick[-1]["wall_end_ms"]) if tick else None,
        "live": manifest["runner_stage"] != "finished",
        "checks": checks,
        "metrics": metrics,
    }
    return summary, gaps, gc_events
