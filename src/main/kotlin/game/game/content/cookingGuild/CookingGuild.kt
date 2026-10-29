package game.content.cookingGuild

import api.predef.ext.*
import game.obj.doors.DoorPassageAction
import game.obj.doors.DoorType
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.`object`.GameObject

/**
 * Cooks' Guild entry rules from Lost City's revision 377 cooking_guild script. Current Cooking
 * level and a worn chef's hat are required on entry; leaving never requires either. The cache's
 * south-facing door stores the indoor tile at [DOOR_POSITION] and opens onto [OUTSIDE].
 *
 * @author lare96
 */
object CookingGuild {
    const val DOOR = 2712
    const val HEAD_CHEF = 847
    const val CHEFS_HAT = 1949
    const val REQUIRED_LEVEL = 32

    /**
     * Cache-confirmed doorway and exterior stand used for native entry, before climbing upstairs.
     */
    val DOOR_POSITION = Position(3143, 3444)
    val OUTSIDE = Position(3143, 3443)

    /**
     * The guild's three-floor footprint; the exterior stand is deliberately excluded.
     */
    fun inside(position: Position): Boolean = position.z in 0..2 &&
        position.x in 3138..3146 && position.y in 3444..3453

    /**
     * Checks current access without opening dialogue or treating a carried hat as worn.
     */
    fun canEnter(plr: Player): Boolean = plr.skill(Skill.COOKING).level >= REQUIRED_LEVEL &&
        plr.equipment.contains(CHEFS_HAT)

    /**
     * Reports the source's level/hat refusal only when entering. Checks run again at the crossing,
     * so losing the hat or suffering a level drain during the approach cannot bypass admission.
     */
    internal fun checkAccess(plr: Player, entering: Boolean): Boolean {
        if (!entering) return true
        if (plr.skill(Skill.COOKING).level < REQUIRED_LEVEL) {
            plr.sendMessage("You need a cooking level of 32 to enter the Chef's Guild.")
            val hat = if (plr.equipment.contains(CHEFS_HAT)) "." else " and come back wearing a chef's hat."
            plr.newDialogue().npc(HEAD_CHEF, 588,
                "Sorry. Only the finest chefs are allowed in here.",
                "Get your cooking level up to 32$hat").open()
            return false
        }
        if (!plr.equipment.contains(CHEFS_HAT)) {
            plr.newDialogue().npc(HEAD_CHEF, 588,
                "You can't come in here unless you're wearing",
                "a chef's hat, or something like that.").open()
            return false
        }
        return true
    }

    /**
     * Uses the shared guarded passage, including its invisible barrier against unchecked followers.
     */
    fun openDoor(plr: Player, door: GameObject) {
        if (plr.actions.first(Crossing::class.java) == null) plr.submitAction(Crossing(plr, door))
    }

    /**
     * The source check_axis result means inside for this south-facing door. The shared passage's
     * forward flag therefore describes leaving, and must be inverted for the admission check.
     *
     * @author lare96
     */
    private class Crossing(plr: Player, door: GameObject) :
        DoorPassageAction(plr, door, DoorType(DOOR, 1535, null, null, null, 3)) {
        override fun checkAccess(entering: Boolean): Boolean = CookingGuild.checkAccess(mob, !entering)
    }
}
