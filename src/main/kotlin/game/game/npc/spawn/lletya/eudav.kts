package game.npc.spawn.lletya

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.dialogue.Expression

/**
 * The Lletya General Store, run by Eudav.
 */
ShopHandler.create("Lletya General Store") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Pot" x 3 every 100
        "Jug" x 2 every 100
        "Shears" x 2 every 100
        "Bucket" x 2 every 100
        "Tinderbox" x 2 every 100
        "Chisel" x 2 every 100
        "Hammer" x 5 every 100
        "Spade" x 5 every 100
        "Knife" x 4 every 100
    }

    open {
        npc2 += 2352
    }
}

npc1(2352) {
    plr.newDialogue()
        .npc(2352, Expression.HAPPY, "Can I help you at all?")
        .options("Yes please. What are you selling?", {
            GeneralStore.open(it, "Lletya General Store")
        }, "No thanks.", {
            it.newDialogue().player(Expression.HAPPY, "No thanks.").open()
        })
        .open()
}
