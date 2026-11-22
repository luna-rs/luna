# Luna server benchmark - 2026-10-21

**COMPLETE** · One calendar month before the task · Historical development snapshot

The server kept up with its configured update schedule in this window. The server completed 6,000 world updates in 60m
00.0s. The average update took 30.97 ms out of the 600 ms budget. About 99 in 100 start-to-start intervals were at most
600.32 ms.

At least 800 bots changed tiles during every rolling five-minute window. Mean server CPU was 6.68% and mean resident
memory was 4,603 MiB.

Heap remaining after garbage collection rose through the hour, from a median of 1,643 MiB in the first quarter to 1,770
MiB in the last.

## Revision and provenance

Base commit `a928f437bb24f2a0af5ddc08ff03764fc40527b4`. Make bots wear their partyhats when showing off (#680). Native
smart wandering and bot activity dispatch are present. This commit updates the show-off script so bots wear partyhats.

Author TheLining at 2026-10-21T17:42:15-04:00. Committer GitHub at 2026-10-21T17:42:15-04:00, corresponding to
2026-10-21T21:42:15+00:00. The folder date is the UTC date of the committer timestamp.

Latest first-parent master commit at or before the recorded Toronto calendar-month cutoff.

Patched snapshot. SHA-256 `473d2ffcbb444ddc78abaed7d57fb4f284c1fa06ba4a290b9d94e14f4acdfd3c`.

Selection evidence

- `git log -1 --first-parent --before=2026-10-21T18:34:54.281-04:00 9cad662e70d8662ae5280be21310adc7c64b1826`
- `src/main/kotlin/engine/engine/test/gameTestHandler.kts`
- `src/main/java/io/luna/game/model/mob/movement/wandering/SmartWanderingAction.java`

## Execution and workload

Attempt started 2026-11-22T01:18:10.206+00:00. Server launch 2026-11-22T01:19:35.009+00:00. Warm-up 15m 00.6s.
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

## Timing and progress

| Metric | Mean | Median | p95 | p99 | Maximum |
| --- | ---: | ---: | ---: | ---: | ---: |
| Tick processing | 30.97 ms | 28.50 ms | 38.23 ms | 51.16 ms | 160.51 ms |
| Tick start spacing | 600.00 ms | 600.00 ms | 600.06 ms | 600.32 ms | 620.27 ms |
| Positive phase lateness | 0.03 ms | 0.00 ms | 0.06 ms | 0.36 ms | 20.25 ms |

Each tick's scheduled iteration is timed immediately around its work. Timer reads and the workload probe outside that
range do not count as processing time. The fixed-rate tick period is 600 ms. Positive phase lateness is max(0, actual
start minus the ideal sequence anchored at the first measured start). A significant gap is a start-to-start interval
strictly greater than 900 ms. Spacing and gaps include the closing start when available. Percentiles are nearest rank.

Tick attempts 6,000. Completed world ticks 6,000. Over-budget tick attempts 0 (0.00%). Significant gaps 0 with 0.00 ms
total start spacing and 0.00 ms total excess over the intended period.

Online bots below target on 0 samples. Bot position changes summed across ticks 2,804,546. Minimum bots moving per
rolling minute 797. Minimum bots moving per rolling five minutes 800. Distinct bots that moved during measurement 800.
Minimum bots recorded with wandering actions 800.

Game-thread probe mean overhead 0.13 ms. The probe's time is recorded on the next observation. It still occupies the
game thread between ticks. Process totals include the resource sampler and JVM GC log. The resource sampler's own CPU is
not split out.

## CPU, memory and garbage collection

| Resource | Mean | Median | p95 | p99 | Maximum |
| --- | ---: | ---: | ---: | ---: | ---: |
| Process CPU | 6.68% | 6.40% | 9.20% | 12.60% | 17.80% |
| Resident memory | 4,602.87 MiB | 4,622.52 MiB | 4,670.27 MiB | 4,670.50 MiB | 4,670.52 MiB |
| JVM heap used | 2,997.61 MiB | 2,987.16 MiB | 4,177.58 MiB | 4,271.09 MiB | 4,320.09 MiB |
| JVM heap committed | 4,620.00 MiB | 4,620.00 MiB | 4,620.00 MiB | 4,620.00 MiB | 4,620.00 MiB |
| JVM heap maximum | 6,144.00 MiB | 6,144.00 MiB | 6,144.00 MiB | 6,144.00 MiB | 6,144.00 MiB |
| Cgroup memory | 4,957.88 MiB | 4,953.20 MiB | 4,998.09 MiB | 4,998.52 MiB | 4,998.52 MiB |

Process CPU time 240.33 s. CPU per completed world tick 40.06 ms. The mean is time-weighted. 100% represents one fully
occupied core. CPU time uses end-point interpolation of the JVM process CPU clock when adjacent samples cover both
endpoints. p95 and peak come from approximately five-second CPU intervals.

Cgroup throttled intervals 0. Cgroup kernel throttled time 0 µs. Cgroup CPU and memory pressure and OOM events appear in
summary.json where the allocation exposes those files. Available counters cgroup_throttled_intervals,
cgroup_throttled_us, cgroup_oom_events, cgroup_oom_kills.

GC pauses 40, distinct GC identifiers 40, total pause time 1,374.12 ms and longest pause 73.93 ms. First and last post-
GC heap values 1,617.00 MiB and 1,768.00 MiB. GC events use the JVM UTC completion timestamp. Allocation-rate
instrumentation was not enabled. Post-GC values summarize the heap after collection. Growth alone does not establish a
leak.

Normal young pauses 40. Mixed pauses 0. Full collections 0. G1 old regions after the first and last recorded collection
357 and 403.

Post-GC heap medians across four equal measurement quarters 1,643 MiB, 1,678 MiB, 1,716 MiB, 1,770 MiB.

First and last sampled heap used 1,659.05 MiB and 2,776.44 MiB. The five-second resource CSV retains the time series.

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

Task started 2026-11-21T18:34:54.281-05:00 in America/Toronto. Timestamp of the Cloud download that made the benchmark
brief available for work. The calendar-month cutoff was 2026-10-21T18:34:54.281-04:00.

Measurement window in UTC from 2026-11-22T01:35:32.449+00:00 to 2026-11-22T02:35:32.449+00:00.

Window finished and telemetry was flushed.
