package game.npc.spawn.portPhasmatys

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.Player

/**
 * The Port Phasmatys General Store, run by a ghost.
 */
ShopHandler.create("Port Phasmatys General Store") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Pot" x 100 every 15
        "Bucket" x 100 every 15
        "Shears" x 2 every 100
        "Jug" x 2 every 100
        "Tinderbox" x 2 every 100
        "Chisel" x 2 every 100
        "Hammer" x 5 every 100
    }
}

/**
 * The amulets of ghostspeak, which let players understand ghosts.
 */
val AMULETS_OF_GHOSTSPEAK = setOf(552, 4250)

/**
 * Determines if [plr] can understand ghosts.
 */
fun speaksGhost(plr: Player) = plr.equipment[Equipment.AMULET]?.id in AMULETS_OF_GHOSTSPEAK

/**
 * Tells [plr] they can't understand the ghost.
 */
fun wooo(plr: Player) {
    plr.newDialogue()
        .npc(1699, "Woooo wooo wooooo woooo")
        .text("You cannot understand the ghost.")
        .open()
}

npc1(1699) {
    if (!speaksGhost(plr)) {
        wooo(plr)
        return@npc1
    }
    plr.newDialogue()
        .npc(1699, "Can I help you at all?")
        .options("Yes please. What are you selling?", {
            GeneralStore.open(it, "Port Phasmatys General Store")
        }, "No, thanks.", {
            it.newDialogue().player("No, thanks.").open()
        })
        .open()
}

npc2(1699) {
    if (speaksGhost(plr)) {
        GeneralStore.open(plr, "Port Phasmatys General Store")
    } else {
        wooo(plr)
    }
}
