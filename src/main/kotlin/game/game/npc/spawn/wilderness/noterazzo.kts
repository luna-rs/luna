package game.npc.spawn.wilderness

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.dialogue.Expression

/**
 * Bandit Duty Free, Noterazzo's general store in the Wilderness bandit camp.
 */
ShopHandler.create("Bandit Duty Free") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Pot" x 5 every 50
        "Jug" x 2 every 100
        "Tinderbox" x 2 every 100
        "Chisel" x 2 every 100
        "Hammer" x 5 every 100
        "Bronze pickaxe" x 5 every 100
        "Bronze axe" x 10 every 100
    }

    open {
        npc2 += 597
    }
}

npc1(597) {
    plr.newDialogue()
        .npc(597, Expression.SHIFTY, "Hey wanna trade? I'll give the best deals you can find.")
        .options("Yes please.", {
            it.newDialogue()
                .player("Yes please.")
                .then { GeneralStore.open(it, "Bandit Duty Free") }
                .open()
        }, "No thanks.", {
            it.newDialogue().player("No thanks.").open()
        }, "How can you afford to give such good deals?", {
            it.newDialogue()
                .player(Expression.QUIZZICAL, "How can you afford to give such good deals?")
                .npc(597, Expression.SHIFTY,
                     "The general stores in Asgarnia and Misthalin are",
                     "heavily taxed. It really makes it hard for them to run",
                     "an effective business. For some reason taxmen don't",
                     "visit my store.")
                .open()
        })
        .open()
}
