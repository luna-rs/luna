package game.npc.spawn.alKharid

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.dialogue.Expression

/**
 * The [Npc] ids for the shop keeper and shop assistant.
 */
val SHOPKEEPER_IDS = listOf(524, 525)

/**
 * The name of the shop.
 */
val SHOP_NAME = "Al-Kharid General Store"

/*
 * Registers the Al-Kharid general store.
 */
ShopHandler.create(SHOP_NAME) {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.FAST
    currency = Currency.COINS

    sell {
        "Pot" x 30
        "Jug" x 10
        "Shears" x 10
        "Bucket" x 30
        "Bowl" x 10
        "Cake tin" x 10
        "Tinderbox" x 10
        "Chisel" x 10
        "Hammer" x 10
        "Newcomer map" x 10
    }

    open {
        SHOPKEEPER_IDS.forEach { npc2 += it }
    }
}

/*
 * Handles the shop keeper and shop assistant's first npc option dialogue.
 *
 * The player can either open the general store or decline it.
 */
for (id in SHOPKEEPER_IDS) {
    npc1(id) {
        plr.newDialogue()
            .npc(targetNpc.id, Expression.HAPPY, "Can I help you at all?")
            .options(
                "Yes please. What are you selling?",
                {
                    plr.newDialogue()
                        .player(Expression.QUIZZICAL, "Yes please. What are you selling?")
                        .npc(targetNpc.id, Expression.HAPPY, "Take a look.")
                        .then { it.overlays.open(ShopInterface(world, SHOP_NAME)) }
                        .open()
                },
                "No thanks.",
                {
                    plr.newDialogue()
                        .player("No thanks.")
                        .open()
                }
            ).open()
    }
}
