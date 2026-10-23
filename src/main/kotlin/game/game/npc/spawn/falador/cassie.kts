package game.npc.spawn.falador

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface

val shopkeeperId = 577

/**
 * Cassies shield shop in Falador.
 */
ShopHandler.create("Cassie's Shield Shop.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Wooden shield" x 50
        "Bronze sq shield" x 30
        "Bronze kiteshield" x 30
        "Iron sq shield" x 20
        "Iron kiteshield" x 10
        "Steel sq shield" x 10
        "Steel kiteshield" x 10
        "Mithril sq shield" x 10
    }

    open {
        npc2 += shopkeeperId
    }
}

/**
 * Dialogue for "Talk" option.
 */
npc1(shopkeeperId) {
    plr.newDialogue()
        .npc(targetNpc.id, "I buy and sell shields, do you want to trade?")
        .options("Yes please.", {
            it.overlays.open(ShopInterface(world, "Cassie's Shield Shop."))
        }, "No thank you.", {
            plr.newDialogue().player("No thank you.").open()
        })
        .open()
}