package game.npc.spawn.varrock

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.PlayerAppearance.DesignPlayerInterface

/**
 * interface id
 *  0 = legs (male)
 *  4731 = legs (female)
 *  2851 = torso (male)
 *  3038 = torso (female)
 * @author hydrozoa
 */
// TODO@0.5.0 Make the interfaces work.

val shopkeeperId = 548

ShopHandler.create("Thessalia's Fine Clothes.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.FAST
    currency = Currency.COINS

    sell {
        "White apron" x 30
        "Leather body" x 120
        "Leather gloves" x 100
        "Brown apron" x 10
        "Pink skirt" x 50
        "Black skirt" x 30
        "Blue skirt" x 20
        1007 x 40
        "Silk" x 50
        "Silk" x 50
        426 x 30
        428 x 30
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