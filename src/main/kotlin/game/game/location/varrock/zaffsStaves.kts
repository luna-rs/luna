package game.location.varrock

import api.predef.*
import api.predef.ext.*
import api.shop.dsl.ShopHandler
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.dialogue.Expression
import io.luna.game.model.item.shop.*
import io.luna.game.model.mob.wandering.*

val shopkeeperId = 546

ShopHandler.create("Zaff's Superior Staffs!") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.FAST
    currency = Currency.COINS

    sell {
        "Staff" x 5
        "Magic staff" x 5
        "Staff of air" x 2
        "Staff of water" x 2
        "Staff of earth" x 2
        "Staff of fire" x 2
    }

    open {
        npc2 += shopkeeperId
    }
}

npc1(shopkeeperId) {
    plr.newDialogue()
        .npc(targetNpc.id, "Would you like to buy or sell some staffs?")
        .options("Yes, please!", {
                plr.overlays.open(ShopInterface(world, "Zaff's Superior Staffs!"))
            }, "No, thank you.", {
                plr.newDialogue()
                    .player("No, thank you.")
                    .npc(targetNpc.id, "Well, 'stick' your head in again", "if you change your mind.")
                    .player("Huh, terrible pun!", "You just can't get the 'staff' these days!")
                    .open()
            })
        .open()
}

on(ServerLaunchEvent::class) {
    world.addNpc(
        id = shopkeeperId,
        x = 3202,
        y = 3433)
        .startWandering(2, WanderingFrequency.NORMAL)
}