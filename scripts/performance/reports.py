#!/usr/bin/env python3
"""Regenerate one benchmark from its manifest and saved measurements, then update the navigation index."""
import argparse
import csv
import gzip
import hashlib
import json
import shlex
import shutil
from pathlib import Path
from textwrap import fill

from PIL import Image

from render import HEIGHT, WIDTH as IMAGE_WIDTH, draw, duration, population, toronto, value
from results import analysis, save_json

SCRIPTS = Path(__file__).resolve().parent
REPORTS = SCRIPTS.parents[1] / "data/benchmarks"
WIDTH = 120
ARTIFACTS = (
    ("graphic", "graphic.png"),
    ("plain-English overview", "overview.txt"),
    ("technical report", "report.md"),
    ("manifest", "manifest.json"),
    ("raw evidence", "raw/"),
    ("reproduction", "reproduce.txt"),
)


def wrapped(*lines):
    return "\n\n".join(fill(line, width=WIDTH) for line in lines if line)


def shell_lines(command):
    lines = [""]
    for token in shlex.split(command):
        word = token if token == "&&" else shlex.quote(token)
        if len(lines[-1]) + len(word) + 1 <= WIDTH - 2:
            lines[-1] += (" " if lines[-1] else "") + word
            continue
        if lines[-1]:
            lines[-1] += " " + chr(92)
            lines.append("")
        for start in range(0, len(token), 92):
            piece = token[start:start + 92]
            lines[-1] = piece if piece == "&&" else shlex.quote(piece)
            if start + len(piece) < len(token):
                lines[-1] += chr(92)
                lines.append("")
    return "\n".join(lines)


def add_csv(path, data, fields):
    with path.open("w") as output:
        writer = csv.DictWriter(output, fieldnames=fields)
        writer.writeheader()
        writer.writerows(data)


def observations(summary, manifest):
    m = summary["metrics"]
    cpu = m.get("resources", {}).get("process_cpu_pct") or {}
    rss = m.get("resources", {}).get("rss_mib") or {}
    proc = m.get("processing_ms") or {}
    if not m:
        if summary["live"]:
            outcome = "The required measurement window is pending. The current stage is " + manifest["runner_stage"] + "."
        else:
            outcome = summary["outcome"] or "The required measurement window was never reached."
        moved = summary["highest_bot_movement_ever_count"]
        peak = summary["peak_active_bots_any_phase"]
        positive = (
            f"Before measurement the server registered up to {peak:,} active bots, and {moved:,} had changed tiles."
            if peak is not None else "Performance observations are unavailable for this attempt."
        )
        contamination = summary["pre_measurement_ticks_with_bot_combat"]
        concern = outcome
        if contamination:
            concern += f" Bot combat appeared in {contamination:,} pre-measurement tick samples."
        return outcome, positive, concern, (
            f"{peak:,} bots connected before measurement. {moved:,} had moved."
            " Steady-state performance is unavailable."
            if peak is not None else "The benchmark could not measure this workload. See the technical report for the"
                                       " source or startup evidence and the required next step."
        )
    gaps = m["scheduling_gap_count"]
    late = m["over_budget_count"]
    moving = m["bots_moving_per_300s"]["min"]
    completed = m["completed_world_ticks"]
    budget = summary["tick_budget_ms"]
    movement_target = summary["target_bots"]
    spacing = m.get("spacing_ms") or {}
    if gaps == 0 and late == 0 and completed == m["tick_attempts"]:
        cadence = "The server kept up with its configured update schedule in this window."
    elif spacing and spacing["median"] <= budget * 1.05 and gaps < m["tick_attempts"] / 20:
        cadence = "The server usually kept up with its configured schedule, with exceptions below."
    else:
        cadence = "The saved evidence shows interruptions or insufficient information to establish a steady cadence."
    performance = (
        f"{cadence} The server completed {completed:,} world updates in {duration(summary['duration_seconds'])}. "
        f"The average update took {value(proc.get('mean'), ' ms')} out of the {budget:g} ms budget. "
        f"About 99 in 100 start-to-start intervals were at most {value(spacing.get('p99'), ' ms')}."
    )
    positives = f"At least {moving:,} bots changed tiles during every rolling five-minute window."
    if cpu and rss:
        positives += f" Mean server CPU was {value(cpu['mean'], '%')} and mean resident memory was " \
                     f"{value(rss['mean'], ' MiB', 0)}."
    concerns = []
    if late:
        concerns.append(f"{late:,} game ticks exceeded the processing budget.")
    if gaps:
        concerns.append(f"{gaps:,} start-to-start gaps exceeded {summary['gap_threshold_ms_strictly_greater_than']:g} ms.")
    if m["ticks_below_target"]:
        concerns.append(f"The active bot count dropped below target on {m['ticks_below_target']:,} tick samples.")
    if moving < movement_target:
        concerns.append(f"One or more five-minute intervals showed movement for fewer than {movement_target:,} bots.")
    if m["ticks_with_participants_in_combat"]:
        concerns.append(f"At least one participant was in combat on {m['ticks_with_participants_in_combat']:,} ticks.")
    if completed != m["tick_attempts"]:
        concerns.append(f"{m['tick_attempts'] - completed:,} attempts did not complete world processing.")
    throttled = m.get("resources", {}).get("cgroup_throttled_intervals")
    if throttled:
        concerns.append(f"The Cloud CPU quota throttled the container in {int(throttled):,} quota intervals.")
    gc = m.get("gc") or {}
    heap_trend = summary["status"] == "COMPLETE" and gc.get("post_gc_continuous_quarter_growth")
    if heap_trend:
        first, *_, last = gc["post_gc_quarter_medians_mib"]
        concerns.append(
            f"Heap remaining after garbage collection rose through the hour, from a median of "
            f"{value(first, ' MiB', 0)} in the first quarter to {value(last, ' MiB', 0)} in the last."
        )
    if summary["status"] == "PARTIAL":
        concerns.append("At least one requirement for a complete record is pending or unmet.")
    if not concerns:
        concerns.append("The tick and population samples showed no major issues within the measurement window.")
    concise = (
        f"{completed:,} completed ticks. {late:,} over budget and {gaps:,} significant scheduling gaps. "
        f"At least {moving:,} bots moved in each five-minute window. "
        + ("Post-GC heap grew. Applies to wandering-only server bots." if heap_trend else
           "Applies to wandering-only server bots.")
    )
    return performance, positives, " ".join(concerns), concise


