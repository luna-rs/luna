# Luna server benchmark - 2026-03-26

**COMPLETE** · Native-wandering substitute for the early bot checkpoints · Historical development snapshot

The server kept up with its configured update schedule in this window. The server completed 6,000 world updates in 60m
00.0s. The average update took 7.31 ms out of the 600 ms budget. About 99 in 100 start-to-start intervals were at most
614.02 ms.

At least 793 bots changed tiles during every rolling five-minute window. Mean server CPU was 194.07% and mean resident
memory was 5,902 MiB.

One or more five-minute intervals showed movement for fewer than 800 bots. Heap remaining after garbage collection rose
through the hour, from a median of 2,083 MiB in the first quarter to 2,891 MiB in the last.

## Revision and provenance

Base commit `c9552bea86a9bfaf37064ff51a43cee5d2e07420`. Merge remote-tracking branch 'origin/master'. Native
SmartWanderingAction with the stock scheduler fix, asynchronous pathfinding and paused roaming.

Author lare96 at 2026-03-26T18:02:06-04:00. Committer lare96 at 2026-03-26T18:02:06-04:00, corresponding to
2026-03-26T22:02:06+00:00. The folder date is the UTC date of the committer timestamp.

First-parent master merge containing the stock WanderingAction polling fix (PR #468). A Java call to the Kotlin spell
requirement API needs the fourth argument that Kotlin defaults to false. The isolated patch supplies that default and
adds benchmark hooks. The runtime uses compiled class directories because this revision's ClassGraph configuration scans
directories. The seeded spawn setup excludes (3013, 3237, 0) and (3185, 3434, 0). Eligible replacements stay in their
roam boxes. This single measurement is the shared native-wandering substitute for the initial framework and first bot
scripts checkpoint labels.

Patched snapshot. SHA-256 `c08139f40a520821bd3bf7114878caae3295e224c3eb422dc4f57a884c3b13b2`.

Selection evidence

- `git merge-base --is-ancestor 1e3aa339 c9552bea`
- `https://github.com/luna-rs/luna/pull/468`
- `src/main/java/io/luna/game/model/mob/wandering/SmartWanderingAction.java`
- `src/main/kotlin/game/game/skill/magic/Magic.kt`
- `src/main/java/io/luna/LunaServer.java`

## Execution and workload

Attempt started 2026-11-22T03:06:08.231+00:00. Server launch 2026-11-22T03:06:53.833+00:00. Warm-up 15m 00.6s.
Measurement 60m 00.0s.

Expected 800 native Luna bots. Observed 800 measured. Effective runtime mode DEVELOPMENT. Effective beta flag true.

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

800 active bots with a native wandering action in the action queue and observed movement progress. Distinct participants
are verified during measurement.

## Timing and progress

| Metric | Mean | Median | p95 | p99 | Maximum |
| --- | ---: | ---: | ---: | ---: | ---: |
| Tick processing | 7.31 ms | 6.51 ms | 9.50 ms | 21.94 ms | 48.06 ms |
| Tick start spacing | 600.00 ms | 600.00 ms | 600.22 ms | 614.02 ms | 643.99 ms |
| Positive phase lateness | 0.37 ms | 0.00 ms | 0.25 ms | 14.05 ms | 43.98 ms |

Each tick's scheduled iteration is timed immediately around its work. Timer reads and the workload probe outside that
range do not count as processing time. The fixed-rate tick period is 600 ms. Positive phase lateness is max(0, actual
start minus the ideal sequence anchored at the first measured start). A significant gap is a start-to-start interval
strictly greater than 900 ms. Spacing and gaps include the closing start when available. Percentiles are nearest rank.

Tick attempts 6,000. Completed world ticks 6,000. Over-budget tick attempts 0 (0.00%). Significant gaps 0 with 0.00 ms
total start spacing and 0.00 ms total excess over the intended period.

Online bots below target on 0 samples. Bot position changes summed across ticks 2,162,010. Minimum bots moving per
rolling minute 769. Minimum bots moving per rolling five minutes 793. Distinct bots that moved during measurement 800.
Minimum bots recorded with wandering actions 800.

Game-thread probe mean overhead 0.18 ms. The probe's time is recorded on the next observation. It still occupies the
game thread between ticks. Process totals include the resource sampler and JVM GC log. The resource sampler's own CPU is
not split out.

## CPU, memory and garbage collection

| Resource | Mean | Median | p95 | p99 | Maximum |
| --- | ---: | ---: | ---: | ---: | ---: |
| Process CPU | 194.07% | 205.20% | 216.60% | 256.20% | 300.20% |
| Resident memory | 5,902.06 MiB | 5,880.16 MiB | 6,154.34 MiB | 6,162.77 MiB | 6,162.91 MiB |
| JVM heap used | 3,597.81 MiB | 3,687.59 MiB | 4,834.48 MiB | 4,978.09 MiB | 5,123.57 MiB |
| JVM heap committed | 5,551.25 MiB | 5,528.00 MiB | 5,780.00 MiB | 5,780.00 MiB | 5,780.00 MiB |
| JVM heap maximum | 6,144.00 MiB | 6,144.00 MiB | 6,144.00 MiB | 6,144.00 MiB | 6,144.00 MiB |
| Cgroup memory | 6,955.56 MiB | 6,933.02 MiB | 7,177.78 MiB | 7,186.38 MiB | 7,186.57 MiB |

Process CPU time 6,986.36 s. CPU per completed world tick 1,164.39 ms. The mean is time-weighted. 100% represents one
fully occupied core. CPU time uses end-point interpolation of the JVM process CPU clock when adjacent samples cover both
endpoints. p95 and peak come from approximately five-second CPU intervals.

Cgroup throttled intervals 0. Cgroup kernel throttled time 0 µs. Cgroup CPU and memory pressure and OOM events appear in
summary.json where the allocation exposes those files. Available counters cgroup_throttled_intervals,
cgroup_throttled_us, cgroup_oom_events, cgroup_oom_kills.

GC pauses 3,507, distinct GC identifiers 3,470, total pause time 80,236.53 ms and longest pause 62.48 ms. First and last
post-GC heap values 2,257.00 MiB and 2,735.00 MiB. GC events use the JVM UTC completion timestamp. Allocation-rate
instrumentation was not enabled. Post-GC values summarize the heap after collection. Growth alone does not establish a
leak.

Normal young pauses 3,384. Mixed pauses 6. Full collections 0. G1 old regions after the first and last recorded
collection 711 and 957.

Post-GC heap medians across four equal measurement quarters 2,083 MiB, 2,140 MiB, 2,190 MiB, 2,891 MiB.

First and last sampled heap used 3,725.70 MiB and 4,372.52 MiB. The five-second resource CSV retains the time series.

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

Measurement window in UTC from 2026-11-22T03:22:09.900+00:00 to 2026-11-22T04:22:09.902+00:00.

Window finished and telemetry was flushed.

The captured server console is empty at WARN level. Runtime startup, beta confirmation, warm-up and measurement
boundaries are recorded by the native-world probe in raw/events.csv.
