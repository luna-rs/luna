# Luna server benchmark - 2026-03-26

**FAILED** · Native-wandering substitute for the early bot checkpoints · Historical development snapshot

Qualification ended with 800 active bots, 799 observed moving, and 52 background NPCs. Warm-up and steady-state
measurement did not start.

Before measurement the server registered up to 800 active bots, and 799 had changed tiles.

Qualification ended with 800 active bots, 799 observed moving, and 52 background NPCs. Warm-up and steady-state
measurement did not start.

## Revision and provenance

Base commit `c9552bea86a9bfaf37064ff51a43cee5d2e07420`. Merge remote-tracking branch 'origin/master'. Native
SmartWanderingAction with the stock scheduler fix, asynchronous pathfinding and paused roaming.

Author lare96 at 2026-03-26T18:02:06-04:00. Committer lare96 at 2026-03-26T18:02:06-04:00, corresponding to
2026-03-26T22:02:06+00:00. The folder date is the UTC date of the committer timestamp.

First-parent master merge containing the stock WanderingAction polling fix (PR #468). A Java call to the Kotlin spell
requirement API needs the fourth argument that Kotlin defaults to false. The isolated patch supplies that default and
adds benchmark hooks. The runtime uses compiled class directories because this revision's ClassGraph configuration scans
directories. The seeded spawn setup excludes tile (3013, 3237, 0) using another eligible tile in the same roam box.

Patched snapshot. SHA-256 `e131a6fa8a58ea5385fc1ec984ba848d996950b9b73ad23e6272a76a5bf9a7d4`.

Selection evidence

- `git merge-base --is-ancestor 1e3aa339 c9552bea`
- `https://github.com/luna-rs/luna/pull/468`
- `src/main/java/io/luna/game/model/mob/wandering/SmartWanderingAction.java`
- `src/main/kotlin/game/game/skill/magic/Magic.kt`
- `src/main/java/io/luna/LunaServer.java`

## Execution and workload

Attempt started 2026-11-22T02:52:47.366+00:00. Server launch 2026-11-22T02:53:33.045+00:00. Warm-up N/A. Measurement
N/A.

Expected 800 native Luna bots. Observed 800 pre-measurement peak. Effective runtime mode DEVELOPMENT. Effective beta
flag true.

50 bots per 16 ground-level overworld centers. Seeded sampling among viable tiles. The startup seed is 3772026. The
native wandering random source has no seed setting. Smart wandering uses NORMAL frequency, the stock pathfinder, and
destinations within an 18-tile radius of each group's center.

An active bot is the matching object in the world player map, in ACTIVE state with no pending logout. Normal world
turns, NPC processing, and the revision's bot client pipeline stay active. The patch gates activity decisions for marked
disposable bots. The automatic bot login scheduler is disabled where supported. Trackable movement is a changed position
between consecutive observations.

Spawn filter. Terrain and live collision. Prefer eight open directions within six tiles or scan up to sixteen tiles
inside the roam box. Require four open cardinal directions as a fallback. Random encounters. Benchmark-only guard where
the revision has a random encounter tick hook.

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

Qualification ended with 800 active bots, 799 observed moving, and 52 background NPCs. Warm-up and steady-state
measurement did not start.

The remaining unmoved bot, bench0195, started at (3185, 3434, 0). The next configuration uses another eligible tile in
that region. Warm-up qualification will require 800 active native wandering actions and observed movement. Distinct
movement by all 800 remains a measurement completion check.
