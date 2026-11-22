# Luna | [![Build](https://github.com/luna-rs/luna/actions/workflows/gradle.yml/badge.svg?branch=master)](https://github.com/luna-rs/luna/actions/workflows/gradle.yml) [![Discord chat](https://img.shields.io/discord/1235328604506685551)](https://discord.gg/bqkGY7cmVX) [![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](https://raw.githubusercontent.com/lare96/luna/master/LICENSE.txt)
Luna is a high-performance RuneScape server for the #377 protocol. It combines classic 2006-style gameplay 
with **autonomous player bots** that train, trade, fight, and progress alongside you.

<p align="center">
<a href="https://luna2006.rs/"><img src="data/resources/play-luna-2006.png" alt="Play Luna 2006" width="320" height="88" border="0"></a>
</p>

**Luna 2006** is a free-to-play actively hosted 2006-era experience based on this project. Come see the
gameplay for yourself!

- 5x experience for players, 1x experience for bots
- Standard economy, no item spawns
- No character/economy wipes or resets

# Meet the bots

- **Real progression**: Bots train skills, acquire gear, build wealth, and progress from level 3 to 126.
- **Dynamic behavior**: Bots make their own decisions based on their personalities, goals, resources, and experiences.
- **Player-like activities**: Bots skill, PvM, PK, trade, socialize, loot, and scam each other!

<img src="./data/resources/marketplace.gif" width="543" height="392" alt="Players gathering in the marketplace">

# Create your own world

To get started, visit the [wiki](https://github.com/luna-rs/luna/wiki) and follow the setup instructions.

# Benchmarked performance

Luna held its 600 ms tick cadence for an hour with **800 in-process wandering bots and 11,722 registered NPCs**
in the November benchmark. Processing time was **72.87 ms at p99**, with zero tick-budget overruns. If you're
evaluating an RSPS base for a busy world, the reports show the workload, hardware limits, and raw measurements
behind those numbers.

[Read the benchmark overview](data/benchmarks/README.md) | [Browse the graphics](data/benchmarks/GALLERY.md)

# Follow our development

- [Benchmarks](data/benchmarks/README.md)
- [Join the Discord](https://discord.gg/cpqFaHMdv9)

# Support our development

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/P4E72AFFM3)

# Thanks
- blakeman8192 
- Graham
- [Apollo](https://github.com/apollo-rsps/apollo) development team
- [Lost City RS](https://github.com/LostCityRS/Content/tree/377-wip) development team
- [RS Mod](https://github.com/rsmod/rsmod) development team, whose routefinder our pathfinding is based on
- All [developers](https://github.com/orgs/luna-rs/people), [contributors](https://github.com/luna-rs/luna/graphs/contributors) and supporters of the project