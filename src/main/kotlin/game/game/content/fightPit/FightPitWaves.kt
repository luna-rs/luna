package game.content.fightPit

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.impl.NpcRetreatAction.RetreatPolicy
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.PursuitAction

/**
 * What the TzHaar send in when a round drags on: a wave of Tz-Kih, then Tz-Kek, then Tok-Xil, one monster for
 * each player still standing, and after those, hits of 1 on everyone until the round ends.
 *
 * @author TheLining
 */
object FightPitWaves {

    /**
     * Ticks from the signal to the first wave, and between waves.
     */
    const val WAVE_GAP = 500

    /**
     * Ticks from the signal to the first hit of 1.
     */
    const val ONES_START = 2000

    /**
     * Ticks between hits of 1.
     */
    const val ONES_EVERY = 4

    /**
     * Each wave's monster, as the pair of ids it comes in.
     */
    val WAVES = listOf(listOf(2627, 2628), listOf(2629, 2630), listOf(2631, 2632))

    /**
     * The waves' monsters still in the pit.
     */
    private val monsters = ArrayList<Npc>()

    /**
     * Runs the waves and the hits of 1 on the tick [clock] ticks after the signal, against the players [standing].
     */
    fun process(clock: Int, standing: List<Player>, tiles: List<Position>) {
        if (clock % WAVE_GAP == 0 && clock / WAVE_GAP in 1..WAVES.size) {
            spawn(WAVES[clock / WAVE_GAP - 1], standing, tiles)
        }
        if (clock >= ONES_START && (clock - ONES_START) % ONES_EVERY == 0) {
            standing.forEach { it.damage(1) }
        }
        monsters.removeIf { it.state != EntityState.ACTIVE }
        monsters.forEach { approach(it, standing) }
    }

    /**
     * Takes every wave monster out of the pit.
     */
    fun clear() {
        for (npc in monsters) {
            npc.combat.target = null
            npc.actions.interruptAll()
            world.removeNpc(npc)
        }
        monsters.clear()
    }

    /**
     * Brings in one monster of [ids] for each player [standing], on random pit tiles with room for it.
     */
    private fun spawn(ids: List<Int>, standing: List<Player>, tiles: List<Position>) {
        val open = tiles.toHashSet()
        for (i in standing.indices) {
            val id = ids[rand().nextInt(ids.size)]
            val size = npcDef(id).size
            val tile = tiles.shuffled(rand()).firstOrNull { tile ->
                (0 until size).all { dx -> (0 until size).all { dy -> tile.translate(dx, dy) in open } }
            } ?: continue
            val npc = Npc(ctx, id, tile)
            npc.respawnTicks = -1
            npc.combat.retreatPolicy = RetreatPolicy.NONE
            if (world.npcs.add(npc)) monsters += npc
        }
    }

    /**
     * Sends [npc] after the nearest player standing, from anywhere in the pit. Within view it attacks; further off,
     * where combat doesn't chase, it walks towards them the same way a chase steps.
     */
    private fun approach(npc: Npc, standing: List<Player>) {
        if (!npc.isAlive) return
        val target = (npc.combat.target as? Player)?.takeIf { it in standing }
            ?: standing.minByOrNull { it.position.computeLongestDistance(npc.position) } ?: return
        if (npc.isViewableFrom(target)) {
            if (npc.combat.target != target) npc.combat.attack(target)
        } else if (npc.walking.isEmpty) {
            val step = PursuitAction.nextStep(npc.position, target.position, 1, false) {
                world.collisionManager.traversable(npc.position, EntityType.NPC, it, npc.size(), npc.routeStrategy)
            }
            if (step != Direction.NONE) npc.walking.addStep(step)
        }
    }
}
