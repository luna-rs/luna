package game.npc.spawn.apeAtoll

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.dialogue.DialogueUtils

/**
 * Ifaba's General Store on Ape Atoll.
 */
ShopHandler.create("Ifaba's General Store") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Pot" x 3 every 100
        "Jug" x 2 every 100
    }

    open {
        npc2 += 1436
    }
}

/**
 * The amulet of monkeyspeak, which lets players understand the monkeys of Ape Atoll.
 */
val AMULET_OF_MONKEYSPEAK = 4021

/**
 * The noises a monkey makes, and how many words each one counts as.
 */
val MONKEY_NOISES = listOf("Ah Ah!" to 2, "Ah Uh Ah!" to 3, "Ah!" to 1, "Ook Ah Ook!" to 3, "Ook Ah Uh!" to 3,
                           "Ook Ook!" to 2, "Ook!" to 1, "Ook." to 1)

/**
 * Builds at least eight words of monkey noises.
 */
fun monkeySpeak(): String {
    val noises = ArrayList<String>()
    var words = 0
    while (words < 8) {
        val (noise, count) = MONKEY_NOISES.random()
        noises += noise
        words += count
    }
    return noises.joinToString(" ")
}

// TODO Once monkey greegrees and the Ape Atoll guards are added, call the guards on players who aren't wearing a
//  greegree instead of trading with them.
npc1(1436) {
    if (plr.equipment[Equipment.AMULET]?.id != AMULET_OF_MONKEYSPEAK) {
        plr.newDialogue()
            .npc(1436, *DialogueUtils.splitChatLines(monkeySpeak()))
            .text("You cannot understand what he is saying.")
            .open()
        return@npc1
    }
    plr.newDialogue()
        .npc(1436, "Would you like to buy or sell anything?")
        .options("Yes, please.", {
            it.newDialogue()
                .player("Yes, please.")
                .then { GeneralStore.open(it, "Ifaba's General Store") }
                .open()
        }, "No, thanks.", {
            it.newDialogue().player("No, thanks.").open()
        })
        .open()
}
