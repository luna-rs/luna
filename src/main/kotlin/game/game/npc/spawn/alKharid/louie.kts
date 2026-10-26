package game.npc.spawn.alKharid

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.dialogue.Expression

/**
 * The [Npc] id for Louie Legs.
 */
val LOUIE_LEGS_ID = 542

/**
 * The name of the shop.
 */
val SHOP_NAME = "Louie's Armoured Legs Bazaar."

/**
 * Louie's Armoured Legs Bazaar in Al-Kharid.
 */
ShopHandler.create(SHOP_NAME) {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Bronze platelegs" x 50
        "Iron platelegs" x 30
        "Steel platelegs" x 20
        "Black platelegs" x 10
        "Mithril platelegs" x 10
        "Adamant platelegs" x 10
    }

    open {
        npc2 += LOUIE_LEGS_ID
    }
}

/*
 * Handles Louie Legs' first npc option dialogue.
 *
 * The player can either open the platelegs shop or decline.
 */
npc1(LOUIE_LEGS_ID) {
    plr.newDialogue()
        .npc(targetNpc.id, "Hey, wanna buy some armour?")
        .options(
            "What have you got?",
            {
                plr.newDialogue()
                    .player("What have you got?")
                    .npc(targetNpc.id, Expression.HAPPY, "I provide items to help you keep your legs!")
                    .then { it.overlays.open(ShopInterface(world, SHOP_NAME)) }
                    .open()
            },
            "No thank you.",
            {
                plr.newDialogue()
                    .player("No thank you.")
                    .open()
            }
        ).open()
}
