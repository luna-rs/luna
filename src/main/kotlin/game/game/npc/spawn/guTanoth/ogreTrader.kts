package game.npc.spawn.guTanoth

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.dialogue.Expression

/**
 * Dal's General Ogre Supplies, the general store in Gu'Tanoth.
 */
ShopHandler.create("Dal's General Ogre Supplies") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Pot" x 3 every 100
        "Jug" x 2 every 100
        "Knife" x 2 every 100
        "Bucket" x 2 every 100
        "Tinderbox" x 2 every 100
        "Chisel" x 2 every 100
        "Hammer" x 5 every 100
    }

    open {
        npc2 += 873
    }
}

npc1(873) {
    plr.newDialogue()
        .npc(873, Expression.QUIZZICAL, "What the human be wantin'?")
        .options("Can I see what you are selling?", {
            it.newDialogue()
                .player("Can I see what you are selling?")
                .npc(873, Expression.BORED, "I suppose so.")
                .then { GeneralStore.open(it, "Dal's General Ogre Supplies") }
                .open()
        }, "I don't need anything.", {
            it.newDialogue()
                .player("I don't need anything.")
                .npc(873, Expression.BORED, "As you wish.")
                .open()
        })
        .open()
}
