package game.npc.spawn.varrock

import api.predef.*
import api.predef.ext.*
import api.shop.dsl.ShopHandler
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.item.shop.*
import io.luna.game.model.mob.*
import io.luna.game.model.mob.block.PlayerAppearance.DesignPlayerInterface
import io.luna.game.model.mob.wandering.*

/**
 * todo make the interfaces work
 * interface id
 *  0 = legs (male)
 *  4731 = legs (female)
 *  2851 = torso (male)
 *  3038 = torso (female)
 * @author hydrozoa
 */

val shopkeeperId = 548

ShopHandler.create("Thessalia's Fine Clothes.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.FAST
    currency = Currency.COINS

    sell {
        "White apron" x 3
        "Leather body" x 12
        "Leather gloves" x 10
        "Brown apron" x 1
        "Pink skirt" x 5
        "Black skirt" x 3
        "Blue skirt" x 2
        1007 x 4
        "Silk" x 5
        "Silk" x 5
        426 x 3
        428 x 3
    }

    open {
        npc2 += shopkeeperId
    }
}

npc1(shopkeeperId) {
    plr.newDialogue()
        .npc(targetNpc.id, "Do you want to buy any fine clothes?")
        .options("What have you got?", {
            plr.newDialogue()
                .player("What have you got?")
                .npc(targetNpc.id, "Well, I have a number of fine pieces of clothing", "on sale or, if you prefer, I can offer you ", "an exclusive, total-clothing makeover?")
                .options("Tell me more about this makeover.", {
                    plr.newDialogue()
                        .npc(targetNpc.id, "Certainly!")
                        .npc(targetNpc.id, "Here at Thessalia's fine clothing boutique,", "we offer a unique service where we will totally", "revamp your outfit to your choosing.")
                        .npc(targetNpc.id, "It's on the house, completely free! Tired of ", "always wearing the same old outfit, day in, day out?", "This is the service for you!")
                        .npc(targetNpc.id, "So what do you say? Interested?")
                        .options("I'd like the makeover please.", {
                            plr.overlays.open(DesignPlayerInterface())
                        }, "I'd just like to buy some clothes.", {
                            plr.overlays.open(ShopInterface(world, "Thessalia's Fine Clothes."))
                        }, "No, thank you.", {
                            noThanksDialogue(plr, targetNpc)
                        })
                        .open()
                }, "I'd just like to buy some clothes.", {
                    plr.overlays.open(ShopInterface(world, "Thessalia's Fine Clothes."))
                }, "No, thank you.", {
                    noThanksDialogue(plr, targetNpc)
                })
                .open()
        }, "No, thank you.", {
            noThanksDialogue(plr, targetNpc)
        })
        .open()
}

fun noThanksDialogue(plr: Player, targetNpc: Npc) {
    plr.newDialogue()
        .player("No, thank you.")
        .npc(targetNpc.id, "Well, please return if ", "you change your mind.")
        .open()
}

on(ServerLaunchEvent::class) {
    world.addNpc(
        id = shopkeeperId,
        x = 3205,
        y = 3417)
        .startWandering(3, WanderingFrequency.NORMAL)
}