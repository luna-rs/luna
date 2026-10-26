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
 * The [Npc] id for the fish monger.
 */
val FISH_MONGER_ID = 1315

/**
 * The object id of the fish monger's fish stalls in Rellekka.
 */
val FISH_STALL_ID = 4277

/**
 * How long the fish monger remembers a player who stole from his stalls.
 */
val GRUDGE = 15.minutes

/**
 * The name of the shop.
 */
val SHOP_NAME = "Fremennik Fishmonger"

/*
 * Registers the fish monger's shop in Rellekka. It is opened by the npc options below, so outerlanders can be turned
 * away.
 */
ShopHandler.create(SHOP_NAME) {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Small fishing net" x 5
        "Fishing rod" x 5
        "Fly fishing rod" x 5
        "Harpoon" x 2
        "Lobster pot" x 2
        "Fishing bait" x 1500
        "Feather" x 1000
        "Big fishing net" x 5
        "Raw shrimps" x 0
        "Raw sardine" x 200
        "Raw herring" x 0
        "Raw mackerel" x 0
        "Raw cod" x 0
        "Raw anchovies" x 0
        "Raw trout" x 0
        "Raw pike" x 0
        "Raw salmon" x 0
        "Raw tuna" x 0
        "Raw lobster" x 0
        "Raw bass" x 0
        "Raw swordfish" x 0
        "Raw shark" x 0
    }
}

/**
 * Has the fish monger call the guards on [plr] if they stole from his stalls recently.
 *
 * @return `true` if [plr] was caught as a thief.
 */
fun catchThief(plr: Player, vararg text: String): Boolean {
    if (!StealFromAction.stoleRecently(plr, FISH_STALL_ID, GRUDGE)) {
        return false
    }
    plr.newDialogue()
        .npc(FISH_MONGER_ID, *text)
        .npc(FISH_MONGER_ID, "Guards guards!")
        .then { StealFromAction.callGuards(it) }
        .open()
    return true
}

/*
 * Handles the fish monger's first npc option dialogue.
 *
 * The fish monger only sells to Fremenniks, turns outerlanders away, and calls the guards on recent thieves.
 */
npc1(FISH_MONGER_ID) {
    // TODO The Fremennik Trials: call the thief by their Fremennik name.
    if (catchThief(plr, "It is ${plr.username} the thief!")) {
        return@npc1
    }
    if (Fremennik.isFremennik(plr)) {
        // TODO The Fremennik Trials: greet the player by their Fremennik name.
        plr.newDialogue()
            .npc(targetNpc.id, "Hello there ${plr.username}. Looking for fresh fish?")
            .then { it.overlays.open(ShopInterface(world, SHOP_NAME)) }
            .open()
    } else {
        plr.newDialogue()
            .npc(targetNpc.id, "I don't sell to outerlanders.")
            .text("Only Fremenniks may purchase fish here.")
            .open()
    }
}

/*
 * Handles the fish monger's "Trade" option.
 */
npc2(FISH_MONGER_ID) {
    if (catchThief(plr, "You're the one who stole something from me!")) {
        return@npc2
    }
    if (Fremennik.isFremennik(plr)) {
        plr.overlays.open(ShopInterface(world, SHOP_NAME))
    } else {
        plr.newDialogue()
            .text("Only Fremenniks may purchase fish here.")
            .open()
    }
}