def overview(folder, manifest, summary, performance, positive, concern):
    code = manifest["code"]
    checkpoint = manifest["checkpoint"]
    status = summary["status"]
    measured_date = manifest.get("server_started_utc")
    beta = summary["effective_beta"]
    date = toronto(measured_date or manifest["attempt_started_utc"]).strftime("%Y-%m-%d %H:%M:%S %Z")
    date_text = f"Server launch {date}." if measured_date else f"Attempt recorded {date}."
    gc = summary["metrics"].get("gc") or {}
    heap_follow_up = ""
    if status == "COMPLETE" and gc.get("post_gc_continuous_quarter_growth"):
        heap_follow_up = (
            "A longer run that captures the older part of the heap being collected, with object retention sampling, "
            "would help explain the growth."
        )
    sections = (
        ("WHAT WAS TESTED", (
            f"Code date {code['date']}. {checkpoint['label']}. Commit {code['sha']}. {date_text}",
            f"{checkpoint['release']}. Effective beta mode {beta or 'unavailable'}. Status {status}. "
            f"Wandering-only workload with target {summary['target_bots']:,} bots and {population(summary)}. "
            f"Warm-up {duration(summary['warmup_seconds'])}. Measured duration {duration(summary['duration_seconds'])}.",
            f"Revision context. {checkpoint['context']}",
        )),
        ("HOW THE SERVER PERFORMED", (performance,)),
        ("WHAT WENT WELL", (positive,)),
        ("PROBLEMS AND CONCERNS", (concern,)),
        ("WHAT THE NUMBERS MEAN", (
            "A tick is one server update. Processing time measures the work in a tick. Tick spacing is the time between"
            " the start of one update and the start of the next. p99 is the time within which about 99 out of 100"
            " measured ticks finished their work.",
            "A process CPU value of 100% means one fully occupied core. Resident memory is RAM held by the JVM process."
            " The JVM heap stores Java objects. Both may grow as workload changes. A memory leak requires evidence that"
            " the program keeps objects it can no longer use.",
        )),
        ("LIMITS AND NEXT STEPS", (
            "Findings apply to this single wandering-only attempt with Luna's in-process bot clients and ordinary NPC"
            " processing. Trading, combat, skilling, conversations and mixed human-player loads are outside its scope.",
            "See reproduce.txt for the regeneration and new-attempt commands. An interrupted measurement requires a new"
            " uninterrupted window." if status != "COMPLETE" else
            "See reproduce.txt for exact reproduction and regeneration commands.",
            heap_follow_up,
        )),
    )
    (folder / "overview.txt").write_text("\n\n".join(title + "\n\n" + wrapped(*body) for title, body in sections) + "\n")


