package game.npc.spawn.westArdougne

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.dialogue.Expression

/**
 * The West Ardougne General Store, run by Chadwell.
 */
ShopHandler.create("West Ardougne General Store") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Pot" x 3 every 100
        "Rope" x 3 every 100
        "Bronze pickaxe" x 2 every 100
        "Bucket" x 2 every 100
        "Tinderbox" x 2 every 100
        "Hammer" x 3 every 100
        "Leather boots" x 2 every 100
        "Longbow" x 2 every 100
        "Bronze arrow" x 20 every 100
        "Salmon" x 10 every 100
        "Meat pie" x 10 every 100
        "Bread" x 5 every 100
        "Cooked meat" x 10 every 100
    }

    open {
        npc2 += 971
    }
}

npc1(971) {
    plr.newDialogue()
        .npc(971, "Good day. What can I get you?")
        .options("Let's see what you've got.", {
            it.newDialogue()
                .player(Expression.HAPPY, "Let's see what you've got.")
                .then { GeneralStore.open(it, "West Ardougne General Store") }
                .open()
        }, "Nothing thanks.", {
            it.newDialogue()
                .player("Nothing thanks.")
                .npc(971, Expression.HAPPY, "Okay then.")
                .open()
        })
        .open()
}
