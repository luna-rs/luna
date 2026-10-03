# Luna Companion

A local Codex plugin for developing Luna and inspecting a running Luna server on the same PC.

## Included

- Luna-specific development instructions for Java/Kotlin, content scripts, bot coroutines, and trade balancing.
- `luna_status`: current tick, game service state, online count, human count, and bot count.
- `luna_online_players`: online usernames and whether each is a bot.
- `luna_inspect_trade`: both offers' item IDs and quantities, offer/confirmation stage, and first-screen acceptance flags.

The Java bridge is read-only, listens on `127.0.0.1:8787`, and requires a shared token. It is disabled unless `LUNA_COMPANION_TOKEN` is supplied to Luna. Snapshots run through `GameService.sync` on the game thread. Player objects, passwords, IP addresses, inventories, and banks are never serialized.

The plugin does not expose bot valuations, reasons for a decision, logs, historical trades, bot controls, or second-screen acceptance flags. Those require additional instrumentation. It cannot connect to a PC from ChatGPT in a browser.

## Requirements

- A checkout containing this change, JDK 21, and the usual Luna client/cache setup.
- Python 3.10 or newer available as `python` (or edit `mcp.json` to the absolute Python executable).
- A Codex version with `codex plugin marketplace add` and `codex plugin add`.

## Install on your PC

Run from the Luna repository root:

```text
python tools/luna-companion/server/configure.py
codex plugin marketplace add .
codex plugin add luna-companion@luna-local
```

The configuration helper creates a random token in your user profile at `.luna-companion/token`. It preserves an existing token, never prints the token, and does not write it to Git. Codex's adapter reads that file by default, so it does not need to inherit a secret environment variable. Windows uses the permissions of your user profile; keep that profile private.

Start Luna with the same token. In PowerShell, from the Luna root:

```powershell
$env:LUNA_COMPANION_TOKEN = (Get-Content -Raw "$HOME\.luna-companion\token").Trim()
.\gradlew.bat run
```

On Linux/macOS:

```bash
export LUNA_COMPANION_TOKEN="$(cat "$HOME/.luna-companion/token")"
./gradlew run
```

If you launch Luna from IntelliJ, put the same token in the Luna run configuration's environment variables. Do not paste it into chat or commit it to the project.

Use Codex with the Luna checkout open and try:

- "Use Luna Companion to show how many bots are online."
- "Inspect test_bot's current trade and show both offers."
- "Help me balance 100 feathers at 1 GP each against 50 GP using Luna's existing trade stages."

Creating the files and passing fixture tests does not establish a connection to your own running server. Verify `luna_status` on your PC after installation. The MCP protocol uses newline-delimited JSON over stdio; only protocol messages are written to stdout.

## Configuration and troubleshooting

- Default bridge port: `8787`. To change it, set `LUNA_COMPANION_PORT` in both Luna's and Codex's environments. For Codex hosts that filter environment variables, configure the adapter's MCP `env` with the non-secret port value.
- The adapter prefers `LUNA_COMPANION_TOKEN` when present; otherwise it reads your user-profile token file.
- A missing token leaves the bridge disabled. An invalid token or port, or a port already in use, fails the enabled bridge startup and currently causes Luna's existing startup error handler to exit.
- "Cannot reach Luna's local bridge": ensure Luna has fully started with the token and that Codex runs on the same operating system/network environment. WSL and native Windows may need additional localhost routing.
- HTTP 401: token mismatch. HTTP 503: game snapshot failed or the game thread did not respond within three seconds.
- If Python is not found, edit `mcp.json` to use your installed Python executable, then remove and add the plugin again.
- To refresh plugin source changes: `codex plugin remove luna-companion@luna-local`, then `codex plugin add luna-companion@luna-local`.
- Disable the bridge by removing `LUNA_COMPANION_TOKEN` from the Luna process and starting Luna again.
- Uninstall with `codex plugin remove luna-companion@luna-local`. Remove the marketplace with `codex plugin marketplace remove luna-local` if you no longer use it.

## Validation

From the repository root:

```text
python -m unittest discover -s tools/luna-companion/tests -v
```

Run Luna's Java tests using JDK 21:

```text
./gradlew test
```

`LunaCompanionBridgeTest` exercises real HTTP handlers with a mocked world. Python tests run the real stdio adapter against a fixture HTTP server and verify initialization, tool discovery, reads, token errors, argument validation, and malformed protocol messages. Neither suite starts a full Luna game world.