def table_row(label, data, unit=""):
    data = data or {}
    return "| " + " | ".join([label, *[value(data.get(key), unit) for key in ("mean", "median", "p95", "p99", "max")]]) + " |"


def report(folder, manifest, summary, performance, positive, concern):
    code = manifest["code"]
    checkpoint = manifest["checkpoint"]
    metrics = summary["metrics"]
    resources = metrics.get("resources", {})
    gc = metrics.get("gc") or {}
    env = manifest["environment"]
    gap_threshold = summary["gap_threshold_ms_strictly_greater_than"]
    patch = manifest.get("patch_sha256")
    instrumentation = metrics.get("instrumentation_ns") or {}
    pressure_checks = [key for key in resources if key.startswith("cgroup_") and resources[key] is not None]
    data = [
        f"# Luna server benchmark - {code['date']}",
        wrapped(f"**{summary['status']}** · {checkpoint['label']} · {checkpoint['release']}", performance, positive, concern),
        "## Revision and provenance",
        wrapped(f"Base commit `{code['sha']}`. {code['subject']}. {checkpoint['context']}",
                f"Author {code['author']} at {code['author_timestamp']}. Committer {code['committer']} at "
                f"{code['committer_timestamp']}, corresponding to {code['committer_timestamp_utc']}."
                " The folder date is the UTC date of the committer timestamp.",
                checkpoint["rationale"],
                f"Patched snapshot. SHA-256 `{patch}`." if patch else "Source patch unavailable because this attempt did"
                " not run instrumented code."),
        "Selection evidence\n\n" + "\n".join(f"- `{item}`" for item in checkpoint["evidence"]),
        "## Execution and workload",
        wrapped(f"Attempt started {manifest['attempt_started_utc']}. Server launch "
                f"{manifest.get('server_started_utc', 'unavailable')}. Warm-up {duration(summary['warmup_seconds'])}. "
                f"Measurement {duration(summary['duration_seconds'])}.",
                f"Expected {summary['target_bots']:,} native Luna bots. Observed {population(summary)}. "
                f"Effective runtime mode {summary.get('runtime_mode') or 'unavailable'}. "
                f"Effective beta flag {summary.get('effective_beta') or 'unavailable'}.",
                f"{manifest['workload']['spawn']} The startup seed is 3772026. The native wandering random source has no "
                "seed setting. Smart wandering uses NORMAL frequency, the stock pathfinder, and destinations within "
                "an 18-tile radius of each group's center.",
                "An active bot is the matching object in the world player map, in ACTIVE state with no pending logout."
                " Normal world turns, NPC processing, and the revision's bot client pipeline stay active. The patch gates"
                " activity decisions for marked disposable bots. The automatic bot login scheduler is disabled where"
                " supported. Trackable movement is a changed position between consecutive observations.",
                f"Spawn filter. {manifest['workload'].get('spawn_validation', 'Terrain and live collision.')}"
                f" Random encounters. {manifest['workload'].get('participant_random_encounters', 'Revision defaults.')}",
                manifest["workload"].get("warmup_qualification", "")),
        "## Timing and progress",
        "\n".join((
            "| Metric | Mean | Median | p95 | p99 | Maximum |",
            "| --- | ---: | ---: | ---: | ---: | ---: |",
            table_row("Tick processing", metrics.get("processing_ms"), " ms"),
            table_row("Tick start spacing", metrics.get("spacing_ms"), " ms"),
            table_row("Positive phase lateness", metrics.get("lateness_ms"), " ms"),
        )),
        wrapped(
            f"Each tick's scheduled iteration is timed immediately around its work. Timer reads and the workload probe"
            f" outside that range do not count as processing time. The fixed-rate tick period is "
            f"{summary['tick_budget_ms']:g} ms. Positive phase lateness is max(0, actual start minus the ideal sequence"
            f" anchored at the first measured start). A significant gap is a start-to-start interval strictly greater"
            f" than {gap_threshold:g} ms. Spacing and gaps include the closing start when available. Percentiles are nearest rank.",
            f"Tick attempts {value(metrics.get('tick_attempts'), decimals=0)}. Completed world ticks "
            f"{value(metrics.get('completed_world_ticks'), decimals=0)}. Over-budget tick attempts "
            f"{value(metrics.get('over_budget_count'), decimals=0)} ({value(metrics.get('over_budget_pct'), '%')}). "
            f"Significant gaps {value(metrics.get('scheduling_gap_count'), decimals=0)} with "
            f"{value(metrics.get('scheduling_gap_total_spacing_ms'), ' ms')} total start spacing and "
            f"{value(metrics.get('scheduling_gap_total_excess_ms'), ' ms')} total excess over the intended period.",
            f"Online bots below target on {value(metrics.get('ticks_below_target'), decimals=0)} samples. "
            f"Bot position changes summed across ticks {value(metrics.get('bot_tick_position_changes'), decimals=0)}. "
            f"Minimum bots moving per rolling minute {value((metrics.get('bots_moving_per_60s') or {}).get('min'), decimals=0)}. "
            "Minimum bots moving per rolling five minutes "
            f"{value((metrics.get('bots_moving_per_300s') or {}).get('min'), decimals=0)}. "
            f"Distinct bots that moved during measurement {value(metrics.get('measurement_movers'), decimals=0)}. "
            f"Minimum bots recorded with wandering actions "
            f"{value((metrics.get('wandering_bot_count') or {}).get('min'), decimals=0)}.",
            f"Game-thread probe mean overhead {value(instrumentation['mean'] / 1_000_000, ' ms') if instrumentation else 'N/A'}. "
            "The probe's time is recorded on the next observation. It still occupies the game thread between ticks."
            " Process totals include the resource sampler and JVM GC log. The resource sampler's own CPU is not split out."),
        "## CPU, memory and garbage collection",
        "\n".join((
            "| Resource | Mean | Median | p95 | p99 | Maximum |",
            "| --- | ---: | ---: | ---: | ---: | ---: |",
            table_row("Process CPU", resources.get("process_cpu_pct"), "%"),
            table_row("Resident memory", resources.get("rss_mib"), " MiB"),
            table_row("JVM heap used", resources.get("heap_used_mib"), " MiB"),
            table_row("JVM heap committed", resources.get("heap_committed_mib"), " MiB"),
            table_row("JVM heap maximum", resources.get("heap_max_mib"), " MiB"),
            table_row("Cgroup memory", resources.get("container_memory_mib"), " MiB"),
        )),
        wrapped(
            f"Process CPU time {value(resources.get('cpu_time_seconds'), ' s')}. CPU per completed world tick "
            f"{value(resources.get('cpu_ms_per_world_tick'), ' ms')}. The mean is time-weighted. 100% represents one"
            " fully occupied core. CPU time uses end-point interpolation of the JVM process CPU clock when adjacent samples"
            " cover both endpoints. p95 and peak come from approximately five-second CPU intervals.",
            f"Cgroup throttled intervals {value(resources.get('cgroup_throttled_intervals'), decimals=0)}. Cgroup kernel"
            f" throttled time {value(resources.get('cgroup_throttled_us'), ' µs', 0)}. Cgroup CPU and memory pressure and"
            f" OOM events appear in summary.json where the allocation exposes those files. Available counters "
            f"{', '.join(pressure_checks) or 'none for this window'}.",
            f"GC pauses {value(gc.get('pause_count'), decimals=0)}, distinct GC identifiers "
            f"{value(gc.get('collection_ids'), decimals=0)}, total pause time {value(gc.get('total_pause_ms'), ' ms')} "
            f"and longest pause {value(gc.get('longest_pause_ms'), ' ms')}. First and last post-GC heap values "
            f"{value(gc.get('post_gc_first_mib'), ' MiB')} and {value(gc.get('post_gc_last_mib'), ' MiB')}. "
            "GC events use the JVM UTC completion timestamp. Allocation-rate instrumentation was not enabled."
            " Post-GC values summarize the heap after collection. Growth alone does not establish a leak.",
            f"Normal young pauses {value(gc.get('normal_young_pauses'), decimals=0)}. Mixed pauses "
            f"{value(gc.get('mixed_pauses'), decimals=0)}. Full collections {value(gc.get('full_gc_pauses'), decimals=0)}. "
            f"G1 old regions after the first and last recorded collection "
            f"{value(gc.get('old_regions_first'), decimals=0)} and {value(gc.get('old_regions_last'), decimals=0)}.",
            f"Post-GC heap medians across four equal measurement quarters "
            f"{', '.join(value(item, ' MiB', 0) for item in gc['post_gc_quarter_medians_mib'])}."
            if gc.get("post_gc_quarter_medians_mib") else "",
            f"First and last sampled heap used {value(resources.get('heap_used_first_mib'), ' MiB')} and "
            f"{value(resources.get('heap_used_last_mib'), ' MiB')}. The five-second resource CSV retains the time series.",
            "Unavailable pressure metrics reflect missing CPU or memory pressure files in the managed kernel."
            " Cgroup deltas use the first and last samples in the window. Exact scheduling-gap durations are in"
            " raw/scheduling-gaps.csv when a gap was observed."),
        "## Environment and reproduction",
        wrapped(f"{env['runner']}. CPU {env['cpu_model']}. Affinity {env['sched_getaffinity']}. "
                f"Effective cpuset {env['effective_cpuset']}. Cgroup cpu.max {env['cpu_max']} (quota and period in µs). "
                f"Memory limit {env['memory_max_bytes']} bytes. {env['kernel']}.",
                f"JVM arguments {', '.join(env['heap_and_gc_args'])}. Exact Java version and dependency jar versions are in "
                "manifest.json. Startup settings are in config/. The worktree contains tracked revision world data and "
                "a verified OpenRS2 revision 377 cache. It starts without existing saved players or bots.",
                "See reproduce.txt for exact commands. Manifest flags track the criteria for completion. Raw tick and"
                " resource files contain all recorded phases. Summary statistics include only the applicable measurement"
                " window. Unavailable metrics are preserved as null or displayed as N/A."),
        wrapped(
            f"Task started {manifest['selection']['task_started_toronto']} in {manifest['selection']['timezone']}. "
            f"{manifest['selection']['task_start_evidence']} The calendar-month cutoff was "
            f"{manifest['selection']['calendar_month_cutoff_toronto']}."
        ) if manifest["checkpoint_key"] == "one-month" else "",
    ]
    if summary["measurement_utc"]:
        data.append(wrapped("Measurement window in UTC from " + summary["measurement_utc"]["start_utc"] + " to "
                            + summary["measurement_utc"]["end_utc"] + "."))
    for key in ("outcome", "decision", "build_verification", "warmup_environment_activity", "console_evidence"):
        if manifest.get(key):
            data.append(wrapped(manifest[key]))
    (folder / "report.md").write_text("\n\n".join(section for section in data if section) + "\n")


