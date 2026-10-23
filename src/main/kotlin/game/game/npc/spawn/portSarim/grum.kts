package game.npc.spawn.portSarim

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.bot.brain.BotActivity

/**
 * Grum's Gold Exchange in Port Sarim.
 */
ShopHandler.create("Grum's Gold Exchange.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    merchantsAccessOnly(true) { personality.isIntelligent }

    sell {
        "Gold ring" x 100
        "Sapphire ring" x 100
        "Emerald ring" x 100
        "Ruby ring" x 100
        "Diamond ring" x 100
        "Gold necklace" x 100
        "Sapphire necklace" x 100
        "Emerald necklace" x 100
        "Ruby necklace" x 100
        "Diamond necklace" x 100
        "Gold amulet" x 100
        "Sapphire amulet" x 100
        "Emerald amulet" x 100
        "Ruby amulet" x 100
        "Diamond amulet" x 100
    }

    open {
        npc2 += 556
    }
}
