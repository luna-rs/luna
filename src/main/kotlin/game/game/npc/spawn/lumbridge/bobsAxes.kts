package game.npc.spawn.lumbridge

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

val shopkeeperId = 519

ShopHandler.create("Bob's Brilliant Axes.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.FAST
    currency = Currency.COINS

    sell {
        "Bronze pickaxe" x 5
        "Bronze axe" x 10
        "Iron axe" x 5
        "Steel axe" x 3
        "Iron battleaxe" x 5
        "Steel battleaxe" x 2
        "Mithril battleaxe" x 1
    }

    open {
        npc2 += shopkeeperId
    }
}

npc1(shopkeeperId) {
    plr.newDialogue()
        .options("Give me a quest!", {
            plr.newDialogue()
                .npc(targetNpc.id, Expression.ANGRY, "Get yer own!")
                .open();
        },
            "Have you anything to sell?", {
                plr.newDialogue()
                    .player("Have you anything to sell?")
                    .npc(targetNpc.id, "Yes! I buy and sell axes! Take your pick (or axe)!")
                    .then { it.overlays.open(ShopInterface(world, "Bob's Brilliant Axes."))}
                    .open()
            },
            "Can you repair my items for me?", {
                plr.newDialogue()
                    .player("Can you repair my items for me?")
                    .npc(targetNpc.id, "Of course I'll repair it, though the materials may cost", "you. Just hand me the item and I'll have a look.")
                    .open()
            })
        .open()
}

on(ServerLaunchEvent::class) {
    world.addNpc(
        id = shopkeeperId,
        x = 3231,
        y = 3203)
        .startWandering(3, WanderingFrequency.NORMAL)
}