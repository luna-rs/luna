package game.npc.spawn.rellekka

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface

/**
 * The [Npc] id for Yrsa.
 */
val YRSA_ID = 1301

/**
 * The name of the shop.
 */
val SHOP_NAME = "Yrsa's Accoutrements"

/*
 * Registers Yrsa's clothes shop in Rellekka. It is opened by the npc options below, so outerlanders can be turned
 * away.
 */
ShopHandler.create(SHOP_NAME) {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        3775 x 5 // Fremennik shirt (blue).
        3773 x 5 // Fremennik shirt (red).
        3767 x 5 // Fremennik shirt (dark brown).
        3769 x 5 // Fremennik shirt (green).
        3771 x 5 // Fremennik shirt (beige).
        "Fremennik robe" x 5
        "Fremennik skirt" x 5
        "Fremennik hat" x 5
        3799 x 5 // Gloves (Fremennik).
        "Fremennik boots" x 5
        3765 x 5 // Fremennik cloak (brown).
        3763 x 5 // Fremennik cloak (light brown).
        3761 x 5 // Fremennik cloak (dark brown).
        3759 x 5 // Fremennik cloak (green).
        3777 x 5 // Fremennik cloak (red).
        3779 x 5 // Fremennik cloak (grey).
        3781 x 5 // Fremennik cloak (tangerine).
        3783 x 5 // Fremennik cloak (ocean).
        3785 x 5 // Fremennik cloak (purple).
        3787 x 5 // Fremennik cloak (pink).
        3789 x 5 // Fremennik cloak (black).
    }
}

/*
 * Handles Yrsa's first npc option dialogue.
 *
 * Yrsa only sells clothes to Fremenniks, and turns outerlanders away.
 */
npc1(YRSA_ID) {
    if (Fremennik.isFremennik(plr)) {
        plr.newDialogue()
            .npc(targetNpc.id, "Hi. You wanted to buy some clothes?")
            .options(
                "I'd like to buy some clothes.", { it.overlays.open(ShopInterface(world, SHOP_NAME)) },
                "Neither thanks.",
                {
                    plr.newDialogue()
                        .player("Neither thanks.")
                        .npc(targetNpc.id, "As you wish.")
                        .open()
                }
            ).open()
    } else {
        plr.newDialogue()
            .player("Hello!")
            .npc(
                targetNpc.id,
                "I'm sorry outerlander, I cannot sell you any clothes.",
                "Our customs forbid it."
            )
            .open()
    }
}

/*
 * Handles Yrsa's "Trade" option.
 */
npc2(YRSA_ID) {
    if (Fremennik.isFremennik(plr)) {
        plr.overlays.open(ShopInterface(world, SHOP_NAME))
    } else {
        plr.newDialogue()
            .text("Only Fremenniks may buy clothes here.")
            .open()
    }
}
