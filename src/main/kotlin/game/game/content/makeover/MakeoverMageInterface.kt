package game.content.makeover

import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.PlayerAppearance
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.Varp

/**
 * The make-over mage's window, where the player picks a gender and a skin colour. The client lights up the picked
 * portrait and colour from varps [GENDER_VARP] and [SKIN_VARP] by itself, so the window keeps its own copy of the picks.
 *
 * @param mage The mage who opened the window.
 * @author TheLining
 */
class MakeoverMageInterface(val mage: Npc) : StandardInterface(5454) {

    companion object {

        /**
         * The varp that lights up the picked portrait: 1 for male, 2 for female.
         */
        const val GENDER_VARP = 261

        /**
         * The varp that lights up the picked skin colour: the colour plus one, or 0 for none.
         */
        const val SKIN_VARP = 262
    }

    /**
     * The picked gender.
     */
    var gender = PlayerAppearance.GENDER_MALE

    /**
     * The picked skin colour, or `null` if none has been picked.
     */
    var skin: Int? = null

    override fun onOpen(player: Player) {
        gender = player.appearance.get(PlayerAppearance.GENDER)
        player.sendVarp(Varp(GENDER_VARP, gender + 1))
        player.sendVarp(Varp(SKIN_VARP, 0))
    }
}
