# Luna wandering benchmarks

From the Codex working checkout, run one checkpoint or a sequential suite.

```sh
python3 scripts/performance/run.py run current
python3 scripts/performance/run.py suite initial-framework first-scripts runnable-framework-early \
  current one-month native-wander native-wander-fixed
```

The runner uses the verified OpenRS2 archive at `/workspace/.onboarding/luna-cache-377-openrs2-657.zip` and
the Cloud Gradle cache at `/workspace/.gradle`. Current and October builds use the installed Temurin 21.0.8+9 JDK.
Older builds use Temurin 11.0.33.1+1 in `/workspace/luna-benchmark-work`. The Cloud Java trust store is needed if
the JDK's default trust store does not trust the Cloud HTTPS proxy. Report rendering uses Python 3 with Pillow and
DejaVu Sans. The cache archive is listed by OpenRS2 as cache 657.
[OpenRS2 cache 657](https://archive.openrs2.org/caches/runescape/657)

Each attempt owns a fresh Git worktree with the same empty save-state setup. The runner records the effective
configuration and source patch next to the results. The archive hash is checked before extraction. The runner
takes a lock to keep benchmark servers sequential and saves telemetry while the VM runs. If a Cloud allocation or
the server stops, the raw files remain in the report folder. Rerun the checkpoint in a fresh folder for a new
continuous window. Regeneration reads saved files and works while the server is running. Saved manifests and
reproduction files retain the original Cloud workspace paths.

```sh
python3 scripts/performance/reports.py data/benchmarks/benchmark-2026-11-21
python3 scripts/performance/run.py replay data/benchmarks/benchmark-2026-11-21
```

PNG layout is fixed at 1260 × 1320 pixels. The tick display presents exact formatted values in fixed columns with
a numerical budget reference. Its text cells have equal size and no variable bar scale. Resource measurements use
MiB for both RSS and heap. Process CPU uses one fully occupied core as 100%. The fixed-rate timing definitions and
measurement-window checks are in each report and manifest.

Luna's benchmark participants are native in-process bots that enter the world through the normal bot account
login path. The isolated hook turns off autonomous activity decisions for those participants and uses the existing
smart wandering action. The patch never implements a route, replaces the pathfinder, or teleports a participant
during the workload. The configured destinations are inside sixteen 18-tile ground-level roam boxes. The native
pathfinder may use adjacent tiles as it selects a route.

Regenerate the README infographic from the three published snapshots after installing Pillow and DejaVu Sans.

```sh
python3 scripts/performance/overview.py
```

## Final beta

The final beta entry stays open in the performance index while the release checklist has pending work. When the
release commit is verified, add a `final-beta` entry in `checkpoints.json` with its full SHA, release label,
revision context and evidence. Start a fresh run from the Codex working checkout.

```sh
python3 scripts/performance/run.py run final-beta
```
