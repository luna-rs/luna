package game.npc.spawn.falador

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.*
import io.luna.game.model.mob.wandering.*

val shopkeeperId = 577

/**
 * Cassies shield shop in Falador.
 */
ShopHandler.create("Cassie's Shield Shop.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Wooden shield" x 5
        "Bronze sq shield" x 3
        "Bronze kiteshield" x 3
        "Iron sq shield" x 2
        "Iron kiteshield" zero 10
        "Steel sq shield" zero 10
        "Steel kiteshield" zero 10
        "Mithril sq shield" zero 10
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