package api.combat.magic

import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.mob.Player

/**
 * An action that tracks an active Tele Block effect on a [Player].
 *
 * Tele Block is processed once per tick and continues until the player's remaining Tele Block duration reaches zero.
 * When the effect expires, the player is notified and this action terminates.
 *
 * @param plr The player affected by Tele Block.
 * @author lare96
 */
class TeleBlockAction(plr: Player) : Action<Player>(plr, ActionType.SOFT, false, 1) {

    override fun run(): Boolean {
        if (mob.combat.magic.decrementTeleBlock() < 1) {
            mob.sendMessage("Your Tele Block has expired.")
            return true
        }
        return false
    }
}