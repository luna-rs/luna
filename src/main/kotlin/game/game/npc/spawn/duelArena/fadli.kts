/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.duelArena

import api.predef.*
import api.shop.dsl.ShopHandler
import engine.bank.Banking
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.dialogue.DialogueUtils

// Lost City inventory: duel_rottenfruitshop.
ShopHandler.create("Shop of Distaste") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.55, 0.01, 1.0)

    sell {
        2518 x 2000 every 10 // Rotten tomato
    }

    open {
        npc3 += 958
        object1 += 3195
    }
}

val FADLI = 958

npc1(FADLI, { _, _ -> Banking.BANKER_POLICY }) {
    plr.newDialogue()
        .player("Hi.")
        .npc(FADLI, "What?")
        .options("What do you do?", {
            it.newDialogue()
                .player("What do you do?")
                .npc(FADLI, *DialogueUtils.splitChatLines("You can store your stuff here if you want. You can dump " +
                                                              "anything you don't want to carry whilst you're " +
                                                              "fighting duels and then pick it up again on the way out."))
                .npc(FADLI, "To be honest I'm wasted here.")
                .npc(FADLI, *DialogueUtils.splitChatLines("I should be winning duels in the arena! I'm the best " +
                                                              "warrior in Al Kharid!"))
                .player("Easy, tiger!")
                .open()
        }, "What is this place?", {
            it.newDialogue()
                .player("What is this place?")
                .npc(FADLI, "Isn't it obvious?")
                .npc(FADLI, "This is the Duel Arena...duh!")
                .open()
        }, "I'd like to access my bank, please.", {
            it.newDialogue()
                .player("I'd like to access my bank, please.")
                .npc(FADLI, "Sure.")
                .then { it.bank.open() }
                .open()
        }, "Do you watch any matches?", {
            it.newDialogue()
                .player("Do you watch any matches?")
                .npc(FADLI, "When I can.")
                .npc(FADLI, *DialogueUtils.splitChatLines("Most aren't any good so I throw rotten fruit at them!"))
                .player("Heh. Can I buy some?")
                .npc(FADLI, "Sure.")
                .then { it.overlays.open(ShopInterface(world, "Shop of Distaste")) }
                .open()
        })
        .open()
}

npc2(FADLI, { _, _ -> Banking.BANKER_POLICY }) { plr.bank.open() }
