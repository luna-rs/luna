package game.npc.spawn.portSarim

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.dialogue.Expression

/**
 * The [Npc] id for Grum.
 */
val GRUM_ID = 556

/**
 * The name of the shop.
 */
val SHOP_NAME = "Grum's Gold Exchange."

/**
 * Grum's Gold Exchange in Port Sarim.
 */
ShopHandler.create(SHOP_NAME) {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    merchantsAccessOnly(true) { personality.isIntelligent }

    sell {
        "Gold ring" x 0
        "Sapphire ring" x 0
        "Emerald ring" x 0
        "Ruby ring" x 0
        "Diamond ring" x 0
        "Gold necklace" x 0
        "Sapphire necklace" x 0
        "Emerald necklace" x 0
        "Ruby necklace" x 0
        "Diamond necklace" x 0
        1692 x 0 // Gold amulet (strung).
        1694 x 0 // Sapphire amulet (strung).
        1696 x 0 // Emerald amulet (strung).
        1698 x 0 // Ruby amulet (strung).
        1700 x 0 // Diamond amulet (strung).
    }

    open {
        npc2 += GRUM_ID
    }
}

/*
 * Handles Grum's first npc option dialogue.
 *
 * The player can either open the gold shop or decline it.
 */
npc1(GRUM_ID) {
    plr.newDialogue()
        .npc(targetNpc.id, Expression.HAPPY, "Would you like to buy or sell some gold jewellery?")
        .options(
            "Yes please.",
            {
                plr.newDialogue()
                    .player(Expression.HAPPY, "Yes please.")
                    .then { it.overlays.open(ShopInterface(world, SHOP_NAME)) }
                    .open()
            },
            "No, I'm not that rich.",
            {
                plr.newDialogue()
                    .player(Expression.HAPPY, "No, I'm not that rich.")
                    .npc(targetNpc.id, Expression.ANGRY, "Get out then! We don't want any riff-raff in here.")
                    .open()
            }
        ).open()
}
