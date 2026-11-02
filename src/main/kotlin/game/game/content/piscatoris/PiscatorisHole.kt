package game.content.piscatoris

import game.skill.agility.AgilityApproach
import game.skill.agility.AgilityAction
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.ExactMovement
import io.luna.game.model.`object`.GameObject

/**
 * The paired cliff holes connecting the boat shore to the colony's gate approach.
 *
 * @author lare96
 */
object PiscatorisHole {

    const val ID = 14922
    private const val CRAWL_TICKS = 6
    val SOUTH_OBJECT = Position(2344, 3651)
    val NORTH_OBJECT = Position(2344, 3654)
    val SOUTH = Position(2344, 3650)
    val NORTH = Position(2344, 3655)

    /**
     * Approaches the clicked mouth and crawls through without a level check or experience reward.
     */
    fun enter(plr: Player, hole: GameObject) {
        if (hole.id != ID || plr.isLocked || !canTravel(plr) || plr.z != 0) return
        val start = when (hole.position) {
            SOUTH_OBJECT -> SOUTH
            NORTH_OBJECT -> NORTH
            else -> return
        }
        if (!plr.position.isWithinDistance(start, AgilityApproach.RADIUS)) return
        val destination = if (start == SOUTH) NORTH else SOUTH
        val crawl = { plr.submitAction(Crawl(plr, start, destination)) }
        if (plr.position == start) crawl() else plr.submitAction(AgilityApproach(plr, start) { crawl() })
    }

    /**
     * Keeps a delayed crawl from moving a dead or disconnected player.
     */
    private fun canTravel(plr: Player): Boolean = plr.state == EntityState.ACTIVE && plr.isAlive &&
        plr.actions.getAll(MobDeathAction::class.java).none { !it.isFinished }

    /**
     * Plays the crawling sequence under one lock and lands only if it completes.
     *
     * @author lare96
     */
    private class Crawl(plr: Player, private val start: Position, private val destination: Position) : LockedAction(plr) {
        override fun beforeLock(): Boolean = !mob.isLocked && canTravel(mob) && mob.position == start

        override fun onLock() {
            mob.walking.clear()
            mob.sendMessage("You crawl through the hole.")
            mob.animation(Animation(844))
            mob.exactMove(ExactMovement(mob.lastRegion, start, destination, 0,
                CRAWL_TICKS * AgilityAction.CYCLES_PER_TICK, Direction.between(start, destination)))
        }

        override fun run(): Boolean {
            if (!canTravel(mob)) return true
            if (executions + 1 < CRAWL_TICKS) return false
            mob.move(destination)
            return true
        }

        override fun onUnlock() {
            mob.walking.clear()
            mob.animation(Animation(-1))
        }
    }
}
