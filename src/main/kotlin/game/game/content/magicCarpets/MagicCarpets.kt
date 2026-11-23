package game.content.magicCarpets

import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder

/**
 * Ali Morrisane's magic carpets, which fly players between stations in the Kharidian desert.
 *
 * @author TheLining
 */
object MagicCarpets {

    /**
     * What a flight costs.
     */
    const val FARE = 200

    /**
     * What a flight costs for a player wearing an activated ring of Charos.
     */
    const val CHAROS_FARE = 100

    /**
     * The activated ring of Charos.
     */
    const val RING_OF_CHAROS_A = 6465

    /**
     * Coins, which pay the fare.
     */
    private const val COINS = 995

    /**
     * The most characters that fit on a line of a chat box.
     */
    private const val LINE_WIDTH = 50

    /**
     * The most lines a chat box holds.
     */
    private const val BOX_LINES = 4

    /**
     * Returns what a flight costs [plr].
     */
    fun fare(plr: Player): Int {
        // TODO Rogue Trader: once Ali Morrisane's discount is unlocked, a flight costs 100 coins, or 75 with the ring.
        return if (plr.equipment.get(Equipment.RING)?.id == RING_OF_CHAROS_A) CHAROS_FARE else FARE
    }

    /**
     * Takes the fare from [plr] if they can fly, or says why they can't. Returns `true` if they paid.
     */
    fun pay(plr: Player): Boolean {
        if (plr.equipment.get(Equipment.WEAPON) != null) {
            plr.sendMessage("You must unwield your weapon before you can fly on a magic carpet.")
            return false
        }
        val fare = fare(plr)
        if (plr.inventory.computeAmountForId(COINS) < fare) {
            plr.sendMessage("You need $fare coins to fly on a magic carpet.")
            return false
        }
        return plr.inventory.remove(Item(COINS, fare))
    }

    /**
     * Flies [plr] along [route], if they can pay for it.
     */
    fun fly(plr: Player, route: CarpetRoute) = plr.submitAction(CarpetRide(plr, route))

    /**
     * Has the merchant with head [face] say [text], over as many chat boxes as it takes.
     */
    fun DialogueQueueBuilder.merchant(face: Int, text: String): DialogueQueueBuilder {
        boxes(text).forEach { npc(face, *it) }
        return this
    }

    /**
     * Has the player say [text], over as many chat boxes as it takes.
     */
    fun DialogueQueueBuilder.say(text: String): DialogueQueueBuilder {
        boxes(text).forEach { player(*it) }
        return this
    }

    /**
     * Breaks [text] into chat boxes of lines that fit.
     */
    private fun boxes(text: String): List<Array<String>> {
        val lines = ArrayList<String>()
        var line = StringBuilder()
        for (word in text.split(' ')) {
            if (line.isNotEmpty() && line.length + 1 + word.length > LINE_WIDTH) {
                lines += line.toString()
                line = StringBuilder()
            }
            if (line.isNotEmpty()) line.append(' ')
            line.append(word)
        }
        lines += line.toString()
        return lines.chunked(BOX_LINES) { it.toTypedArray() }
    }
}
