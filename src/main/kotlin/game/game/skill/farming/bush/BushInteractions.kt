package game.skill.farming.bush

import game.skill.farming.*
import io.luna.game.model.mob.Player

/**
 * Digs up the fully grown bush in the bush [patch], once its health has been checked, leaving the patch empty.
 */
fun digUpBush(plr: Player, patch: BushPatch) {
    if (!patch.canDigUp()) {
        return
    }
    plr.animation(Farming.DIG_ANIMATION)
    plr.sendMessage("You dig up the bush.")
    patch.clear()
    Farming.commit(plr)
}
