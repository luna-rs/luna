package game.npc.spawn.portSarim

import api.predef.*
import api.predef.ext.*
import api.shop.dsl.ShopHandler
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.item.shop.*
import io.luna.game.model.mob.wandering.*

val shopkeeperId = 559

ShopHandler.create("Brian's Battleaxe Bazaar.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.DEFAULT
    currency = Currency.COINS

    sell {
        "Bronze battleaxe" x 4
        "Iron battleaxe" x 3
        "Steel battleaxe" x 2
        "Black battleaxe" x 1
        "Mithril battleaxe" x 1
        "Adamant battleaxe" x 1
    }

    open {
        npc2 += shopkeeperId
    }
}

npc1(shopkeeperId, {
    plr.newDialogue()
        .options(
            "So, are you selling something?", {
                plr.newDialogue()
                    .player("So, are you selling something?")
                    .npc(targetNpc.id, "Yep, take a lok at these great axes!")
                    .then({it.overlays.open(ShopInterface(world, "Brian's Battleaxe Bazaar."))})
                    .open()
            },
            "'Ello.", {
                plr.newDialogue()
                    .player("'Ello.")
                    .npc(targetNpc.id, "'Ello!")
                    .open()
            })
        .open()
})

on(ServerLaunchEvent::class) {
    world.addNpc(shopkeeperId, 3029, 3249)
        .startWandering(3, WanderingFrequency.NORMAL)
}