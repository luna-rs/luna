package game.content.fightPit

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.content.fightCave.FightCave
import game.npc.spawn.tzhaar.TzHaarNames
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.area.Area
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.SkullIcon
import java.util.ArrayDeque

/**
 * The Fight Pit's rounds. Two or more players waiting start one; they're moved into the pit, wait for
 * TzHaar-Mej-Kah's signal and fight until one is left standing. That player is the champion, stays in the pit for
 * the next round, and is paid in Tokkul on leaving through the fire wall.
 *
 * @author TheLining
 */
object FightPitRound {

    /**
     * Ticks from enough players waiting to a round starting.
     */
    const val COUNTDOWN_TICKS = 17

    /**
     * Ticks from the players arriving in the pit to Mej-Kah's signal.
     */
    const val SIGNAL_TICKS = 25

    /**
     * How far apart, in tiles, players arrive in the pit where there's room.
     */
    private const val SPREAD = 6

    /**
     * Where a round is.
     */
    enum class Phase {

        /**
         * No round, and too few players to start one.
         */
        IDLE,

        /**
         * Enough players, counting down to the start.
         */
        COUNTDOWN,

        /**
         * In the pit, waiting for the signal.
         */
        SIGNAL,

        /**
         * Fighting.
         */
        FIGHT
    }

    /**
     * Tokkul the champion has won and not yet been paid, kept through logouts and restarts.
     */
    var Player.fightPitTokkul by Attr.int().persist("fight_pit_tokkul")

    /**
     * The round's phase.
     */
    var phase = Phase.IDLE
        private set

    /**
     * Ticks left in [Phase.COUNTDOWN] and [Phase.SIGNAL]; ticks since the signal in [Phase.FIGHT].
     */
    var clock = 0
        private set

    /**
     * The champion, while they're in the pit.
     */
    var champion: Player? = null
        private set

    /**
     * Every player with a [FightPitController].
     */
    private val members = LinkedHashSet<Player>()

    /**
     * The round's players still standing.
     */
    private val fighters = LinkedHashSet<Player>()

    /**
     * The combat levels of the round's players who died.
     */
    private val fallen = ArrayList<Int>()

    /**
     * The pit tiles a player can stand on, and the waiting room's.
     */
    private var pitTiles: List<Position> = emptyList()
    private var waitingTiles: List<Position> = emptyList()

    /**
     * Finds the tiles players can stand on and starts the rounds' clock.
     */
    fun launch() {
        pitTiles = reachable(FightPit.PIT_EXIT, FightPit.PIT)
        waitingTiles = reachable(FightPit.SOUTH_INSIDE, FightPit.WAITING_ROOM)
        world.schedule(1) { tick() }
    }

    /**
     * Whether [plr] is standing in this round.
     */
    fun fighting(plr: Player) = plr in fighters

    /**
     * Whether [plr] and [other] may fight each other now.
     */
    fun canFight(plr: Player, other: Player) = phase == Phase.FIGHT && plr in fighters && other in fighters &&
        FightPit.PIT.contains(plr.position) && FightPit.PIT.contains(other.position)

    /**
     * The round's players still standing, in the pit.
     */
    fun standing(): List<Player> = fighters.filter { it.isAlive && FightPit.PIT.contains(it.position) }

    /**
     * Notes a new [FightPitController].
     */
    fun joined(plr: Player) {
        members += plr
    }

    /**
     * Forgets a player whose [FightPitController] is gone.
     */
    fun departed(plr: Player) {
        members -= plr
        walkedOut(plr)
    }

    /**
     * Takes [plr] out of the round without counting them as defeated: they walked out or logged out. A champion is
     * paid now, or on their next login if they logged out.
     */
    fun walkedOut(plr: Player, pay: Boolean = true) {
        fighters -= plr
        plr.contextMenu.hide(OPTION_ATTACK)
        if (plr === champion) {
            champion = null
            if (plr.skullIcon == SkullIcon.RED) plr.skullIcon = SkullIcon.NONE
            if (pay) payOut(plr)
        }
    }

    /**
     * Counts [plr], just fallen, as defeated. A fallen champion loses their unpaid Tokkul.
     */
    fun died(plr: Player) {
        plr.contextMenu.hide(OPTION_ATTACK)
        if (fighters.remove(plr)) fallen += plr.combatLevel
        if (plr === champion) {
            champion = null
            plr.fightPitTokkul = 0
        }
    }

    /**
     * Puts a defeated player back in the waiting room.
     */
    fun eject(plr: Player) {
        plr.move(waitingTiles.randomOrNull() ?: FightPit.SOUTH_INSIDE)
    }

