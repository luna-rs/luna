package game.content.blastFurnace

import game.obj.doors.DoorPassageAction
import game.obj.doors.DoorType
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.`object`.GameObject

/**
 * Admits level 60 smiths to the Blast Furnace anvils using Lost City's conditional-door passage.
 * The 377 reference defines smithing_guild_door (9141) and its open model (9142), but has no gate script;
 * admission is reconstructed from the documented anvil requirement. Exit remains free after a skill drain.
 *
 * @author lare96
 */
object BlastFurnaceAnvilGate {

    const val CLOSED = 9141
    private val doorType = DoorType(CLOSED, 9142, null, null, null, 3)

    /**
     * Checks the current Smithing level on entry, following Lost City's skill-gated door checks.
     * Furnace payments do not grant access to the anvils.
     */
    internal fun checkAccess(player: Player, entering: Boolean): Boolean {
        if (!entering || player.skill(Skill.SMITHING).level >= 60) {
            return true
        }
        player.sendMessage("You need a Smithing level of 60 to access these anvils.")
        return false
    }

    /**
     * Aligns with the gate before checking admission and crossing; walking away cancels the passage.
     */
    fun open(player: Player, gate: GameObject) {
        if (player.actions.first(Crossing::class.java) == null) {
            player.submitAction(Crossing(player, gate))
        }
    }

    /**
     * Uses a temporary invisible wall so other players cannot bypass their own admission check.
     *
     * @author lare96
     */
    private class Crossing(player: Player, gate: GameObject) : DoorPassageAction(player, gate, doorType) {
        override fun checkAccess(entering: Boolean): Boolean = BlastFurnaceAnvilGate.checkAccess(mob, entering)
    }
}
