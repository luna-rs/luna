package game.content.fightPit

import game.skill.agility.AgilityAction
import game.skill.agility.AgilityApproach
import game.skill.agility.AgilityStep
import io.luna.game.action.DeathInterruptible
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.ExactMovement
import io.luna.game.model.`object`.GameObject
import java.util.WeakHashMap

/**
 * The Fight Pit's two hot vent doors. Passing one walks the player over the vent to the tile on its other side.
 *
 * @author TheLining
 */
object FightPitDoors {

    /**
     * The walk animation played over the vent.
     */
    private const val WALK = 819
    private val traversals = WeakHashMap<Player, Traversal>()

    /**
     * A visit's owner token and its current crossing, without retaining a player through the value.
     *
     * @author lare96
     */
    private class Traversal(val owner: Any?, var step: Any? = null)

    /**
     * Gives a visitor ownership of its future door actions; replacing the owner invalidates older actions.
     */
    fun ownTraversals(plr: Player, owner: Any) { traversals[plr] = Traversal(owner) }

    /**
     * Invalidates a visitor's pending approach or crossing without admitting them after cancellation.
     */
    fun cancelTraversal(plr: Player, owner: Any) {
        if (traversals[plr]?.owner === owner) traversals.remove(plr)
    }

    /**
     * Passes [door] from whichever side [plr] is on.
     */
    fun pass(plr: Player, door: GameObject) {
        when (door.id) {
            FightPit.NORTH_DOOR -> if (plr.y > door.y) {
                cross(plr, door, FightPit.OUTSIDE, FightPit.NORTH_INSIDE) { FightPit.enter(it) }
            } else {
                cross(plr, door, FightPit.NORTH_INSIDE, FightPit.OUTSIDE) { }
            }
            FightPit.SOUTH_DOOR -> if (plr.y > door.y) {
                plr.sendMessage("You must wait for the next fight to start.")
            } else {
                cross(plr, door, FightPit.PIT_EXIT, FightPit.SOUTH_INSIDE) {
                    FightPit.enter(it)
                    FightPitRound.walkedOut(it)
                }
            }
        }
    }

    /**
     * Walks [plr] to [from] if they aren't on it, then over [door] to [to], running [arrived] once they're there.
     */
    private fun cross(plr: Player, door: GameObject, from: Position, to: Position, arrived: (Player) -> Unit) {
        val visit = traversals.getOrPut(plr) { Traversal(null) }
        val traversal = Any()
        visit.step = traversal
        val current = { traversals[plr] === visit && visit.step === traversal }
        val finished: () -> Unit = {
            if (current()) {
                visit.step = null
                if (visit.owner == null) traversals.remove(plr)
            }
        }
        val start = {
            if (current() && plr.position == from) plr.submitAction(Crossing(plr, door, from, to, arrived, current, finished))
        }
        when {
            plr.position == from -> start()
            plr.position.computeLongestDistance(from) <= AgilityApproach.RADIUS ->
                plr.submitAction(AgilityApproach(plr, from, start))
        }
    }

    /**
     * The two-tick walk over a vent. A death on the way stops it.
     */
    private class Crossing(plr: Player, door: GameObject, from: Position, to: Position, arrived: (Player) -> Unit,
                           current: () -> Boolean, finished: () -> Unit) :
        AgilityAction(plr, listOf(AgilityStep(to, 2, animation = WALK)), null,
                      valid = { current() && plr.isAlive && plr.position == from && door.state == EntityState.ACTIVE }),
        DeathInterruptible {
        init {
            onSuccess = { finished(); arrived(plr) }
            onCancel = {
                finished()
                plr.exactMove(ExactMovement(plr.lastRegion, plr.position, plr.position, 0, 1,
                                            Direction.between(from, to)))
            }
        }
    }
}
