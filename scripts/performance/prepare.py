#!/usr/bin/env python3
"""Prepare an isolated Luna worktree. Emit the entire source patch separately from local configuration."""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
from pathlib import Path
from zipfile import ZipFile

SCRIPTS = Path(__file__).resolve().parent
JAVA = Path("src/main/java/io/luna/game")
CACHE_SHA256 = "5467efe75598a77f6f8d1960178cc21ad32c17613143f8aeabfcdfd23a172ed4"


def replace_once(source, old, new):
    if source.count(old) != 1:
        raise ValueError(f"Expected exactly one source match for {old!r}")
    return source.replace(old, new, 1)


def patch_sources(tree):
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=tree, text=True).strip()
    if revision == "c9552bea86a9bfaf37064ff51a43cee5d2e07420":
        combat = tree / JAVA / "model/mob/combat/PlayerCombatContext.java"
        source = combat.read_text()
        combat.write_text(replace_once(source,
            "Magic.INSTANCE.checkRequirements(player, spell.getLevel(), spell.getRequired());",
            "Magic.INSTANCE.checkRequirements(player, spell.getLevel(), spell.getRequired(), false);"))
    source = (SCRIPTS / "BenchmarkProbe.java").read_text()
    if not (tree / JAVA / "model/mob/movement/wandering").is_dir():
        source = source.replace(".model.mob.movement.wandering.", ".model.mob.wandering.")
    if not any((tree / JAVA / "model/mob/combat").rglob("CombatContext.java")):
        source = replace_once(source, """            if (bot.getCombat().inCombat()) {
                ++inCombat;
            }
""", "").replace('+ wanderingCount + "," + inCombat + ","', '+ wanderingCount + "," + "" + ","')
    (tree / JAVA / "BenchmarkProbe.java").write_text(source)
    service = tree / JAVA / "GameService.java"
    service.write_text(replace_once(service.read_text(), """    protected void runOneIteration() {
        process();
    }""", """    protected void runOneIteration() {
        if (!BenchmarkProbe.ENABLED) {
            process();
            return;
        }
        long worldTick = world.getCurrentTick();
        long start = System.nanoTime();
        process();
        long end = System.nanoTime();
        BenchmarkProbe.tick(world, start, end, world.getCurrentTick() != worldTick);
    }"""))
    bot = tree / JAVA / "model/mob/bot/Bot.java"
    bot.write_text(replace_once(bot.read_text(), "    public void process() {", """    private boolean benchmarkWanderingOnly;

    /** Suppresses decision scripts for this disposable workload participant. */
    public void enableBenchmarkWanderingOnly() {
        benchmarkWanderingOnly = true;
    }

    /** Returns whether this disposable bot is confined to the benchmark workload. */
    public boolean isBenchmarkWanderingOnly() {
        return benchmarkWanderingOnly;
    }

    public void process() {
        if (benchmarkWanderingOnly) {
            cycles++;
            return;
        }"""))

    server = tree / "src/main/java/io/luna/LunaServer.java"
    source = server.read_text()
    old_bind = "bootstrap.bind(Luna.settings().game().port())"
    if old_bind in source:
        server.write_text(replace_once(source, old_bind, 'bootstrap.bind("127.0.0.1", Luna.settings().game().port())'))

    handlers = list(tree.glob("src/main/kotlin/**/randomEventHandlers.kts"))
    for handler in handlers:
        source = handler.read_text()
        hook = "on(PlayerTickEvent::class) {\n    randomEvents.restore(plr)\n    randomEvents.tick(plr)\n}"
        if hook in source:
            handler.write_text(replace_once(source, hook, """on(PlayerTickEvent::class) {
    if (!io.luna.game.BenchmarkProbe.isParticipant(plr)) {
        randomEvents.restore(plr)
        randomEvents.tick(plr)
    }
}"""))


