package game.location.falador

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
        "Iron kiteshield" x 0
        "Steel sq shield" x 0
        "Steel kiteshield" x 0
        "Mithril sq shield" x 0
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

/**
 * Spawn Flynn NPC.
 */
on(ServerLaunchEvent::class) {
    world.addNpc(id = shopkeeperId,
        x = 2975,
        y = 3383)
        .startWandering(3, WanderingFrequency.NORMAL)
}