#!/usr/bin/env python3
"""Summarize both 1,500-bot runs with the original Luna benchmark statistics."""
import json
import sys
from collections import Counter
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[3] / "scripts/performance"
sys.path.insert(0, str(SCRIPTS))
from results import analysis, integer_rows, rows, stats

SKILLS = (
    "Attack", "Defence", "Strength", "Hitpoints", "Ranged", "Prayer", "Magic", "Cooking", "Woodcutting",
    "Fletching", "Fishing", "Firemaking", "Crafting", "Smithing", "Mining", "Herblore", "Agility",
    "Thieving", "Slayer", "Farming", "Runecrafting",
)


def summarize(folder, mode, workload):
    manifest = {"status": "UNVERIFIED", "runner_stage": "finished", "tick_budget_ms": 600, "workload": workload}
    summary, gaps, gc_events = analysis(folder, manifest)
    ticks = integer_rows(folder / "raw/ticks.csv", ("phase",))
    measured = [row for row in ticks if row["phase"] == "measurement"]
    boundary = next(row for row in ticks if row["phase"] == "boundary")
    start = measured[0]["start_ns"]
    finish = boundary["start_ns"]
    checks = summary["checks"]
    del checks["server_confirmed_beta"]
    checks["production_mode"] = summary["runtime_mode"] == "PRODUCTION" and summary["effective_beta"] == "false"
    checks["all_world_ticks_completed"] = summary["metrics"]["completed_world_ticks"] == len(measured)
    checks["no_login_errors"] = boundary["login_errors"] == 0
    checks["webwalk_data_generated"] = "  walk_graph.jsonc\n" in (folder / "webwalk.sha256").read_text()
    if mode == "live":
        del checks["native_wandering_population_observed"]
        del checks["no_bot_combat"]
        del checks["movement_verified"]
        checks["native_combat_observed"] = summary["metrics"]["ticks_with_participants_in_combat"] > 0
        checks["movement_observed"] = summary["metrics"]["measurement_movers"] > 0

    first, last = {}, {}
    trades_before = trades_after = None
    for row in rows(folder / "raw/experience.csv"):
        timestamp = int(row["monotonic_ns"])
        if timestamp == start:
            first[SKILLS[int(row["skill_id"])]] = float(row["total_xp"])
            trades_before = int(row["completed_trades"])
        if timestamp == finish:
            last[SKILLS[int(row["skill_id"])]] = float(row["total_xp"])
            trades_after = int(row["completed_trades"])

    script_counts = Counter()
    active_counts = Counter()
    for row in integer_rows(folder / "raw/scripts.csv", ("phase", "script")):
        timestamp = row["monotonic_ns"]
        if start <= timestamp < finish:
            script = row["script"] or "(anonymous)"
            script_counts[script] += row["count"]
            active_counts[timestamp] += row["count"] if script != "(none)" else 0
    experience = {skill: round(last[skill] - xp, 2) for skill, xp in first.items()}
    completed_trades = trades_after - trades_before
    summary["activity"] = {
        "experience_gained": experience,
        "trades_completed": completed_trades,
        "bots_in_combat_per_tick": stats([row["in_combat"] for row in measured]),
        "top_script_observations": dict(script_counts.most_common()),
        "bots_with_scripts_per_snapshot": stats(active_counts.values()),
    }
    if mode == "live":
        checks["noncombat_xp_gained"] = any(experience[skill] > 0 for skill in SKILLS[7:])
        checks["completed_trade_observed"] = completed_trades > 0
    log_lines = (folder / "logs/server.txt").read_text().splitlines()
    summary["server_warnings"] = sum(line.startswith("WARN:") for line in log_lines)
    summary["server_errors"] = sum(line.startswith("ERROR:") for line in log_lines)
    summary["longest_gaps"] = sorted(gaps, key=lambda gap: gap["spacing_ms"], reverse=True)[:10]
    summary["status"] = "COMPLETE" if all(checks.values()) else "PARTIAL"
    summary["outcome"] = "Measurement window finished and telemetry was flushed."
    return summary


if __name__ == "__main__":
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent
    workload = json.loads((Path(__file__).resolve().parent / "manifest.json").read_text())["workload"]
    print(json.dumps({mode: summarize(path / mode, mode, workload) for mode in ("live", "wandering")}, indent=2))
