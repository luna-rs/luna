package game.npc.spawn.rellekka

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueUtils
import io.luna.game.model.mob.dialogue.Expression

/**
 * Sigmund The Merchant's general store in Rellekka.
 */
ShopHandler.create("Sigmund The Merchant") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Tinderbox" x 2 every 500
        "Rope" x 10 every 200
        "Pestle and mortar" x 3 every 500
        "Pot" x 3 every 500
        "Cooked meat" x 2 every 500
        "Bread" x 10 every 500
        "Spade" x 10 every 500
        "Candle" x 10 every 500
        "Chisel" x 10 every 500
        "Hammer" x 10 every 500
        "Vial" x 10 every 500
        "Vial of water" x 10 every 200
        "Bucket" x 10 every 500
        "Bucket of water" x 10 every 200
        "Egg" x 10 every 200
        "Potato" x 10 every 200
        "Cabbage" x 10 every 200
    }

    open {
        npc2 += 1282
    }
}

/**
 * The first halves of the names given to new Fremennik.
 */
val NAME_STARTS = listOf("Rak", "Bal", "Bar", "Dal", "Dar", "Den", "Dok", "Jar", "Jik", "Lar", "Ral", "Ril", "Sig",
                         "Tal", "Thor", "Ton")

/**
 * The second halves of the names given to new Fremennik.
 */
val NAME_ENDS = listOf("lak", "dar", "dor", "dur", "kal", "kar", "kir", "kur", "lah", "lim", "lor", "rak", "tin", "ton",
                       "tor", "vald")

/**
 * The player's Fremennik name, or an empty string if they haven't been given one yet.
 */
var Player.fremennikName by Attr.string().persist("fremennik_name")

/**
 * Returns [plr]'s Fremennik name, giving them one first if they don't have one yet.
 */
fun nameOf(plr: Player): String {
    if (plr.fremennikName.isEmpty()) {
        plr.fremennikName = NAME_STARTS.random() + NAME_ENDS.random()
    }
    return plr.fremennikName
}

// Sigmund only trades with the Fremennik.
// TODO Once The Fremennik Trials is added, turn away outerlanders and give players their Fremennik name when they
//  finish the quest instead.
npc1(1282) {
    val name = nameOf(plr)
    val kin = if (plr.appearance.isFemale) "sister" else "brother"
    plr.newDialogue()
        .player("Hello there!")
        .npc(1282, Expression.QUIZZICAL,
             *DialogueUtils.splitChatLines("Greetings again $kin $name! What can I do for you this day?"))
        .options("Can I see your wares?", {
            it.newDialogue()
                .player(Expression.QUIZZICAL, "Can I see your wares?")
                .npc(1282, Expression.HAPPY, "Certainly, $name.")
                .then { GeneralStore.open(it, "Sigmund The Merchant") }
                .open()
        }, "Nothin thanks.", { // The option's missing letter is how it was in the 377 era.
            it.newDialogue()
                .player(Expression.HAPPY, "Nothing thanks.")
                .npc(1282, Expression.HAPPY, *DialogueUtils.splitChatLines(
                    "Well, feel free to stop by anytime that you wish $name. You are always welcome here!"))
                .open()
        })
        .open()
}
