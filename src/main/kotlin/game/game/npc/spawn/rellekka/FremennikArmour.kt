package game.npc.spawn.rellekka

import api.predef.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption
import io.luna.game.model.mob.dialogue.Expression

/**
 * A piece of Fremennik armour, made from dagannoth hides, one part from Waterbirth Island and coins.
 *
 * @author lare96
 */
class FremennikArmourPiece(val product: Int, val hides: Int, val part: Int, val coins: Int) {
    fun optionText(): String = "${itemName(product)} (${"%,d".format(coins)} coins)"
}

/**
 * The Rellekka craftsmen who turn Waterbirth Island drops into Fremennik armour for Fremenniks.
 */
enum class FremennikArmour(val npc: Int, val armour: String, val pieces: List<FremennikArmourPiece>) {
    ROCK_SHELL(npc = 1303,
               armour = "rock-shell",
               pieces = listOf(FremennikArmourPiece(product = 6128, hides = 1, part = 6157, coins = 5000),
                               FremennikArmourPiece(product = 6129, hides = 3, part = 6159, coins = 10_000),
                               FremennikArmourPiece(product = 6130, hides = 2, part = 6161, coins = 7500))),
    SPINED(npc = 1281,
           armour = "spined",
           pieces = listOf(FremennikArmourPiece(product = 6131, hides = 1, part = 6169, coins = 5000),
                           FremennikArmourPiece(product = 6133, hides = 3, part = 6171, coins = 10_000),
                           FremennikArmourPiece(product = 6135, hides = 2, part = 6173, coins = 7500))),
    SKELETAL(npc = 1288,
             armour = "skeletal",
             pieces = listOf(FremennikArmourPiece(product = 6137, hides = 1, part = 6163, coins = 5000),
                             FremennikArmourPiece(product = 6139, hides = 3, part = 6165, coins = 10_000),
                             FremennikArmourPiece(product = 6141, hides = 2, part = 6167, coins = 7500)));

    companion object {

        /**
         * The dagannoth hide every piece needs.
         */
        const val DAGANNOTH_HIDE = 6155

        /**
         * The coins every piece costs.
         */
        const val COINS = 995

        /**
         * Asks [plr] which piece of [maker]'s armour they want made.
         */
        fun openMenu(plr: Player, maker: FremennikArmour) {
            if (!Fremennik.isFremennik(plr)) {
                plr.newDialogue()
                    .npc(maker.npc, "I only make armour for Fremenniks.")
                    .open()
                return
            }
            val options = maker.pieces.map { piece ->
                DialogueOption(piece.optionText()) { make(it, maker, piece) }
            } + DialogueOption("Nothing, thanks.") { }
            plr.newDialogue()
                .npc(maker.npc, Expression.HAPPY, "I can make ${maker.armour} armour from dagannoth",
                     "hides and the right parts, for a price.")
                .options(options)
                .open()
        }

        /**
         * Makes [piece] for [plr] if they carry its hides, part and coins.
         */
        private fun make(plr: Player, maker: FremennikArmour, piece: FremennikArmourPiece) {
            if (!Fremennik.isFremennik(plr)) return
            val inventory = plr.inventory
            if (inventory.computeAmountForId(DAGANNOTH_HIDE) < piece.hides || !inventory.contains(piece.part) ||
                inventory.computeAmountForId(COINS) < piece.coins) {
                val hides = if (piece.hides == 1) "a dagannoth hide" else "${piece.hides} dagannoth hides"
                plr.newDialogue()
                    .npc(maker.npc, "For the ${itemName(piece.product).lowercase()} I need $hides,",
                         "${addArticle(itemName(piece.part).lowercase())} and ${"%,d".format(piece.coins)} coins.")
                    .open()
                return
            }
            inventory.remove(Item(COINS, piece.coins))
            inventory.remove(Item(DAGANNOTH_HIDE, piece.hides))
            inventory.remove(Item(piece.part))
            inventory.add(Item(piece.product))
            plr.newDialogue()
                .npc(maker.npc, Expression.HAPPY, "There you go.")
                .open()
        }
    }
}
