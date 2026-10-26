package game.npc.spawn.rellekka

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.Player

/**
 * The [Npc] id for Sigmund The Merchant.
 */
val SIGMUND_ID = 1282

/**
 * The name of the shop.
 */
val SHOP_NAME = "Sigmund the Merchant"

/**
 * Returns whether [plr] is a Fremennik, which Sigmund requires before he will trade with them.
 */
fun isFremennik(plr: Player): Boolean {
    // TODO The Fremennik Trials: only players who have finished the quest are Fremenniks.
    return true
}

/*
 * Registers Sigmund's shop in Rellekka. It is opened by the npc options below, so outerlanders can be turned away.
 */
ShopHandler.create(SHOP_NAME) {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Tinderbox" x 2
        "Rope" x 10
        "Pestle and mortar" x 3
        "Pot" x 3
        "Cooked meat" x 2
        "Bread" x 10
        "Spade" x 10
        "Candle" x 10
        "Chisel" x 10
        "Hammer" x 10
        "Vial" x 10
        "Vial of water" x 10
        "Bucket" x 10
        "Bucket of water" x 10
        "Egg" x 10
        "Potato" x 10
        "Cabbage" x 10
    }
}

/*
 * Handles Sigmund's first npc option dialogue.
 *
 * Sigmund only speaks to Fremenniks, and turns outerlanders away.
 */
npc1(SIGMUND_ID) {
    if (isFremennik(plr)) {
        // TODO The Fremennik Trials: address the player by their Fremennik name.
        val name = plr.username
        val sibling = if (plr.appearance.isFemale) "sister" else "brother"
        plr.newDialogue()
            .player("Hello there!")
            .npc(targetNpc.id, "Greetings again $sibling $name! What can I do for you this day?")
            .options(
                "Can I see your wares?",
                {
                    plr.newDialogue()
                        .player("Can I see your wares?")
                        .npc(targetNpc.id, "Certainly, $name.")
                        .then { it.overlays.open(ShopInterface(world, SHOP_NAME)) }
                        .open()
                },
                "Nothing thanks.",
                {
                    plr.newDialogue()
                        .player("Nothing thanks.")
                        .npc(
                            targetNpc.id,
                            "Well, feel free to stop by anytime that you wish $name.",
                            "You are always welcome here!"
                        )
                        .open()
                }
            ).open()
    } else {
        plr.newDialogue()
            .player("Hiya.")
            .npc(
                targetNpc.id,
                "Hello outerlander. By the laws of our tribe, I am afraid",
                "I may not speak to you without the express permission",
                "of the chieftain."
            )
            .open()
    }
}

/*
 * Handles Sigmund's "Trade" option.
 */
npc2(SIGMUND_ID) {
    if (isFremennik(plr)) {
        plr.overlays.open(ShopInterface(world, SHOP_NAME))
    } else {
        plr.newDialogue()
            .text("Only Fremenniks may trade with this merchant.")
            .open()
    }
}
