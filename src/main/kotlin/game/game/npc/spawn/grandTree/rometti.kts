package game.npc.spawn.grandTree

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.bot.brain.BotActivity

/**
 * Fine Fashions shop in the Grand Tree.
 */
ShopHandler.create("Fine Fashions") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    merchantsAccessOnly { personality.isSocial }

    sell {
        656 x 5 // Pink hat
        658 x 5 // Green hat
        660 x 5 // Blue hat
        662 x 5 // Cream hat
        664 x 5 // Turquoise hat

        636 x 5 // Pink robe top
        638 x 5  // Green robe top
        640 x 5 // Blue robe top
        642 x 5 // Cream robe top
        644 x 5 // Turquoise robe top

        646 x 5 // Pink robe bottoms
        648 x 5 // Green robe bottoms
        650 x 5 // Blue robe bottoms
        652 x 5 // Cream robe bottoms
        654 x 5 // Turquoise robe bottoms

        626 x 5 // Pink boots
        628 x 5 // Green boots
        630 x 5 // Blue boots
        632 x 5 // Cream boots
        634 x 5 // Turquoise boots
    }

    open {
        npc2 += 601
    }
}
