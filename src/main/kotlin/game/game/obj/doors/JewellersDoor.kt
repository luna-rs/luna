package game.obj.doors

import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * The jeweller's door in Port Sarim from the Merlins Crystal quest. It is not a toggle door:
 * Clicking it steps the player through, and the door closes behind them after a few ticks.
 *
 * @author Hydrozoa
 */
object JewellersDoor {

    const val DOOR = 59
    const val OPEN = 1535
    private val doorType = DoorType(DOOR, OPEN, null, null, null, 3)

    /**
     * Aligns the player with the door before crossing.
     */
    fun openDoor(plr: Player, door: GameObject) {
        // todo when Merlin Crystal is added
        // Spawn the lake beggar one tile north of the door here while the quest is started,
        // but Excalibur has not been rewarded yet, if the beggar is not already there.
        // Also open the beggar's dialogue if the player has not spoken to them yet.

        if (plr.actions.first(Crossing::class.java) == null) {
            plr.submitAction(Crossing(plr, door))
        }
    }

    /**
     * Uses the shared passage so that the door closes behind the player.
     *
     * @author Hydrozoa
     */
    private class Crossing(plr: Player, door: GameObject) : DoorPassageAction(plr, door, doorType) {
        override fun checkAccess(entering: Boolean): Boolean = true
    }
}
