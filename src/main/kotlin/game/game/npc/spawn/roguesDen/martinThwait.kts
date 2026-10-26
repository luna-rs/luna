package game.npc.spawn.roguesDen

import api.predef.*
import api.predef.ext.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.Player

/**
 * Martin Thwait's Lost and Found in the Rogues' Den.
 */
ShopHandler.create("Martin Thwait's Lost and Found.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { thieving.level >= 50 && agility.level >= 50 }

    sell {
        "Rope" x 50 every 100
        "Lockpick" x 25 every 100
        "Chisel" x 30 every 100
        "Knife" x 20 every 100
        "Stethoscope" x 25 every 100
        "Bronze knife" x 15 every 100
        "Iron knife" x 10 every 100
        "Steel knife" x 5 every 100
        "Bronze claws" x 3 every 100
        "Iron claws" x 2 every 100
        "Steel claws" x 1 every 100
    }
}

/**
 * Opens Martin's store for [plr] if they have the 50 Thieving and 50 Agility he asks for.
 */
fun trade(plr: Player) {
    when {
        plr.thieving.level < 50 -> plr.newDialogue()
            .npc(2270,
                 "Sorry, mate. Train up your Thieving skill to at least",
                 "50 and I might be able to help you out.")
            .open()

        plr.agility.level < 50 -> plr.newDialogue()
            .npc(2270,
                 "Sorry, mate. Train up your Agility skill to at least",
                 "50 and I might be able to help you out.")
            .open()

        else -> GeneralStore.open(plr, "Martin Thwait's Lost and Found.")
    }
}

npc1(2270) {
    plr.newDialogue()
        .npc(2270,
             "You know it's sometimes funny how things work out, I",
             "lose some gold but find an item, or I lose an item and",
             "find some gold... no-one ever knows what's gone where",
             "ya know.")
        .options("Yeah I know what you mean, found anything recently?", {
            it.newDialogue()
                .player("Yeah I know what you mean, found anything recently?")
                .then { trade(it) }
                .open()
        }, "Okay... I'll be going now.", {
            it.newDialogue().player("Okay... I'll be going now.").open()
        })
        .open()
}

npc2(2270) { trade(plr) }
