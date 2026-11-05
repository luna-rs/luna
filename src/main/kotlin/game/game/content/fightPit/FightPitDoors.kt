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
                cross(plr, door, FightPit.PIT_EXIT, FightPit.SOUTH_INSIDE) { FightPit.enter(it) }
            }
        }
    }

    /**
     * Walks [plr] to [from] if they aren't on it, then over [door] to [to], running [arrived] once they're there.
     */
    private fun cross(plr: Player, door: GameObject, from: Position, to: Position, arrived: (Player) -> Unit) {
        val start = { if (plr.position == from) plr.submitAction(Crossing(plr, door, from, to, arrived)) }
        when {
            plr.position == from -> start()
            plr.position.computeLongestDistance(from) <= AgilityApproach.RADIUS ->
                plr.submitAction(AgilityApproach(plr, from, start))
        }
    }

    /**
     * The two-tick walk over a vent. A death on the way stops it.
     */
    private class Crossing(plr: Player, door: GameObject, from: Position, to: Position, arrived: (Player) -> Unit) :
        AgilityAction(plr, listOf(AgilityStep(to, 2, animation = WALK)), null,
                      valid = { plr.isAlive && plr.position == from && door.state == EntityState.ACTIVE }),
        DeathInterruptible {
        init {
            onSuccess = { arrived(plr) }
            onCancel = {
                plr.exactMove(ExactMovement(plr.lastRegion, plr.position, plr.position, 0, 1,
                                            Direction.between(from, to)))
            }
        }
    }
}