def write(folder):
    folder = folder.resolve()
    manifest = json.loads((folder / "manifest.json").read_text())
    summary, gaps, gc = analysis(folder, manifest)
    performance, positive, concern, note = observations(summary, manifest)
    summary["graphic_note"] = note
    if gaps:
        add_csv(folder / "raw/scheduling-gaps.csv", gaps, list(gaps[0]))
    if gc:
        add_csv(folder / "raw/gc-events.csv", gc, ("end_utc", "jvm_uptime_ns", "id", "kind", "elapsed_ms", "is_pause",
                                                 "before_b", "after_b", "capacity_b"))
    if not (folder / "raw/ticks.csv").exists():
        (folder / "raw/README.md").write_text("No runtime telemetry was produced for this attempt. See the technical report.\n")
    if not list((folder / "config").iterdir()):
        (folder / "config/README.md").write_text("This attempt did not load a runtime configuration.\n")
    if not list((folder / "patches").iterdir()):
        (folder / "patches/README.md").write_text("This attempt recorded no source patch.\n")
    if not summary["live"]:
        manifest.update(
            status=summary["status"], measurement_utc=summary["measurement_utc"], checks=summary["checks"],
            event_timestamps_utc=summary["event_timestamps_utc"],
            warmup_seconds=summary["warmup_seconds"], duration_seconds=summary["duration_seconds"],
        )
        gc_log = folder / "logs/gc.txt"
        if gc_log.is_file() and gc_log.stat().st_size > 2 * 1024**2:
            with gc_log.open("rb") as source, gc_log.with_suffix(".txt.gz").open("wb") as output:
                with gzip.GzipFile(filename="", fileobj=output, mode="wb", mtime=0) as compressed:
                    shutil.copyfileobj(source, compressed)
            gc_log.unlink()
        manifest["evidence_sha256"] = {
            str(path.relative_to(folder)): hashlib.sha256(path.read_bytes()).hexdigest()
            for directory in ("raw", "logs", "config", "patches")
            for path in sorted((folder / directory).glob("*")) if path.is_file()
        }
        save_json(folder / "manifest.json", manifest)
    save_json(folder / "summary.json", summary)
    # Read the saved summary to keep every visible value reproducible from that file.
    summary = json.loads((folder / "summary.json").read_text())
    overview(folder, manifest, summary, performance, positive, concern)
    report(folder, manifest, summary, performance, positive, concern)
    commands = dict(manifest["commands"])
    if manifest.get("patch_sha256") and list((folder / "config").glob("luna.json*")):
        commands["replay_archived_source_and_configuration"] = shlex.join(
            ["python3", str(SCRIPTS / "run.py"), "replay", str(folder)]
        )
    (folder / "reproduce.txt").write_text("LUNA BENCHMARK REPRODUCTION\n\n"
        "Run these commands from the repository root unless a working directory is part of the command.\n"
        "Replay creates a fresh dated folder and applies the archived source patch and runtime settings.\n"
        "A new attempt uses the current harness. An interrupted measurement window starts over.\n"
        "The recorded checkout, preparation, build, and start commands describe the original attempt.\n\n"
        + "\n\n".join(f"{name.replace('_', ' ').upper()}\n{shell_lines(command)}" for name, command in commands.items()) + "\n")
    draw(folder, manifest, summary)
    with Image.open(folder / "graphic.png") as graphic:
        digest = hashlib.sha256((folder / "summary.json").read_bytes()).hexdigest()
        if (graphic.size != (IMAGE_WIDTH, HEIGHT) or graphic.info.get("Summary SHA-256") != digest
                or graphic.info.get("Code SHA") != manifest["code"]["sha"]):
            raise ValueError("The graphic does not match this run's summary and source")
        graphic.verify()
    if not folder.name.startswith(f"benchmark-{manifest['code']['date']}"):
        raise ValueError("The folder name does not match the base commit's UTC code date")
    if summary["status"] == "COMPLETE" and not all(summary["checks"].values()):
        raise ValueError("A complete result must satisfy every recorded completion check")
    for file in ("manifest.json", "summary.json", "overview.txt", "report.md", "reproduce.txt", "graphic.png"):
        if not (folder / file).stat().st_size:
            raise ValueError(f"Empty benchmark output {file}")


