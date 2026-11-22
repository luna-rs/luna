# Luna server benchmark - 2026-11-21

**FAILED** · Current master · Provisional pre-beta

Stopped spawn qualification after observing incomplete movement participation and bot combat. The 15-minute warm-up
never started. The server log records the graceful shutdown.

Before measurement the server registered up to 800 active bots, and 791 had changed tiles.

Stopped spawn qualification after observing incomplete movement participation and bot combat. The 15-minute warm-up
never started. The server log records the graceful shutdown. Bot combat appeared in 205 pre-measurement tick samples.

## Revision and provenance

Base commit `9cad662e70d8662ae5280be21310adc7c64b1826`. Give the Ape Atoll cave monsters their 2006 combat (#888). World
includes Ape Atoll cave combat, native smart wandering and the bot scheduling and activity systems.

Author TheLining at 2026-11-21T18:29:40-05:00. Committer GitHub at 2026-11-21T18:29:40-05:00, corresponding to
2026-11-21T23:29:40+00:00. The folder date is the UTC date of the committer timestamp.

Fetched origin/master at task start. The open v0.5 release checklist leaves performance work, final content and polish,
source audit, TODO review, wiki review, release announcements and licensing open.

Patched snapshot. SHA-256 `ef3e4cb96626a163a73a7613e43f022198bf28ce826d670e63e439e0cd56fa38`.

Selection evidence

- `git ls-remote origin refs/heads/master`
- `https://github.com/luna-rs/luna/issues/581`
- `https://github.com/luna-rs/luna/milestone/3`

## Execution and workload

Attempt started 2026-11-21T23:43:12.702+00:00. Server launch 2026-11-21T23:43:12.703+00:00. Warm-up N/A. Measurement
N/A.

Expected 800 native Luna bots. Observed 800 pre-measurement peak. Effective runtime mode DEVELOPMENT. Effective beta
flag true.

50 bots per 16 ground-level overworld centers. Jitter up to six tiles per axis, filtered for collision. The startup seed
is 3772026. The native wandering random source has no seed setting. Smart wandering uses NORMAL frequency, the stock
pathfinder, and destinations within an 18-tile radius of each group's center.

An active bot is the matching object in the world player map, in ACTIVE state with no pending logout. Normal world
turns, NPC processing, and the revision's bot client pipeline stay active. The patch gates activity decisions for marked
disposable bots. The automatic bot login scheduler is disabled where supported. Trackable movement is a changed position
between consecutive observations.

Spawn filter. Terrain and live collision. Random encounters. Revision defaults.

## Timing and progress

| Metric | Mean | Median | p95 | p99 | Maximum |
| --- | ---: | ---: | ---: | ---: | ---: |
| Tick processing | N/A | N/A | N/A | N/A | N/A |
| Tick start spacing | N/A | N/A | N/A | N/A | N/A |
| Positive phase lateness | N/A | N/A | N/A | N/A | N/A |

Each tick's scheduled iteration is timed immediately around its work. Timer reads and the workload probe outside that
range do not count as processing time. The fixed-rate tick period is 600 ms. Positive phase lateness is max(0, actual
start minus the ideal sequence anchored at the first measured start). A significant gap is a start-to-start interval
strictly greater than 900 ms. Spacing and gaps include the closing start when available. Percentiles are nearest rank.

Tick attempts N/A. Completed world ticks N/A. Over-budget tick attempts N/A (N/A). Significant gaps N/A with N/A total
start spacing and N/A total excess over the intended period.

Online bots below target on N/A samples. Bot position changes summed across ticks N/A. Minimum bots moving per rolling
minute N/A. Minimum bots moving per rolling five minutes N/A. Distinct bots that moved during measurement N/A. Minimum
bots recorded with wandering actions N/A.

Game-thread probe mean overhead N/A. The probe's time is recorded on the next observation. It still occupies the game
thread between ticks. Process totals include the resource sampler and JVM GC log. The resource sampler's own CPU is not
split out.

## CPU, memory and garbage collection

| Resource | Mean | Median | p95 | p99 | Maximum |
| --- | ---: | ---: | ---: | ---: | ---: |
| Process CPU | N/A | N/A | N/A | N/A | N/A |
| Resident memory | N/A | N/A | N/A | N/A | N/A |
| JVM heap used | N/A | N/A | N/A | N/A | N/A |
| JVM heap committed | N/A | N/A | N/A | N/A | N/A |
| JVM heap maximum | N/A | N/A | N/A | N/A | N/A |
| Cgroup memory | N/A | N/A | N/A | N/A | N/A |

Process CPU time N/A. CPU per completed world tick N/A. The mean is time-weighted. 100% represents one fully occupied
core. CPU time uses end-point interpolation of the JVM process CPU clock when adjacent samples cover both endpoints. p95
and peak come from approximately five-second CPU intervals.

Cgroup throttled intervals N/A. Cgroup kernel throttled time N/A. Cgroup CPU and memory pressure and OOM events appear
in summary.json where the allocation exposes those files. Available counters none for this window.

GC pauses N/A, distinct GC identifiers N/A, total pause time N/A and longest pause N/A. First and last post-GC heap
values N/A and N/A. GC events use the JVM UTC completion timestamp. Allocation-rate instrumentation was not enabled.
Post-GC values summarize the heap after collection. Growth alone does not establish a leak.

Normal young pauses N/A. Mixed pauses N/A. Full collections N/A. G1 old regions after the first and last recorded
collection N/A and N/A.

First and last sampled heap used N/A and N/A. The five-second resource CSV retains the time series.

Unavailable pressure metrics reflect missing CPU or memory pressure files in the managed kernel. Cgroup deltas use the
first and last samples in the window. Exact scheduling-gap durations are in raw/scheduling-gaps.csv when a gap was
observed.

## Environment and reproduction

Managed Codex Cloud Linux allocation. CPU AMD EPYC 9V74 80-Core Processor. Affinity [0, 1, 2, 3, 4]. Effective cpuset
0-4. Cgroup cpu.max 400000 100000 (quota and period in µs). Memory limit 17179869184 bytes. Linux-6.18.57-x86_64-with-
glibc2.41.

JVM arguments -Xms2g, -Xmx6g, -XX:+UseG1GC. Exact Java version and dependency jar versions are in manifest.json. Startup
settings are in config/. The worktree contains tracked revision world data and a verified OpenRS2 revision 377 cache. It
starts without existing saved players or bots.

See reproduce.txt for exact commands. Manifest flags track the criteria for completion. Raw tick and resource files
contain all recorded phases. Summary statistics include only the applicable measurement window. Unavailable metrics are
preserved as null or displayed as N/A.

Stopped spawn qualification after observing incomplete movement participation and bot combat. The 15-minute warm-up
never started. The server log records the graceful shutdown.

Use a fresh repeat with stricter spawn validation and a benchmark-only random encounter guard. This initial attempt
retains its original source patch and raw data.
