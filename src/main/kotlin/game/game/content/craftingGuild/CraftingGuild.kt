package game.content.craftingGuild

import api.predef.ext.*
import game.obj.doors.DoorPassageAction
import game.obj.doors.DoorType
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.dialogue.Expression
import io.luna.game.model.`object`.GameObject

/**
 * Crafting Guild entrance rules from Lost City's revision 377 crafting_guild script.
 *
 * @author lare96
 */
object CraftingGuild {

    const val DOOR = 2647
    const val MASTER_CRAFTER = 805
    const val BROWN_APRON = 1757
    const val REQUIRED_LEVEL = 40
    private val doorType = DoorType(DOOR, 1532, null, null, null, 3)

    /**
     * Whether the player currently meets the entry rules, without opening dialogue.
     */
    fun canEnter(plr: Player): Boolean =
        plr.skill(Skill.CRAFTING).level >= REQUIRED_LEVEL && plr.equipment.contains(BROWN_APRON)

    /**
     * Checks the current Crafting level and worn apron only when entering the guild.
     */
    internal fun checkAccess(plr: Player, entering: Boolean): Boolean {
        if (!entering) {
            return true
        }
        if (plr.skill(Skill.CRAFTING).level < REQUIRED_LEVEL) {
            plr.newDialogue().npc(MASTER_CRAFTER, 588,
                "Sorry only experienced craftsmen are allowed in here.",
                "You must be level 40 or above to enter.").open()
            return false
        }
        if (!plr.equipment.contains(BROWN_APRON)) {
            val dialogue = plr.newDialogue().npc(MASTER_CRAFTER, 588,
                "Where's your brown apron? You can't come in here",
                "unless you're wearing one.")
            if (!plr.inventory.contains(BROWN_APRON)) {
                dialogue.player(Expression.NEUTRAL, "Err... I haven't got one.")
            }
            dialogue.open()
            return false
        }
        return true
    }

    /**
     * Aligns the player with the guild door before checking access and crossing.
     */
    fun openDoor(plr: Player, door: GameObject) {
        if (plr.actions.first(Crossing::class.java) == null) {
            plr.submitAction(Crossing(plr, door))
        }
    }

    /**
     * Uses the shared conditional passage to prevent unchecked followers.
     *
     * @author lare96
     */
    private class Crossing(plr: Player, door: GameObject) : DoorPassageAction(plr, door, doorType) {
        override fun checkAccess(entering: Boolean): Boolean = CraftingGuild.checkAccess(mob, entering)

        override fun onPassage(entering: Boolean) {
            if (entering) {
                mob.newDialogue().npc(MASTER_CRAFTER, 588,
                    "Welcome to the Guild of Master Craftsmen.").open()
            }
        }
    }
}
