# Luna with 1,500 autonomous bots

Luna held **1,500 autonomous bots online for an hour** and completed all **6,001 world ticks** within its
600 ms processing budget. Tick work took **191.12 ms at p99**, about **32% of the budget**, and peaked at
450.91 ms. The bots fought, trained skills, shopped, and completed **101 trades** while tick starts at p99
were 600.23 ms apart. [Autonomous run summary](benchmark-2026-11-23-live-bots/live/summary.json)

![Mean, 99th-percentile, and slowest tick processing times for the two 1,500-bot runs against the 600 ms budget](benchmark-2026-11-23-live-bots/comparison.png)

## How the current runs compare with benchmark history

All five one-hour runs recorded zero ticks over 600 ms and zero gaps over 900 ms between tick starts.
[Historical benchmarks](HISTORY.md)

| Metric | Mar. 26 wandering | Oct. 21 wandering | Nov. 21 wandering | Nov. 23 wandering | Nov. 23 autonomous |
| --- | ---: | ---: | ---: | ---: | ---: |
| Bots online | 800 | 800 | 800 | 1,500 | 1,500 |
| Registered NPCs | 52 | 11,674 | 11,722 | 11,722 | 11,722–11,762 |
| Completed world ticks | 6,000 | 6,000 | 6,001 | 6,001 | 6,001 |
| Mean tick work | 7.31 ms | 30.97 ms | 36.08 ms | 39.36 ms | 109.17 ms |
| p95 tick work | 9.50 ms | 38.23 ms | 44.68 ms | 53.46 ms | 160.24 ms |
| p99 tick work | 21.94 ms | 51.16 ms | 72.87 ms | 80.14 ms | 191.12 ms |
| Slowest tick | 48.06 ms | 160.51 ms | 127.59 ms | 207.66 ms | 450.91 ms |
| Mean process CPU | 194.07% | 6.68% | 7.82% | 11.31% | 114.77% |
| Mean resident memory | 5.8 GiB | 4.5 GiB | 4.8 GiB | 4.0 GiB | 4.5 GiB |
| Total GC pause time | 80.24 s | 1.37 s | 3.07 s | 3.36 s | 25.02 s |
| Evidence | [Summary](benchmark-2026-03-26-run-20261122T030608176775Z/summary.json) | [Summary](benchmark-2026-10-21/summary.json) | [Summary](benchmark-2026-11-21-run-20261121T235914749522Z/summary.json) | [Summary](benchmark-2026-11-23-live-bots/wandering/summary.json) | [Summary](benchmark-2026-11-23-live-bots/live/summary.json) |

The November 23 wandering run handled **87.5% more bots** than November 21 with the same NPC count.
Mean tick work rose by **9.1%** and p99 rose by **10.0%**. The later run used production mode and a
newer revision. [November 21 run](benchmark-2026-11-21-run-20261121T235914749522Z/) ·
[November 23 run](benchmark-2026-11-23-live-bots/wandering/summary.json)

March paired the fastest ticks with the highest process CPU and the most time spent in GC pauses.
That snapshot ran on Java 11 with 52 NPCs. October and November ran on Java 21 with over 11,600 NPCs.
Those historical comparisons span changes in world content, pathfinding, and the runtime.
[Snapshot history](HISTORY.md#how-performance-changed)

The autonomous run's p99 was **2.62×** the most recent 800-bot result. In the matched 1,500-bot runs,
autonomy took **2.77×** as much mean tick work and **10.15×** as much process CPU as wandering.
It still had about **409 ms of processing headroom at p99**, a strong result for running a bot-populated
Luna world at this scale on comparable hardware. [Autonomous run](benchmark-2026-11-23-live-bots/live/summary.json)

Tick work measures time inside the scheduled game loop. The p99 is the time within which at least
99% of measured ticks finished. Process CPU includes background workers and garbage collection, with
100% equal to one occupied core.

## What the bots did

The autonomous cohort gained XP across **18 skills**, including Fishing, Mining, Cooking, Agility, and
combat skills. On an average tick **65 bots were in combat**, and **1,499 bots changed position** during
the hour. The script samples show NPC combat, Wilderness activity, trading, shop purchases, food
restocking, and skilling. [Activity and movement data](benchmark-2026-11-23-live-bots/live/raw/)

## Setup and reproduction

Both November 23 runs used revision `f5ae3775` in production mode on Linux with an AMD EPYC 9V74
processor, a four-core CPU quota, and 16 GiB of RAM. The JVM was Temurin 21.0.8 with G1 and a
6 GiB maximum heap. Each run started with fresh bot accounts and JSON saves, 11,722 registered NPCs,
and zero networked players. The driver supplied the login cohort to hold the population at 1,500,
using identical spawn tiles across 16 overworld areas. Each run had a 15-minute warm-up at full
population followed by one measured hour. The control used Luna's native smart wandering action.
[Tested revision](https://github.com/luna-rs/luna/commit/f5ae37757edddc3fa9e47996d9a3cdd231e29289)

The source patch, configuration, raw telemetry, and commands are archived with the run.
[Run files](benchmark-2026-11-23-live-bots/) · [Reproduce the benchmark](benchmark-2026-11-23-live-bots/reproduce.txt)
