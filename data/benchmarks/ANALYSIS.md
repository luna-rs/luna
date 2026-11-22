# Luna performance progression

Luna’s world got substantially busier, and the server kept its 600 ms update cadence. The measured world grew
from **52 registered NPCs in March to 11,722 in November**. Over the same span, tracked gameplay Kotlin files
under `src/main/kotlin/game` grew from **278 to 1,265**. That source count describes the size of the gameplay
codebase. It includes additions such as the Ape Atoll Agility Course, expanded Slayer encounters, new shops and
dialogue, and more elaborate Blast Furnace bot behavior. [Content history](https://github.com/luna-rs/luna/compare/c9552bea...9cad662e)

Each completed snapshot held **800 active in-process wandering bots for an hour** after warm-up. There were zero
ticks over the 600 ms processing budget and zero scheduling gaps over 900 ms. The published runs used a Cloud
allocation capped at four CPU cores and 16 GiB of memory. [Run index](RUNS.md)

| Code date | Commit | Registered NPCs | Mean tick work | p99 tick work | Mean process CPU | Mean resident memory |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 2026-03-26 | `c9552bea` | 52 | 7.31 ms | 21.94 ms | 194.07% | 5.76 GiB |
| 2026-10-21 | `a928f437` | 11,674 | 30.97 ms | 51.16 ms | 6.68% | 4.50 GiB |
| 2026-11-21 | `9cad662e` | 11,722 | 36.08 ms | 72.87 ms | 7.82% | 4.79 GiB |

Process CPU is measured against one fully occupied core at 100%. Tick work is the time spent in the scheduled
game loop. Tick spacing is the elapsed time between starts. [March report](benchmark-2026-03-26-run-20261122T030608176775Z/report.md) · [October report](benchmark-2026-10-21/report.md) · [November report](benchmark-2026-11-21-run-20261121T235914749522Z/report.md)

## Whole-process CPU fell as the world expanded

March spent the least time in the scheduled tick loop. Its Java process averaged almost two cores and recorded
3,507 garbage-collection pauses totalling about 80 seconds. November averaged 7.82% of one core and recorded 64
pauses totalling about three seconds. Process CPU includes work on threads outside the game loop, including
asynchronous pathfinding and garbage collection. A low tick time can coexist with substantial server-wide work.
The March run used Java 11, and the October and November runs used Java 21. The routefinder and world population
also changed, so individual contributions can’t be inferred from these three snapshots.
[March report](benchmark-2026-03-26-run-20261122T030608176775Z/report.md) · [November report](benchmark-2026-11-21-run-20261121T235914749522Z/report.md) · [Routefinder change](https://github.com/luna-rs/luna/commit/4dd2aed5)

## The November snapshot used more tick time and held the cadence

October and November provide the closest comparison here. On the same Java version, mean tick work rose by
**5.11 ms**, p99 rose by **21.70 ms** (42%), and mean resident memory rose by **305 MiB**. Even at the higher
p99, November used **12% of the tick budget**, and its slowest observed tick took **127.59 ms**. Tick starts
remained close to 600 ms apart. All 800 bots moved in every rolling five-minute sample in both runs.
[October report](benchmark-2026-10-21/report.md) · [November report](benchmark-2026-11-21-run-20261121T235914749522Z/report.md)

November’s median post-GC heap rose from **1,755 MiB** in the first quarter to **2,080 MiB** in the last quarter.
The run recorded young collections only, leaving long-run heap retention unresolved.
[November memory and GC results](benchmark-2026-11-21-run-20261121T235914749522Z/report.md#cpu-memory-and-garbage-collection)

## How to compare RSPS bases

For developers weighing Luna against an Elvarg or Project Insanity (PI) base, the scheduled cycle is a shared
reference point. Elvarg’s public Java snapshot uses a 600 ms fixed-rate schedule. The public PI snapshot uses a
600 ms cycle, measures completed work, and subtracts it from the next sleep interval. These are source-level timing
targets for the linked revisions. [Elvarg cycle constant](https://github.com/RSPSApp/elvarg-rsps/blob/cb14109644f1455c99c8cb0c339c9d6b3dfc9e4e/ElvargServer/game/src/main/java/com/elvarg/game/GameConstants.java#L63) · [Elvarg scheduler](https://github.com/RSPSApp/elvarg-rsps/blob/cb14109644f1455c99c8cb0c339c9d6b3dfc9e4e/ElvargServer/game/src/main/java/com/elvarg/game/GameEngine.java#L25-L36) · [PI cycle constant](https://github.com/H-a-z-e/Project-Insanity/blob/c0831dcf3aa220946dc60cf2d2826a9252e42b98/Source/src/server/Server.java#L86) · [PI server loop](https://github.com/H-a-z-e/Project-Insanity/blob/c0831dcf3aa220946dc60cf2d2826a9252e42b98/Source/src/server/Server.java#L155-L170)

**Luna’s case for a populated world is measurable.** November left **527 ms of its tick budget available at
p99** while sustaining 800 wandering bots and background processing for 11,722 registered NPCs. If you are
starting a #377 project and want room for world content, the benchmark gives you concrete numbers and raw data
to evaluate. A speed ranking against Elvarg or PI would require measured runs of those revisions with the same
workload, hardware, timing definitions, and resource accounting. [November raw evidence](benchmark-2026-11-21-run-20261121T235914749522Z/raw/) · [Reproduction tooling](../../scripts/performance/README.md)

## Workload and release scope

These runs cover wandering bot actions and normal background world processing. They leave the cost of networked
player traffic and active gameplay such as combat, skilling, and trade to separate workloads. March is the native
wandering substitute for the early framework and script checkpoints. November is a provisional pre-beta
snapshot. The original build and startup attempts are available alongside all completed runs.
[All benchmark graphics](GALLERY.md) · [Run index](RUNS.md) · [Release checklist](https://github.com/luna-rs/luna/issues/581)
