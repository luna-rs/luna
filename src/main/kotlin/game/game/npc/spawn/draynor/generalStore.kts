package game.npc.spawn.draynor

import api.predef.*
import api.predef.ext.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface

/**
 * General store shop.
 */
ShopHandler.create("General Store") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.FAST
    currency = Currency.COINS

    sell {
        "Pot" x 15
        "Jug" x 15
        "Tinderbox" x 15
        "Chisel" x 15
        "Hammer" x 15
        "Bucket" x 15
        "Bowl" x 15
        "Anti-dragon shield" x 50
        "Lobster" x 150
    }
}

/**
 * Dialogue for "Talk" option. This keeper shares the Lumbridge shop keeper's id, so both stores check which keeper was
 * clicked.
 */
npc1(520) {
    if (GeneralStore.isDraynorKeeper(targetNpc)) {
        plr.newDialogue()
            .npc(targetNpc.id, "Hi, here's what I have in stock for today!")
            .then { it.overlays.open(ShopInterface(world, "General Store")) }.open()
    }
}

/**
 * The "Trade" option.
 */
npc2(520) {
    if (GeneralStore.isDraynorKeeper(targetNpc)) {
        plr.overlays.open(ShopInterface(world, "General Store"))
    }
}

/**
 * Spawn general store NPC.
 */
on(ServerLaunchEvent::class) {
    world.addNpc(id = 520,
                 x = GeneralStore.DRAYNOR_KEEPER_SPAWN.x,
                 y = GeneralStore.DRAYNOR_KEEPER_SPAWN.y)
}
