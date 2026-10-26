package game.npc.spawn.rellekka

import api.predef.*
import api.shop.dsl.ShopHandler
import game.skill.thieving.stealFromStall.StealFromAction
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.Player
import kotlin.time.Duration.Companion.minutes

/**
 * The [Npc] id for the fur trader.
 */
val FUR_TRADER_ID = 1316

/**
 * The object id of the fur trader's fur stalls in Rellekka.
 */
val FUR_STALL_ID = 4278

/**
 * How long the fur trader remembers a player who stole from his stalls.
 */
val GRUDGE = 15.minutes

/**
 * The name of the shop.
 */
val SHOP_NAME = "Fremennik Fur Trader"

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

/**
 * Has the fur trader call the guards on [plr] if they stole from his stalls recently.
 *
 * @return `true` if [plr] was caught as a thief.
 */
fun catchThief(plr: Player, vararg text: String): Boolean {
    if (!StealFromAction.stoleRecently(plr, FUR_STALL_ID, GRUDGE)) {
        return false
    }
    plr.newDialogue()
        .npc(FUR_TRADER_ID, *text)
        .npc(FUR_TRADER_ID, "Guards guards!")
        .then { StealFromAction.callGuards(it) }
        .open()
    return true
}

/*
 * Handles the fur trader's first npc option dialogue.
 *
 * The fur trader only sells to Fremenniks, turns outerlanders away, and calls the guards on recent thieves.
 */
npc1(FUR_TRADER_ID) {
    // TODO The Fremennik Trials: call the thief by their Fremennik name.
    if (catchThief(plr, "You think me stupid ${plr.username}?")) {
        return@npc1
    }
    if (Fremennik.isFremennik(plr)) {
        // TODO The Fremennik Trials: greet the player by their Fremennik name.
        plr.newDialogue()
            .npc(
                targetNpc.id,
                "Welcome back ${plr.username}!",
                "Have you seen the furs I have today?"
            )
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
    if (catchThief(plr, "You're the one who stole something from me!")) {
        return@npc2
    }
    if (Fremennik.isFremennik(plr)) {
        plr.overlays.open(ShopInterface(world, SHOP_NAME))
    } else {
        plr.newDialogue()
            .text("Only Fremenniks may purchase furs here.")
            .open()
    }
}
