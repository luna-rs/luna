package game.content.fishingGuild

import api.predef.ext.*
import game.obj.doors.DoorPassageAction
import game.obj.doors.DoorType
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.`object`.GameObject

/**
 * Fishing Guild admission through its native door. Only entry checks the current Fishing level.
 *
 * @author lare96
 */
object FishingGuild {

    const val DOOR = 2025
    const val MASTER_FISHER = 308
    const val REQUIRED_LEVEL = 68
    private val doorType = DoorType(DOOR, 1535, null, null, null, 3)

    /**
     * Whether the current Fishing level, including boosts and drains, permits entry.
     */
    fun canEnter(plr: Player): Boolean = plr.skill(Skill.FISHING).level >= REQUIRED_LEVEL

    /**
     * Checks boosts and drains on entry, while allowing a drained player to leave.
     */
    internal fun checkAccess(plr: Player, entering: Boolean): Boolean {
        if (!entering || canEnter(plr)) {
            return true
        }
        plr.newDialogue().npc(MASTER_FISHER,
            "Hello, only the top fishers are allowed in here,",
            "you need a Fishing level of 68 to enter.").open()
        return false
    }

    /**
     * Aligns with the entrance and checks admission immediately before crossing.
     */
    fun openDoor(plr: Player, door: GameObject) {
        if (plr.actions.first(Crossing::class.java) == null) {
            plr.submitAction(Crossing(plr, door))
        }
    }

    /**
     * Uses a private passage so other players cannot follow through without their own level check.
     * The south-facing entrance is approached from the side opposite the door's own tile.
     *
     * @author lare96
     */
    private class Crossing(plr: Player, door: GameObject) : DoorPassageAction(plr, door, doorType) {
        override fun checkAccess(entering: Boolean): Boolean = FishingGuild.checkAccess(mob, !entering)
    }
}
