package game.npc.spawn.alKharid

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.dialogue.Expression

/**
 * The [Npc] id for Zeke.
 */
val ZEKE_ID = 541

/**
 * The name of the shop.
 */
val SHOP_NAME = "Zeke's Superior Scimitars."

/**
 * Zeke's Superior Scimitars in Al-Kharid.
 */
ShopHandler.create(SHOP_NAME) {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Bronze scimitar" x 10
        "Iron scimitar" x 10
        "Steel scimitar" x 10
        "Mithril scimitar" x 10
    }

    open {
        npc2 += ZEKE_ID
    }
}

/*
 * Handles Zeke's first npc option dialogue.
 *
 * The player can either open the scimitar shop or compliment Zeke's cloak. Easy clue scroll progress for Zeke is
 * handled separately by the treasure trails plugin.
 */
npc1(ZEKE_ID) {
    plr.newDialogue()
        .npc(targetNpc.id, Expression.HAPPY, "A thousand greetings, sir.")
        .options(
            "Do you want to trade?",
            {
                plr.newDialogue()
                    .player(Expression.QUIZZICAL, "Do you want to trade?")
                    .npc(targetNpc.id, Expression.HAPPY, "Yes, certainly. I deal in scimitars.")
                    .then { it.overlays.open(ShopInterface(world, SHOP_NAME)) }
                    .open()
            },
            "Nice cloak.",
            {
                plr.newDialogue()
                    .player("Nice cloak.")
                    .npc(targetNpc.id, "Thank you.")
                    .open()
            }
        ).open()
}
