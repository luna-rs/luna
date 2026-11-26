/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.lighthouse

import api.predef.*
import api.shop.dsl.ShopHandler
import game.content.godBooks.GodBook
import game.content.godBooks.GodBook.Companion.JOSSIK
import game.content.godBooks.GodBook.Companion.PRAYER_BOOKS_OPTION
import game.content.questRewards.QuestRewards
import game.content.quests.Quest
import game.content.quests.QuestGates
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption

// Lost City inventory: lighthouseshop.
ShopHandler.create("Lighthouse Store") {
    generalStore = true
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS

    sell {
        954 x 10 every 200 // Rope
        2347 x 10 every 500 // Hammer
        1755 x 10 every 500 // Chisel
        946 x 10 every 500 // Knife
        952 x 10 every 500 // Spade
        590 x 5 every 500 // Tinderbox
        36 x 10 every 500 // Candle
        273 x 10 every 200 // Poison
        233 x 10 every 500 // Pestle and mortar
        1931 x 10 every 500 // Pot
        1925 x 5 every 500 // Bucket
        1929 x 5 every 500 // Bucket of water
        1935 x 5 every 200 // Jug
        1937 x 5 every 500 // Jug of water
        229 x 100 every 500 // Vial
        230 x 9_900 every 500 // Vial (noted)
        227 x 100 every 500 // Vial of water
        2019 x 5 every 500 // Gin
        2021 x 5 every 500 // Brandy
        2015 x 5 every 500 // Vodka
        1915 x 5 every 500 // Grog
        2017 x 5 every 500 // Whisky
        1909 x 5 every 500 // Greenman's ale
        1913 x 5 every 500 // Dwarven stout
        1907 x 5 every 500 // Wizard's mind bomb
        1540 x 10 // Anti-dragon shield
    }

    open {
        npc2 += JOSSIK
    }
}

/**
 * The coins Jossik asks for another god's book once the player has completed theirs.
 */
val BOOK_PRICE = 5_000

npc1(JOSSIK) {
    if (!QuestGates.passes(plr, Quest.HORROR_FROM_THE_DEEP)) {
        GeneralStore.open(plr, "Lighthouse Store")
        return@npc1
    }
    plr.newDialogue()
        .npc(JOSSIK, "Hello again, adventurer. What brings you this way?")
        .options("Can I see your wares?", {
            it.newDialogue()
                .player("Can I see your wares?")
                .npc(JOSSIK, "Sure thing! I think you'll find my prices are remarkable!")
                .then { p -> GeneralStore.open(p, "Lighthouse Store") }
                .open()
        }, PRAYER_BOOKS_OPTION, { prayerBooks(it) },
                 "Nothing, really. Good bye.", { it.newDialogue().player("Nothing, really. Good bye.").open() })
        .open()
}

/**
 * Jossik hands out the damaged prayer books: one of the player's choice, any they have lost, and, once every book
 * they have is complete, another god's book for [BOOK_PRICE] coins.
 */
fun prayerBooks(plr: Player) {
    val dialogue = plr.newDialogue().player(PRAYER_BOOKS_OPTION)
    val given = GodBook.entries.filter { it.given(plr) }
    val lost = given.filter { !it.owned(plr) }
    val unclaimed = GodBook.entries.filter { !it.given(plr) }
    when {
        lost.isNotEmpty() -> {
            val book = lost.first()
            dialogue.npc(JOSSIK, "Yes, this one washed up again. I believe it's yours.")
                .then {
                    val id = if (book.completed(it)) book.complete else book.damaged
                    if (!book.owned(it)) QuestRewards.give(it, id)
                }
        }
        given.isEmpty() -> {
            dialogue.npc(JOSSIK, "It's amazing how things like that keep washing up on the shore in caskets.")
                .options(unclaimed.map { book -> DialogueOption(book.god) { claim(it, book, 0) } })
        }
        unclaimed.isEmpty() -> dialogue.npc(JOSSIK, "No, you already have every one I've found.")
        given.any { !it.completed(plr) } ->
            dialogue.npc(JOSSIK, "Not yet. Finish the book you have, and I'll see what else washes up.")
        else -> {
            val options = unclaimed.map { book -> DialogueOption(book.god) { claim(it, book, BOOK_PRICE) } } +
                DialogueOption("No thanks.") { it.newDialogue().player("No thanks.").open() }
            dialogue.npc(JOSSIK, "It's amazing how things like that keep washing up on the shore in caskets.")
                .npc(JOSSIK, "I can let you have another one for 5,000 coins.")
                .options(options)
        }
    }
    dialogue.open()
}

/**
 * Gives [plr] the damaged [book] for [price] coins.
 */
fun claim(plr: Player, book: GodBook, price: Int) {
    if (book.given(plr)) return
    if (price > 0 && !QuestRewards.canPay(plr, price)) {
        plr.newDialogue().npc(JOSSIK, "Come back when you have 5,000 coins.").open()
        return
    }
    if (!QuestRewards.give(plr, book.damaged, price)) return
    book.markGiven(plr)
    plr.newDialogue()
        .npc(JOSSIK, "Wow! ${book.found} I thought these things had all vanished! Well, it's all yours, I hope you " +
            "appreciate it.")
        .open()
}
