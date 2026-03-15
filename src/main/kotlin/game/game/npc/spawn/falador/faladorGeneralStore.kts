package game.npc.spawn.falador

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import api.shop.dsl.ShopHandler
import com.google.common.collect.ImmutableList
import io.luna.game.model.item.shop.*
import io.luna.game.model.mob.wandering.*

val shopkeeperId = ImmutableList.of(524, 525)

/**
 * General store shop.
 */
ShopHandler.create("Falador General Store") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.FAST
    currency = Currency.COINS

    sell {
        "Pot" x 5
        "Jug" x 2
        "Shears" x 2
        "Bucket" x 3
        "Bowl" x 2
        "Cake tin" x 2
        "Tinderbox" x 2
        "Chisel" x 2
        "Hammer" x 5
        "Newcomer map" x 5
    }

    open {
        shopkeeperId.forEach({ npc2 += it })
    }
}

/**
 * Dialogue for "Talk" option.
 */
shopkeeperId.forEach({ npcId ->
    npc1(npcId) {
        plr.newDialogue()
            .npc(targetNpc.id, "Can I help you at all?")
            .options("Yes please, what are you selling?", {
                it.overlays.open(ShopInterface(world, "Falador General Store"))
            }, "No thanks.", {
                plr.newDialogue().player("No thanks.").open()
            })
            .open()
    }
})

/**
 * Spawn general store NPC.
 */
on(ServerLaunchEvent::class) {
    world.addNpc(
        id = shopkeeperId.get(0),
        x = 2959,
        y = 3388
    )
        .startWandering(3, WanderingFrequency.NORMAL)
    world.addNpc(
        id = shopkeeperId.get(1),
        x = 2958,
        y = 3388
    ).startWandering(3, WanderingFrequency.NORMAL)
}