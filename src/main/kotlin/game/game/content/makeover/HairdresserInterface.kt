package game.content.makeover

import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.Varp

/**
 * One of the hairdresser's brochures, where the player picks a style and a colour. The client lights up the picks from
 * varps [STYLE_VARP] and [COLOUR_VARP] by itself, so the window keeps its own copy of them.
 *
 * @param style The brochure.
 * @param hairdresser The hairdresser who opened it.
 * @author TheLining
 */
class HairdresserInterface(val style: HairStyle, val hairdresser: Npc) : StandardInterface(style.interfaceId) {

    companion object {

        /**
         * The varp that lights up the picked style: its button number from 1, or 0 for none.
         */
        const val STYLE_VARP = 261

        /**
         * The varp that lights up the picked colour: the colour plus one, or 0 for none.
         */
        const val COLOUR_VARP = 262
    }

    /**
     * The identity kit of the picked style, or `null` if none has been picked.
     */
    var kit: Int? = null

    /**
     * The picked hair colour, or `null` if none has been picked.
     */
    var colour: Int? = null

    override fun onOpen(player: Player) {
        player.sendVarp(Varp(STYLE_VARP, 0))
        player.sendVarp(Varp(COLOUR_VARP, 0))
    }
}
