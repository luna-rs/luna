# Luna Companion runtime harness

Luna Companion 0.2 combines a local process harness with authenticated game-state
collectors. It runs against the dedicated agent clone on a `codex/*` branch.

## Runtime workflow

1. `luna_build` builds an installable distribution. Poll `luna_job` until exitCode is 0.
2. `luna_run_tests` runs Java tests, optionally with a class/method `filter`.
   Poll `luna_job` for the exit code and fresh JUnit suite results.
3. `luna_start` launches the built runtime directly. It does not implicitly rebuild.
4. Read `luna_logs` with a job ID and `after` cursor for startup errors and output.
5. Use `luna_status`, `luna_online_players`, and `luna_inspect_trade` for game state.
6. Use `luna_diagnostics`, `luna_threads`, `luna_events`, and `luna_collectors` /
   `luna_collect` for diagnostics and registered game snapshots.
7. `luna_stop` force-stops only a process tree launched by this MCP session.
   Pending runtime saves can be interrupted. Use this only for the isolated test world.

`luna_harness` lists retained jobs and lifecycle events. One job runs at a time;
stop the test runtime before rebuilding. Jobs belong to the MCP session and are
stopped when its stdin closes; restarting the plugin does not reconnect to old jobs.
Completed job/log history is in memory, so save relevant output before closing a chat.

## Local configuration

`mcp.json` sets the Python executable and these non-secret environment variables:

- `LUNA_COMPANION_REPO`: dedicated clone, currently `C:/Users/cj/Documents/Codex/luna-agent`.
- `LUNA_COMPANION_JAVA_HOME`: JDK 21 installation.
- `LUNA_COMPANION_PORT`: bridge port, configured here as 8788.
- `LUNA_COMPANION_GAME_PORT`: isolated loopback game port, configured here as 43595.

Point a test client at 127.0.0.1:43595. The harness uses this clone's own data and
saved players. It does not attach to or stop the user's working server.
Ignored cache assets must be present at `data/game/cache`. Build after source changes.
The token is created by `server/configure.py` in `~/.luna-companion/token`; it is
never put in the package. Existing tokens are preserved. The adapter and spawned
runtime use the same token. Logs redact that token, known secret environment values,
credential fields from Luna's configuration, and common key/value credentials.
This is best-effort log redaction, not a guarantee for arbitrary application text.
Do not log private messages, credentials, or unnecessary player data.

## Diagnostics and limits

- Heap/non-heap memory, GC counts/timing, uptime, thread counts and deadlock IDs.
- At most 128 thread stacks with 24 frames each; no local-variable values.
- Tick count, last/max duration, and 600ms budget overruns.
- Latest 200 slow-tick/error/custom events with sequence numbers.
- Latest 2000 log lines per job, up to 200 per request; oversized lines omitted.
- Latest 20 jobs and 200 process lifecycle events per MCP session.
- JUnit suite summaries include only reports generated during the requested run.
- Snapshot responses are limited to one megabyte. JVM diagnostics do not wait
  for the game thread; game-state collectors do, with a three-second timeout.

This does not automatically observe every bot decision or arbitrary local variable.
Add explicit instrumentation for behavior under investigation. Trade snapshots
still omit valuation decisions and private second-screen acceptance flags.

## Extending game observability

Register named collectors through `RuntimeDiagnostics.register(name, context -> dto)`.
They run on the game thread and must return small, detached DTO maps. Never block,
perform network/disk I/O, or return live player/inventory objects. Bound large lists.
Collectors appear in `luna_collectors` and can be queried with `luna_collect` without
adding another MCP tool. Use explicit fields and do not include secrets or raw config.

Emit `RuntimeDiagnostics.event("bot_decision", Map.of("action", "buy", "itemId", 314))`
at meaningful decision boundaries. Event fields accept bounded scalar values;
credential-like keys are dropped. Producer code remains responsible for choosing
safe field values. Add integration tests for each collector and event producer.

## Installation and tests

From the clone, run configure.py, `codex plugin marketplace add .`, then
`codex plugin add luna-companion@luna-local`. For updates, remove and add this plugin
to refresh the installed copy. Start a new Codex chat after installing.

Run `python -m unittest discover -s tools/luna-companion/tests -v` and
`./gradlew test`. Python tests exercise actual stdio and child processes as well
as an HTTP fixture; Java tests exercise actual HTTP handlers and bounded diagnostics.
A successful fixture test alone is not a live runtime verification.
