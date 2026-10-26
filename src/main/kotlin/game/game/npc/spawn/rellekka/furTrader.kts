package game.npc.spawn.rellekka

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.Player

/**
 * The [Npc] id for the fur trader.
 */
val FUR_TRADER_ID = 1316

/**
 * The name of the shop.
 */
val SHOP_NAME = "Fremennik Fur Trader"

/**
 * Returns whether [plr] is a Fremennik, which the fur trader requires before he will sell to them.
 */
fun isFremennik(plr: Player): Boolean {
    // TODO The Fremennik Trials: only players who have finished the quest are Fremenniks.
    return true
}

/*
 * Registers the fur trader's shop in Rellekka. It is opened by the npc options below, so outerlanders can be turned
 * away.
 */
ShopHandler.create(SHOP_NAME) {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Bear fur" x 3
        "Grey wolf fur" x 3
    }
}

/*
 * Handles the fur trader's first npc option dialogue.
 *
 * The fur trader only sells to Fremenniks, and turns outerlanders away.
 */
npc1(FUR_TRADER_ID) {
    if (isFremennik(plr)) {
        // TODO The Fremennik Trials: greet the player by their Fremennik name.
        plr.newDialogue()
            .npc(targetNpc.id, "Welcome back ${plr.username}! Have you seen the furs I have today?")
            .then { it.overlays.open(ShopInterface(world, SHOP_NAME)) }
            .open()
    } else {
        plr.newDialogue()
            .npc(targetNpc.id, "I don't sell to outerlanders.")
            .open()
    }
}

/*
 * Handles the fur trader's "Trade" option.
 */
npc2(FUR_TRADER_ID) {
    if (isFremennik(plr)) {
        plr.overlays.open(ShopInterface(world, SHOP_NAME))
    } else {
        plr.newDialogue()
            .text("Only Fremenniks may purchase furs here.")
            .open()
    }
}
