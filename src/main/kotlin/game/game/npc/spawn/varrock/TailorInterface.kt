package game.npc.spawn.varrock

import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.Varp

/**
 * One of Thessalia's make-over catalogues. The client lights up the picked style, arms and colour from varps 261, 262
 * and 263 by itself, so the window keeps its own copy of the picks.
 *
 * @param style The catalogue.
 * @param thessalia The Thessalia who opened it.
 * @author TheLining
 */
class TailorInterface(val style: TailorStyle, val thessalia: Npc) : StandardInterface(style.interfaceId) {

    companion object {

        /**
         * The varps that light up the picked style, arms and colour: each pick's number from 1, or 0 for none.
         */
        val PICK_VARPS = listOf(261, 262, 263)
    }

    /**
     * The identity kit of the picked style, or `null` if none has been picked.
     */
    var kit: Int? = null

    /**
     * The identity kit of the picked arms, or `null` if none has been picked.
     */
    var arms: Int? = null

    /**
     * The picked colour, or `null` if none has been picked.
     */
    var colour: Int? = null

    override fun onOpen(player: Player) {
        PICK_VARPS.forEach { player.sendVarp(Varp(it, 0)) }
    }
}
