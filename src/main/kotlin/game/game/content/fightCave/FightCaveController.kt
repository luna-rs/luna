package game.content.fightCave

import api.predef.*
import api.predef.ext.*
import game.content.fightCave.FightCave.fightCaveWave
import game.player.Jingles
import io.luna.game.action.impl.NpcRetreatAction.RetreatPolicy
import io.luna.game.model.Direction
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.chunk.Chunk
import io.luna.game.model.map.DynamicMap
import io.luna.game.model.map.DynamicMapController
import io.luna.game.model.map.builder.DynamicMapPalette
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.PursuitAction

/**
 * Looks after a player in their copy of the Fight Cave: runs the waves, ends the run, and removes the copy once
 * they leave it.
 *
 * @author TheLining
 */
class FightCaveController(plr: Player, startWave: Int) : DynamicMapController(plr) {

    companion object {

        /**
         * Ticks from entering to the first wave.
         */
        const val FIRST_WAVE_DELAY = 10

        /**
         * Ticks from the last monster of a wave disappearing to the next wave.
         */
        const val WAVE_DELAY = 5

        /**
         * Ticks from TzTok-Jad disappearing to the player being taken out.
         */
        const val WIN_DELAY = 3

        /**
         * The monsters that count towards a wave: every cave monster but Jad's healers, which come and go with him.
         */
        val WAVE_MONSTERS = 2734..2745

        /**
         * What a first Logout click during a wave says, broken into chat lines where the game broke them.
         */
        val PAUSE_LINES = listOf(
            "Your logout request has been received. The minigame will be",
            "paused after this wave.",
            "- Clicking logout BEFORE finishing this wave will log you out without",
            "saving your progress.",
            "- Clicking logout AFTER finishing this wave will log you out and save",
            "your progress."
        )
    }

    /**
     * How a run ended, which decides what the player gets on the way out.
     */
    private enum class Ending { LEFT, DEFEATED, WON, LOGGED_OUT }

    /**
     * The wave being fought, or the next one while [countdown] runs.
     */
    var wave = startWave
        private set

    /**
     * The last wave whose monsters appeared; its number sets the Tokkul.
     */
    var reached = startWave - 1
        private set

    /**
     * Ticks until the next wave appears, or 0 while a wave is being fought.
     */
    var countdown = FIRST_WAVE_DELAY
        private set

    /**
     * Set while a wave's monsters are in the cave.
     */
    var fighting = false
        private set

    /**
     * Set by a Logout click during a wave: no new wave comes once it's over.
     */
    var pauseAsked = false
        private set

    /**
     * Set once Jad's wave is over; the player is taken out [WIN_DELAY] ticks later.
     */
    var finished = false
        private set

    /**
     * Ticks until a player who beat Jad is taken out.
     */
    private var winCountdown = 0

    /**
     * How the run ended, once it has.
     */
    private var ending: Ending? = null

    /**
     * The player's run energy as they died, which coming out doesn't restore.
     */
    private var deathEnergy = 0.0

    /**
     * The copy's map chunks, where its monsters are looked for.
     */
    private var chunks: List<Chunk> = emptyList()

    override fun enter(player: Player) {
        val map = player.dynamicMap
        chunks = DynamicMapPalette.getAllChunksInRegion(FightCave.REGION.id)
            .map { map.getInstancePosition(it.absPosition).chunk }
        player.newDialogue().npc(FightCave.MEJ_JAL, "You're on your own now JalYt, prepare to fight for",
                                 "your life!").open()
    }

    override fun process() {
        val map = player.dynamicMap
        if (ending != null || map == null || !map.contains(player.position)) return
        if (finished) {
            if (--winCountdown == 0) win()
            return
        }
        if (!fighting) {
            if (!pauseAsked && --countdown == 0) spawn(map)
            return
        }
        val monsters = monsters()
        if (monsters.isEmpty()) {
            waveOver()
        } else {
            monsters.forEach(::approach)
        }
    }

    override fun logout(): Boolean {
        if (ending == Ending.LOGGED_OUT || !fighting) return !finished
        if (!pauseAsked) {
            pauseAsked = true
            PAUSE_LINES.forEach(player::sendMessage)
            return false
        }
        // A second click before the wave is over logs out without saving, once combat allows it.
        return true
    }

    override fun beforeLogout() {
        ending = Ending.LOGGED_OUT
    }

    override fun exit(player: Player): Position {
        val map = player.dynamicMap
        // The map still holds the player until this returns, so it can't be deleted yet.
        world.scheduleOnce(1) { map.delete() }
        // A saved run is kept only by a logout; any other way out ends it.
        if (ending != Ending.LOGGED_OUT) {
            ending = ending ?: Ending.LEFT
            player.fightCaveWave = 0
        }
        // A player who teleported out is already where they're going.
        return if (map.contains(player.position)) FightCave.OUTSIDE else player.position
    }