    /**
     * Pays [plr] the Tokkul they've won as champion.
     */
    fun payOut(plr: Player) {
        val amount = plr.fightPitTokkul
        if (amount > 0) {
            plr.fightPitTokkul = 0
            FightCave.give(plr, FightCave.TOKKUL, amount)
        }
    }

    /**
     * Runs the rounds, once a tick.
     */
    private fun tick() {
        members.removeIf { it.state != EntityState.ACTIVE || it.controllers.primary !is FightPitController }
        fighters.retainAll(members)
        // A player who drops now has fallen before their death hook runs, and before the round can end without them.
        fighters.filter { it.health == 0 }.forEach(::died)
        when (phase) {
            Phase.IDLE, Phase.COUNTDOWN -> {
                val ready = waiting().size + (if (champion != null) 1 else 0)
                if (ready < 2) {
                    phase = Phase.IDLE
                } else if (phase == Phase.IDLE) {
                    phase = Phase.COUNTDOWN
                    clock = COUNTDOWN_TICKS
                } else if (--clock == 0) {
                    start()
                }
            }
            Phase.SIGNAL -> if (!over() && --clock == 0) signal()
            Phase.FIGHT -> if (!over()) clock++
        }
        members.forEach { (it.controllers.primary as? FightPitController)?.refresh() }
    }

    /**
     * The players in the waiting room who'd be moved into the pit now.
     */
    private fun waiting() = members.filter {
        it.isAlive && !it.isLocked && FightPit.WAITING_ROOM.contains(it.position)
    }

    /**
     * Moves everyone waiting into the pit, apart from each other, to join the champion if there is one.
     */
    private fun start() {
        val entrants = waiting()
        fallen.clear()
        fighters.clear()
        champion?.let { fighters += it }
        val tiles = spread(entrants.size)
        for ((index, plr) in entrants.withIndex()) {
            plr.overlays.closeWindows()
            plr.walking.clear()
            plr.move(tiles[index])
            fighters += plr
        }
        phase = Phase.SIGNAL
        clock = SIGNAL_TICKS
    }

    /**
     * TzHaar-Mej-Kah's signal: the fight is on.
     */
    private fun signal() {
        phase = Phase.FIGHT
        clock = 0
        for (plr in fighters) {
            plr.contextMenu.show(OPTION_ATTACK)
            plr.sendMessage("Fight!")
        }
        world.npcs.firstOrNull { it.id == FightPit.MEJ_KAH }?.speak("Fight!")
    }

    /**
     * Ends the round if one player or none is left standing, and returns whether it did.
     */
    private fun over(): Boolean {
        val standing = standing()
        if (standing.size > 1) return false
        end(standing.firstOrNull())
        return true
    }

    /**
     * Ends the round. A [winner] becomes champion with the red skull, and is owed Tokkul for everyone who fell.
     */
    private fun end(winner: Player?) {
        phase = Phase.IDLE
        fighters.forEach { it.contextMenu.hide(OPTION_ATTACK) }
        fighters.clear()
        if (winner != null) {
            winner.fightPitTokkul += fallen.sum()
            winner.skullIcon = SkullIcon.RED
            champion = winner
            FightPit.crown(TzHaarNames.name(winner))
        }
        fallen.clear()
    }

    /**
     * Picks [count] pit tiles, each at least [SPREAD] tiles from the others where there's room.
     */
    private fun spread(count: Int): List<Position> {
        val taken = ArrayList<Position>()
        champion?.let { taken += it.position }
        val picked = ArrayList<Position>()
        val shuffled = pitTiles.shuffled(rand())
        for (tile in shuffled) {
            if (picked.size == count) break
            if (taken.none { it.isWithinDistance(tile, SPREAD - 1) }) {
                picked += tile
                taken += tile
            }
        }
        while (picked.size < count) picked += shuffled[picked.size % shuffled.size]
        return picked
    }

    /**
     * The tiles of [area] a player can walk to from [start].
     */
    private fun reachable(start: Position, area: Area): List<Position> {
        val seen = hashSetOf(start)
        val queue = ArrayDeque(listOf(start))
        val directions = listOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)
        while (queue.isNotEmpty()) {
            val tile = queue.poll()
            for (direction in directions) {
                val next = tile.translate(1, direction)
                if (next !in seen && area.contains(next) &&
                    world.collisionManager.traversable(tile, EntityType.PLAYER, direction)) {
                    seen += next
                    queue += next
                }
            }
        }
        return seen.toList()
    }
}
