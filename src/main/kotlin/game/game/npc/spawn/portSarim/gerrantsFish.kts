package game.npc.spawn.portSarim

import api.predef.*
import api.predef.ext.*
import api.shop.dsl.ShopHandler
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.item.shop.*
import io.luna.game.model.mob.wandering.*

val shopkeeperId = 558

ShopHandler.create("Gerrant's Fishy Business.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.FAST
    currency = Currency.COINS

    sell {
        "Small fishing net" x 5
        "Fishing rod" x 5
        "Fly fishing rod" x 5
        "Harpoon" x 1
        "Lobster pot" x 5
        "Fishing bait" x 1500
        "Feather" x 1000
        "Raw shrimps" zero 200
        "Raw sardine" zero 200
        "Raw herring" zero 200
        "Raw anchovies" zero 200
        "Raw trout" zero 200
        "Raw pike" zero 200
        "Raw salmon" zero 200
        "Raw tuna" zero 200
        "Raw lobster" zero 200
        "Raw swordfish" zero 200
    }

    open {
        npc2 += shopkeeperId
    }
}

npc1(shopkeeperId, {
    plr.newDialogue()
        .npc(targetNpc.id, "Wecome! You can buy fishing equipment at my store.", "We'll also buy anything you catch off you.")
        .options(
            "Let's see what you've got then.", {
                plr.newDialogue()
                    .player("Let's see what you've got then.")
                    .then({it.overlays.open(ShopInterface(world, "Gerrant's Fishy Business."))})
                    .open()
            },
            "Sorry, I'm not interested.", {
                plr.newDialogue().player("Sorry, I'm not interested.").open()
            })
        .open()
})

on(ServerLaunchEvent::class) {
    world.addNpc(shopkeeperId, 3015, 3225)
        .startWandering(3, WanderingFrequency.NORMAL)
}