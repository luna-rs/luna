package game.npc.spawn.tyrasCamp

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.dialogue.DialogueUtils
import io.luna.game.model.mob.dialogue.Expression

/**
 * The Quartermaster's Stores in Tyras Camp.
 */
ShopHandler.create("Quartermaster's Stores") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    merchantsAccessOnly { combatLevel > 65 }

    sell {
        "Pot" x 5 every 100
        "Jug" x 2 every 100
        "Shears" x 3 every 100
        "Tinderbox" x 3 every 100
        "Bread" x 10 every 100
        "Bronze halberd" x 10 every 100
        "Iron halberd" x 10 every 100
        "Steel halberd" x 10 every 100
        "Black halberd" x 10 every 100
        "Mithril halberd" x 7 every 100
        "Adamant halberd" x 7 every 100
        "Rune halberd" x 7 every 100
        "Dragon halberd" x 5 every 500
    }
}

// The Quartermaster has no Trade option, so his stores are only opened by talking to him.
// TODO Once Regicide is added, sell the stock without the dragon halberds and tell players a new stock is coming soon
//  until they've finished the quest.
npc1(1208) {
    val title = if (plr.appearance.isFemale) "miss" else "sir"
    val greeting = "Good day $title. I'm the quartermaster for this camp. We have a little we could trade here."
    plr.newDialogue()
        .npc(1208, Expression.HAPPY, *DialogueUtils.splitChatLines(greeting))
        .npc(1208, Expression.HAPPY,
             "We have a new stock of dragon halberds. Would you",
             "like a look at what we have now?")
        .options("Yes please. What are you selling?", {
            it.newDialogue()
                .player(Expression.HAPPY, "Yes please. What are you selling?")
                .then { GeneralStore.open(it, "Quartermaster's Stores") }
                .open()
        }, "No thanks.", {
            it.newDialogue().player("No thanks.").open()
        })
        .open()
}
