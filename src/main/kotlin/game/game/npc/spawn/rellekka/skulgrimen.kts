package game.npc.spawn.rellekka

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.dialogue.Expression

/**
 * The [Npc] id for Skulgrimen.
 */
val SKULGRIMEN_ID = 1303

/**
 * The name of the shop.
 */
val SHOP_NAME = "Skulgrimen's Battle Gear"

/*
 * Registers Skulgrimen's battle gear shop in Rellekka. It is opened by the npc options below, so outerlanders can be
 * turned away.
 */
ShopHandler.create(SHOP_NAME) {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Bronze warhammer" x 5
        "Iron warhammer" x 4
        "Steel warhammer" x 3
        "Black warhammer" x 3
        "Mithril warhammer" x 2
        "Addy warhammer" x 1
        "Rune warhammer" x 0
        "Archer helm" x 5
        "Berserker helm" x 5
        "Warrior helm" x 5
        "Farseer helm" x 5
    }
}

/*
 * Handles Skulgrimen's first npc option dialogue.
 *
 * Skulgrimen only sells to Fremenniks, and turns outerlanders away.
 */
npc1(SKULGRIMEN_ID) {
    if (Fremennik.isFremennik(plr)) {
        // TODO The Fremennik Trials: greet the player by their Fremennik name.
        plr.newDialogue()
            .npc(targetNpc.id, Expression.HAPPY, "Hello again ${plr.username}. Come to see what's for sale?")
            .then { it.overlays.open(ShopInterface(world, SHOP_NAME)) }
            .open()
    } else {
        plr.newDialogue()
            .player(Expression.HAPPY, "Hello!")
            .npc(
                targetNpc.id,
                Expression.HAPPY,
                "Sorry. I can't sell weapons to outerlanders.",
                "Wouldn't be right. Against our beliefs."
            )
            .open()
    }
}

/*
 * Handles Skulgrimen's "Trade" option.
 */
npc2(SKULGRIMEN_ID) {
    if (Fremennik.isFremennik(plr)) {
        plr.overlays.open(ShopInterface(world, SHOP_NAME))
    } else {
        plr.newDialogue()
            .text("Only Fremenniks may purchase weapons and armour here.")
            .open()
    }
}
