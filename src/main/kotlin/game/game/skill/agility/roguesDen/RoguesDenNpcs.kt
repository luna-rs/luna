package game.skill.agility.roguesDen

import api.predef.*
import game.skill.agility.roguesDen.RoguesDen.denRun
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc

/**
 * The maze's moving dangers: the two Spin Blades circling the north-east square, and the Rogue Guards, who close in on
 * a player and send them back to the start unless flash powder has dazed them.
 *
 * @author TheLining
 */
object RoguesDenNpcs {

    /**
     * The Spin Blades' NPC ids.
     */
    private val SPIN_BLADES = setOf(2272, 2273)

    /**
     * The Rogue Guards' NPC ids.
     */
    val GUARDS = setOf(2267, 2268, 2269)

    /**
     * How far a guard sees a player from, and how long flash powder dazes him, in ticks.
     */
    const val GUARD_SIGHT = 4
    const val STUN_TICKS = 8

    /**
     * The square the Spin Blades circle, anticlockwise from its north-west corner: south down the west side, east
     * along the south side, north up the east side and west along the north side.
     */
    private val LOOP: List<Position> = run {
        val west = 3044
        val east = 3059
        val south = 5090
        val north = 5105
        (north downTo south).map { Position(west, it, 1) } +
            (west + 1..east).map { Position(it, south, 1) } +
            (south + 1..north).map { Position(east, it, 1) } +
            (east - 1 downTo west + 1).map { Position(it, north, 1) }
    }

    /**
     * The maze's NPCs, found once their spawns are in.
     */
    private val blades = ArrayList<Npc>()
    private val guards = ArrayList<Npc>()

    /**
     * The tick each dazed guard comes round on.
     */
    private val dazedUntil = HashMap<Npc, Long>()

    /**
     * The earliest tick the NPCs are looked for again, while some are missing.
     */
    private var nextFind = 0L

    /**
     * Moves the blades and guards one tick.
     */
    fun tick() {
        val stale = blades.isEmpty() || guards.isEmpty() || (blades + guards).any { it.state != EntityState.ACTIVE }
        if (stale && world.currentTick >= nextFind) find()
        blades.forEach { moveBlade(it) }
        guards.forEach { moveGuard(it) }
    }

    private fun find() {
        nextFind = world.currentTick + 50
        blades.clear()
        guards.clear()
        for (npc in world.npcs) {
            if (!RoguesDenMap.inBox(npc.position)) continue
            if (npc.id in SPIN_BLADES) blades += npc else if (npc.id in GUARDS) guards += npc
        }
    }

    private fun moveBlade(npc: Npc) {
        if (npc.state != EntityState.ACTIVE) return
        if (npc.isWandering) npc.isWandering = false
        val at = LOOP.indexOf(npc.position)
        if (at == -1) {
            npc.move(LOOP.minByOrNull { it.computeLongestDistance(npc.position) }!!)
            return
        }
        npc.walking.addForcedStep(Direction.between(npc.position, LOOP[(at + 1) % LOOP.size]))
    }

    private fun moveGuard(npc: Npc) {
        if (npc.state != EntityState.ACTIVE) return
        if (isDazed(npc)) {
            npc.walking.clear()
            return
        }
        val target = world.locator.findPlayers(npc.position, GUARD_SIGHT) {
            it.denRun?.leaving == false && it.position.isWithinDistance(npc.position, GUARD_SIGHT)
        }.minByOrNull { it.position.computeLongestDistance(npc.position) }
        if (target == null) {
            if (!npc.isWandering) npc.isWandering = true
            return
        }
        npc.isWandering = false
        if (!target.position.isWithinDistance(npc.position, 1)) {
            npc.walking.clear()
            npc.walking.addStep(Direction.between(npc.position, target.position))
        }
    }

    /**
     * Whether flash powder still dazes [npc].
     */
    fun isDazed(npc: Npc) = (dazedUntil[npc] ?: 0L) > world.currentTick

    /**
     * Dazes [npc] for [STUN_TICKS] ticks.
     */
    fun daze(npc: Npc) {
        dazedUntil[npc] = world.currentTick + STUN_TICKS
        npc.walking.clear()
    }

    /**
     * Whether a guard who isn't dazed stands beside [here].
     */
    fun guardCatches(here: Position) =
        guards.any { it.state == EntityState.ACTIVE && !isDazed(it) && it.position.isWithinDistance(here, 1) }

    /**
     * Whether a Spin Blade stands on [here], or crossed a tile the player stepped onto this tick.
     */
    fun bladeTouches(here: Position, walked: List<Position>) =
        blades.any { it.state == EntityState.ACTIVE && (it.position == here || it.position in walked) }
}