def index():
    entries = []
    gallery = []
    for folder in sorted(REPORTS.glob("benchmark-*")):
        if not (folder / "graphic.png").exists():
            continue
        manifest = json.loads((folder / "manifest.json").read_text())
        summary = json.loads((folder / "summary.json").read_text())
        code = manifest["code"]
        checkpoint = manifest["checkpoint"]
        timestamp = manifest.get("server_started_utc") or manifest["attempt_started_utc"]
        disposition = "Execution" if "server_started_utc" in manifest else "Attempt"
        links = "\n".join(f"- [{label}]({folder.name}/{path})" for label, path in ARTIFACTS)
        identity = wrapped(
            f"Commit `{code['sha']}`. {disposition} date {toronto(timestamp).date()} Toronto. "
            f"{checkpoint['release']}. **{summary['status']}**."
        )
        entries.append(
            f"### {code['date']} — {checkpoint['label']}\n\n"
            f"{identity}\n\n"
            f"800-bot wandering workload · {population(summary)} · {duration(summary['duration_seconds'])} measured.\n\n"
            f"{links}"
        )
        gallery.append(
            f"## {code['date']} · {checkpoint['label']} · {summary['status']}\n\n"
            f"Commit `{code['sha'][:8]}` · Attempt `{manifest['run_id']}`\n\n"
            f"![{checkpoint['label']} benchmark results]({folder.name}/graphic.png)\n\n"
            f"[Technical report]({folder.name}/report.md) · [Manifest]({folder.name}/manifest.json)"
        )
    (REPORTS / "GALLERY.md").write_text(
        "# Benchmark gallery\n\n[Benchmark overview](README.md) · [Run index](RUNS.md)\n\n"
        + "\n\n".join(gallery) + "\n"
    )
    (REPORTS / "RUNS.md").write_text("# Benchmark run index\n\n"
        "[Benchmark overview](README.md) · [All benchmark graphics](GALLERY.md)\n\n"
        "[Reproduction workflow](../../scripts/performance/README.md)\n\n"
        "Folder dates use the base Git commit's UTC committer date. Execution dates are recorded separately.\n\n"
        "- [ ] Final beta benchmark after the release checklist is complete.\n"
        "  [Release checklist](https://github.com/luna-rs/luna/issues/581)\n\n"
        + "\n\n".join(entries) + "\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path)
    args = parser.parse_args()
    write(args.folder)
    index()
