# Luna performance benchmarks

Luna’s November snapshot sustained **800 in-process wandering
bots** alongside **11,722 registered NPCs** for a full hour. Tick work finished in **72.87 ms at p99**, about
**12% of the 600 ms budget**. The run completed 6,001 ticks with zero budget overruns and zero scheduling gaps over
900 ms. [November report](benchmark-2026-11-21-run-20261121T235914749522Z/report.md)

![Luna benchmark overview. All three runs held 800 wandering bots for an hour with zero tick-budget overruns.
November had 11,722 registered NPCs and 72.87 ms p99 tick work against the 600 ms budget.](overview.png)

[Full progression analysis](ANALYSIS.md) · [All 16 benchmark graphics](GALLERY.md) · [Every run and its raw evidence](RUNS.md)

## Performance as the world grew

| Code snapshot | Registered NPCs | Mean tick work | p99 tick work | Mean process CPU | Graphic |
| --- | ---: | ---: | ---: | ---: | --- |
| March 26 · `c9552bea` | 52 | 7.31 ms | 21.94 ms | 194.07% | [March](benchmark-2026-03-26-run-20261122T030608176775Z/graphic.png) |
| October 21 · `a928f437` | 11,674 | 30.97 ms | 51.16 ms | 6.68% | [October](benchmark-2026-10-21/graphic.png) |
| November 21 · `9cad662e` | 11,722 | 36.08 ms | 72.87 ms | 7.82% | [November](benchmark-2026-11-21-run-20261121T235914749522Z/graphic.png) |

In these snapshots, Luna went from 278 to 1,265 tracked gameplay Kotlin files. Additions included the Ape Atoll
Agility Course, expanded Slayer encounters, shops and dialogue, and more elaborate Blast Furnace bot behavior.
March used almost two CPU cores on average. November used **7.82% of one core** while its substantially larger
background world kept the same update cadence. [Content and engine history](https://github.com/luna-rs/luna/compare/c9552bea...9cad662e)

October and November ran on the same Java version. November added **5.11 ms of mean tick work**, **21.70 ms at
p99**, and **305 MiB of mean resident memory**. Both kept their 600 ms cadence, and all 800 bots moved in every
rolling five-minute sample. The higher p99 and rising post-GC heap are worth tracking as the world grows.
[October report](benchmark-2026-10-21/report.md) · [November report](benchmark-2026-11-21-run-20261121T235914749522Z/report.md)

## Evaluating Luna alongside Elvarg and Project Insanity

Elvarg’s public Java source schedules a fixed-rate 600 ms game loop. The public Project Insanity (PI) snapshot sets
a 600 ms cycle and subtracts completed cycle work from its sleep time. That shared target is useful when comparing
RSPS bases. Luna’s November run shows **527 ms of tick-budget headroom at p99** with 800 wandering bots in a populated
world. [Elvarg cycle constant](https://github.com/RSPSApp/elvarg-rsps/blob/cb14109644f1455c99c8cb0c339c9d6b3dfc9e4e/ElvargServer/game/src/main/java/com/elvarg/game/GameConstants.java#L63) · [PI server loop](https://github.com/H-a-z-e/Project-Insanity/blob/c0831dcf3aa220946dc60cf2d2826a9252e42b98/Source/src/server/Server.java#L155-L170)

**Choose Luna if you want a #377 base with measured headroom for a populated world.** You can inspect the
source revisions, workload, CPU and memory, tick distributions, and raw evidence behind these results. A
head-to-head speed ranking across Luna, Elvarg, and PI would require matching the workload, JVM, and hardware.
[Comparison details](ANALYSIS.md#how-to-compare-rsps-bases) · [Raw evidence by run](RUNS.md)

The runs cover in-process bot movement and ordinary background world processing. Active combat, skilling, trade,
and networked player traffic are separate workload questions. March is the measured substitute for the early bot
checkpoints. November is provisional pre-beta. The run index preserves the original attempts and their outcomes.
[Run index](RUNS.md) · [Final beta checklist](https://github.com/luna-rs/luna/issues/581)
