package game.npc.spawn.mortton

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.dialogue.Expression

/**
 * Razmire Keelgan's general store in Mort'ton.
 */
ShopHandler.create("Razmire General Store.") {
    buy = BuyPolicy.ALL
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
        "Olive oil(3)" x 150 every 10
        "Vial of water" x 100 every 100
        "Pot of flour" x 100 every 10
        "Flamtaer hammer" x 3 every 10
    }

    open {
        npc2 += 1254
    }
}

/**
 * Razmire Keelgan's building supplies store in Mort'ton.
 */
ShopHandler.create("Razmire Builders Merchants.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Plank" x 1000 every 10
        "Limestone brick" x 1000 every 10
        "Swamp paste" x 1000 every 10
    }

    open {
        npc3 += 1254
    }
}

// TODO Once Shades of Mort'ton is added, keep both stores closed until the player has laid the shades to rest, and only
//  let Razmire talk while he's been given serum.
npc1(1254) {
    plr.newDialogue()
        .npc(1254, Expression.HAPPY, "Ah, it's you, how's it going?")
        .player("Can you open a store for me?")
        .npc(1254, Expression.HAPPY, "Sure, which store do you wanna see?")
        .options("Can I see the general store please?", {
            GeneralStore.open(it, "Razmire General Store.")
        }, "Can I see the building store please?", {
            GeneralStore.open(it, "Razmire Builders Merchants.")
        }, "Ok, thanks.", {
            it.newDialogue().player("Ok, thanks").open()
        })
        .open()
}