    override fun recovery(player: Player): Position = FightCave.OUTSIDE

    /**
     * Takes the player out by the exit, with Tokkul for the waves they reached.
     */
    fun leave() {
        val reached = reached
        ending = Ending.LEFT
        player.dynamicMap.remove(player)
        if (reached > 0) {
            FightCave.give(player, FightCave.TOKKUL, FightCave.tokkul(reached))
            player.newDialogue().npc(FightCave.MEJ_JAL, "Well done in the cave, here take TokKul as reward.").open()
        } else {
            player.newDialogue().npc(FightCave.MEJ_JAL, "Well I suppose you tried... better luck next time.").open()
        }
    }

    /**
     * Notes the player's run energy as they die.
     */
    fun dying() {
        deathEnergy = player.runEnergy
    }

    /**
     * Brings a defeated player out to the city with Tokkul for the wave they died on. Their items are safe, and their
     * run energy stays as it was.
     */
    fun defeated() {
        val reached = reached
        ending = Ending.DEFEATED
        player.runEnergy = deathEnergy
        player.move(FightCave.OUTSIDE)
        if (reached > 0) FightCave.give(player, FightCave.TOKKUL, FightCave.tokkul(reached))
    }

    /**
     * Takes a player who beat Jad out with the fire cape and Tokkul, or double Tokkul if they already have a cape.
     */
    private fun win() {
        if (!player.isAlive) return
        ending = Ending.WON
        player.dynamicMap.remove(player)
        if (FightCave.hasFireCape(player)) {
            FightCave.give(player, FightCave.TOKKUL, FightCave.REPEAT_WIN_TOKKUL)
        } else {
            FightCave.give(player, FightCave.FIRE_CAPE, 1)
            FightCave.give(player, FightCave.TOKKUL, FightCave.WIN_TOKKUL)
        }
        player.newDialogue().npc(FightCave.MEJ_JAL, "You even defeated TzTok-Jad, I am most impressed!",
                                 "Please accept this gift as a reward.").open()
        player.playJingle(Jingles.FIGHT_CONTINUES)
    }

    /**
     * Ends the wave just cleared with its jingle and readies the next one, which a run saved now resumes at. Before
     * Jad's, Mej-Jal warns the player as the last monster goes. After a Logout click, no wave comes, and the game
     * logs the player out if combat allows it.
     */
    private fun waveOver() {
        fighting = false
        if (wave == FightCaveWaves.LAST) {
            finished = true
            winCountdown = WIN_DELAY
            return
        }
        player.playJingle(Jingles.FIGHT_CONTINUES)
        wave++
        countdown = WAVE_DELAY
        player.fightCaveWave = wave
        if (wave == FightCaveWaves.LAST) {
            player.newDialogue().npc(FightCave.MEJ_JAL, "Look out, here comes TzTok-Jad!").open()
        }
        if (pauseAsked && !player.combat.isLogoutBlocked) player.logout(false)
    }

    /**
     * Brings in the current wave's monsters, each at one of the spawn points picked at random for this wave. From
     * now until the wave is over, logging out loses the run.
     */
    private fun spawn(map: DynamicMap) {
        fighting = true
        reached = wave
        player.fightCaveWave = 0
        val points = FightCaveWaves.SPAWN_POINTS.shuffled(rand())
        for ((index, id) in FightCaveWaves.MONSTERS[wave - 1].withIndex()) {
            val tile = FightCaveWaves.spawnTile(points[index % points.size], npcDef(id).size)
            val npc = Npc(ctx, id, map.getInstancePosition(tile))
            npc.respawnTicks = -1
            npc.combat.retreatPolicy = RetreatPolicy.NONE
            world.npcs.add(npc)
        }
    }

    /**
     * The wave's monsters still in the copy, the dying ones included and the Tz-Kek splits with them.
     */
    private fun monsters(): List<Npc> = chunks.flatMap { chunk ->
        world.chunks.load(chunk).getAll<Npc>(EntityType.NPC).filter { it.id in WAVE_MONSTERS }
    }

    /**
     * Sends [npc] after the player from anywhere in the cave. Within view it attacks; further off, where combat
     * doesn't chase, it walks towards the player the same way a chase steps.
     */
    private fun approach(npc: Npc) {
        if (!npc.isAlive) return
        if (npc.isViewableFrom(player)) {
            if (npc.combat.target != player) npc.combat.attack(player)
        } else if (npc.walking.isEmpty) {
            val step = PursuitAction.nextStep(npc.position, player.position, 1, false) {
                world.collisionManager.traversable(npc.position, EntityType.NPC, it, npc.size(), npc.routeStrategy)
            }
            if (step != Direction.NONE) npc.walking.addStep(step)
        }
    }
}
