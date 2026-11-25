package game.content.culinaromancersChest

import api.predef.*
import api.shop.dsl.ShopHandler
import game.content.quests.Quest
import game.content.quests.QuestGates
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.varp.Varbit

/**
 * The chest in the Lumbridge Castle cellar. The client only shows it as the open chest once this varbit reaches 3.
 */
val CHEST = 12308
val CHEST_VARBIT = 1850

/**
 * The varbit's value once Recipe for Disaster is finished. It also hides the quest's portal and dining-room NPCs.
 */
val CHEST_COMPLETE = 5

/**
 * The chest's item store.
 */
val ITEMS_SHOP = "Culinaromancer's Chest."

on(LoginEvent::class) {
    if (QuestGates.passes(plr, Quest.RECIPE_FOR_DISASTER)) {
        plr.sendVarbit(Varbit(CHEST_VARBIT, CHEST_COMPLETE))
    }
}

// Bank.
object1(CHEST) {
    if (QuestGates.passes(plr, Quest.RECIPE_FOR_DISASTER)) {
        plr.bank.open()
    }
}

// TODO Buy-food: no dated list of the chest's food has been found yet.

// Buy-items.
object3(CHEST) {
    if (QuestGates.passes(plr, Quest.RECIPE_FOR_DISASTER)) {
        plr.overlays.open(ShopInterface(world, ITEMS_SHOP))
    }
}

// The whole stock, as after the full quest. Prices are 130% of the cache value.
// TODO Recipe for Disaster: each subquest unlocks one more tier of gloves and kitchen weapon.
ShopHandler.create(ITEMS_SHOP) {
    buy = BuyPolicy.NONE
    stockPricing = Shop.StockPricing(0.0, 0.02, 1.3)
    sell {
        // Gloves, from hardleather to barrows.
        7453 x 5; 7454 x 5; 7455 x 5; 7456 x 5; 7457 x 5; 7458 x 5; 7459 x 5; 7460 x 5; 7461 x 5; 7462 x 5

        // Wooden spoon, egg whisk, spork, spatula, frying pan, skewer, rolling pin, kitchen knife, meat tenderiser and
        // cleaver.
        7433 x 5; 7435 x 5; 7437 x 5; 7439 x 5; 7441 x 5; 7443 x 5; 7445 x 5; 7447 x 5; 7449 x 5; 7451 x 5
    }
}
