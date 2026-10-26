package game.npc.spawn.ardougne

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.dialogue.Expression

/**
 * The [Npc] ids for Aemad and Kortan, who both run the shop.
 */
val SHOPKEEPER_IDS = listOf(590, 591)

/**
 * The name of the shop.
 */
val SHOP_NAME = "Aemad's Adventuring Supplies."

/*
 * Registers Aemad's adventuring supplies shop in East Ardougne.
 */
ShopHandler.create(SHOP_NAME) {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.FAST
    currency = Currency.COINS

    sell {
        "Vial of water" x 10
        "Bronze pickaxe" x 10
        "Iron axe" x 10
        "Cooked meat" x 10
        "Tinderbox" x 10
        "Ball of wool" x 100
        "Bronze arrow" x 1000
        "Rope" x 10
        "Papyrus" x 10
        "Knife" x 10
        "Jug" x 10
    }

    open {
        SHOPKEEPER_IDS.forEach { npc2 += it }
    }
}

/*
 * Handles Aemad and Kortan's first npc option dialogue.
 *
 * The player can either open the shop or decline it.
 */
for (id in SHOPKEEPER_IDS) {
    npc1(id) {
        plr.newDialogue()
            .npc(
                targetNpc.id,
                Expression.HAPPY,
                "Hello there. You've come to the right place if you're",
                "looking for adventurer's equipment."
            )
            .options(
                "Oh that sounds interesting.",
                {
                    plr.newDialogue()
                        .player("Oh that sounds interesting.")
                        .then { it.overlays.open(ShopInterface(world, SHOP_NAME)) }
                        .open()
                },
                "No, I've come to the wrong place.",
                {
                    plr.newDialogue()
                        .player("No, sorry, I've come to the wrong place.")
                        .npc(
                            targetNpc.id,
                            Expression.SAD,
                            "Hmph. Well, perhaps next time you'll need",
                            "something from me?"
                        )
                        .open()
                }
            ).open()
    }
}