def configure(tree, out, cache):
    data = tree / "data"
    source = data / "luna.jsonc"
    if not source.exists():
        source = data / "luna.json"
    # Gson parses the runtime JSONC. This pattern preserves comment-like text in quoted strings.
    strings_or_comments = r'("(?:\\.|[^"\\])*")|//[^\n]*|/\*[\s\S]*?\*/'
    config = json.loads(re.sub(strings_or_comments, lambda m: m.group(1) or "", source.read_text()))
    game = config["game"]
    game.update(runtime_mode="DEVELOPMENT", port=43596, bind_address="127.0.0.1", serializer="JsonGameSerializer")
    if "websocket" in game:
        game["websocket"]["enabled"] = False
    config.setdefault("bots", {})["base_online_count"] = 0
    config["logging"].update(root_level="WARN", output_type="MIXED", active_file_logs=[])
    config["database"].update(host="127.0.0.1", username="unused_benchmark", password="unused")
    source.write_text(json.dumps(config, indent=2) + "\n")
    config["database"]["password"] = "[redacted; unused by JSON persistence]"
    (out / "config" / source.name).write_text(json.dumps(config, indent=2) + "\n")
    install_cache(data, cache)
    for save_dir in ("game/saved_players", "game/bots/saved_bots"):
        (data / save_dir).mkdir(parents=True, exist_ok=True)
    return source.relative_to(tree)


def install_cache(data, cache):
    if hashlib.sha256(cache.read_bytes()).hexdigest() != CACHE_SHA256:
        raise ValueError("Cache archive SHA-256 did not match the verified OpenRS2 revision 377 archive")
    target = data / "game/cache"
    target.mkdir(parents=True, exist_ok=True)
    with ZipFile(cache) as archive:
        for member in ["main_file_cache.dat", *(f"main_file_cache.idx{i}" for i in range(5))]:
            with archive.open(f"cache/{member}") as content, (target / member).open("xb") as output:
                shutil.copyfileobj(content, output)


def prepare(tree, out, cache):
    patch_sources(tree)
    probe = JAVA / "BenchmarkProbe.java"
    subprocess.run(["git", "add", "-N", str(probe)], cwd=tree, check=True)
    patch = subprocess.check_output(["git", "diff", "--binary", "HEAD", "--", "src"], cwd=tree)
    (out / "patches/instrumentation.patch").write_bytes(patch)
    digest = hashlib.sha256(patch).hexdigest()
    (out / "patches/sha256.txt").write_text(f"{digest}  instrumentation.patch\n")
    configure(tree, out, cache)
    return digest


def replay(tree, out, cache, archived, expected_digest):
    original_patch = archived / "patches/instrumentation.patch"
    patch = original_patch.read_bytes()
    digest = hashlib.sha256(patch).hexdigest()
    if digest != expected_digest:
        raise ValueError("Archived patch digest does not match its manifest")
    subprocess.run(["git", "apply", "--binary", str(original_patch)], cwd=tree, check=True)
    (out / "patches/instrumentation.patch").write_bytes(patch)
    (out / "patches/sha256.txt").write_text(f"{digest}  instrumentation.patch\n")
    snapshot = next(path for path in (archived / "config").iterdir() if path.name in ("luna.json", "luna.jsonc"))
    config = json.loads(snapshot.read_text())
    config["database"]["password"] = "unused"
    (tree / "data" / snapshot.name).write_text(json.dumps(config, indent=2) + "\n")
    shutil.copyfile(snapshot, out / "config" / snapshot.name)
    install_cache(tree / "data", cache)
    for save_dir in ("game/saved_players", "game/bots/saved_bots"):
        (tree / "data" / save_dir).mkdir(parents=True, exist_ok=True)
    return digest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tree", type=Path)
    parser.add_argument("out", type=Path)
    parser.add_argument("cache", type=Path)
    args = parser.parse_args()
    print(prepare(args.tree.resolve(), args.out.resolve(), args.cache.resolve()))
