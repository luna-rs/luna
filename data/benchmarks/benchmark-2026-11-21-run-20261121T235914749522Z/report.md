# Luna server benchmark - 2026-11-21

**COMPLETE** · Current master · Provisional pre-beta

The server kept up with its configured update schedule in this window. The server completed 6,001 world updates in 60m
00.6s. The average update took 36.08 ms out of the 600 ms budget. About 99 in 100 start-to-start intervals were at most
600.25 ms.

At least 800 bots changed tiles during every rolling five-minute window. Mean server CPU was 7.82% and mean resident
memory was 4,908 MiB.

Heap remaining after garbage collection rose through the hour, from a median of 1,755 MiB in the first quarter to 2,080
MiB in the last.

## Revision and provenance

Base commit `9cad662e70d8662ae5280be21310adc7c64b1826`. Give the Ape Atoll cave monsters their 2006 combat (#888). World
includes Ape Atoll cave combat, native smart wandering and the bot scheduling and activity systems.

Author TheLining at 2026-11-21T18:29:40-05:00. Committer GitHub at 2026-11-21T18:29:40-05:00, corresponding to
2026-11-21T23:29:40+00:00. The folder date is the UTC date of the committer timestamp.

Fetched origin/master at task start. The open v0.5 release checklist leaves performance work, final content and polish,
source audit, TODO review, wiki review, release announcements and licensing open.

Patched snapshot. SHA-256 `ea8a3c5c06315f538ae094fb8087b3e78571370cc37591292fcca3cfa4f1c2cd`.

Selection evidence

- `git ls-remote origin refs/heads/master`
- `https://github.com/luna-rs/luna/issues/581`
- `https://github.com/luna-rs/luna/milestone/3`

## Execution and workload

Attempt started 2026-11-21T23:59:14.819+00:00. Server launch 2026-11-22T00:02:08.270+00:00. Warm-up 15m 00.6s.
Measurement 60m 00.6s.

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
| Tick processing | 36.08 ms | 33.06 ms | 44.68 ms | 72.87 ms | 127.59 ms |
| Tick start spacing | 600.00 ms | 600.00 ms | 600.08 ms | 600.25 ms | 604.34 ms |
| Positive phase lateness | 0.02 ms | 0.00 ms | 0.07 ms | 0.28 ms | 4.38 ms |

Each tick's scheduled iteration is timed immediately around its work. Timer reads and the workload probe outside that
range do not count as processing time. The fixed-rate tick period is 600 ms. Positive phase lateness is max(0, actual
start minus the ideal sequence anchored at the first measured start). A significant gap is a start-to-start interval
strictly greater than 900 ms. Spacing and gaps include the closing start when available. Percentiles are nearest rank.

Tick attempts 6,001. Completed world ticks 6,001. Over-budget tick attempts 0 (0.00%). Significant gaps 0 with 0.00 ms
total start spacing and 0.00 ms total excess over the intended period.

Online bots below target on 0 samples. Bot position changes summed across ticks 2,813,745. Minimum bots moving per
rolling minute 797. Minimum bots moving per rolling five minutes 800. Distinct bots that moved during measurement 800.
Minimum bots recorded with wandering actions 800.

Game-thread probe mean overhead 0.13 ms. The probe's time is recorded on the next observation. It still occupies the
game thread between ticks. Process totals include the resource sampler and JVM GC log. The resource sampler's own CPU is
not split out.

## CPU, memory and garbage collection

| Resource | Mean | Median | p95 | p99 | Maximum |
| --- | ---: | ---: | ---: | ---: | ---: |
| Process CPU | 7.82% | 7.40% | 12.00% | 14.20% | 18.80% |
| Resident memory | 4,907.93 MiB | 4,930.88 MiB | 4,969.93 MiB | 4,970.00 MiB | 4,970.00 MiB |
| JVM heap used | 3,199.73 MiB | 3,196.21 MiB | 4,370.81 MiB | 4,461.98 MiB | 4,519.36 MiB |
| JVM heap committed | 4,868.00 MiB | 4,868.00 MiB | 4,868.00 MiB | 4,868.00 MiB | 4,868.00 MiB |
| JVM heap maximum | 6,144.00 MiB | 6,144.00 MiB | 6,144.00 MiB | 6,144.00 MiB | 6,144.00 MiB |
| Cgroup memory | 5,382.99 MiB | 5,248.18 MiB | 6,096.90 MiB | 6,310.62 MiB | 6,326.14 MiB |

Process CPU time 281.39 s. CPU per completed world tick 46.89 ms. The mean is time-weighted. 100% represents one fully
occupied core. CPU time uses end-point interpolation of the JVM process CPU clock when adjacent samples cover both
endpoints. p95 and peak come from approximately five-second CPU intervals.

Cgroup throttled intervals 0. Cgroup kernel throttled time 0 µs. Cgroup CPU and memory pressure and OOM events appear in
summary.json where the allocation exposes those files. Available counters cgroup_throttled_intervals,
cgroup_throttled_us, cgroup_oom_events, cgroup_oom_kills.

GC pauses 64, distinct GC identifiers 64, total pause time 3,072.12 ms and longest pause 78.38 ms. First and last post-
GC heap values 1,726.00 MiB and 2,141.00 MiB. GC events use the JVM UTC completion timestamp. Allocation-rate
instrumentation was not enabled. Post-GC values summarize the heap after collection. Growth alone does not establish a
leak.

Normal young pauses 64. Mixed pauses 0. Full collections 0. G1 old regions after the first and last recorded collection
388 and 477.

Post-GC heap medians across four equal measurement quarters 1,755 MiB, 1,842 MiB, 1,925 MiB, 2,080 MiB.

First and last sampled heap used 2,415.99 MiB and 3,705.23 MiB. The five-second resource CSV retains the time series.

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

Measurement window in UTC from 2026-11-22T00:18:01.409+00:00 to 2026-11-22T01:18:02.010+00:00.

Window finished and telemetry was flushed.

Historical build validation and a 35-second legacy server startup check ran during warm-up. They ended before the
steady-state window. No builds or auxiliary server checks ran during measurement.
