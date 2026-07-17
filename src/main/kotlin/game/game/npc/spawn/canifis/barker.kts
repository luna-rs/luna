package game.npc.spawn.canifis

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.bot.brain.BotActivity

/**
 * Barkers' Haberdashery in Canifis.
 */
ShopHandler.create("Barkers' Haberdashery") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    botAccess = { preferences.likesActivity(BotActivity.MERCHANTING) || personality.isSocial }

    sell {
        2900 x 50 // Grey hat
        2896 x 50 // Grey robe top
        2898 x 50 // Grey robe bottoms
        2902 x 50 // Grey gloves
        2894 x 50 // Grey boots

        2910 x 50 // Red hat
        2906 x 50 // Red robe top
        2908 x 50 // Red robe bottoms
        2912 x 50 // Red gloves
        2904 x 50 // Red boots

        2920 x 50 // Yellow hat
        2916 x 50 // Yellow robe top
        2918 x 50 // Yellow robe bottoms
        2922 x 50 // Yellow gloves
        2914 x 50 // Yellow boots

        2930 x 50 // Teal hat
        2926 x 50 // Teal robe top
        2928 x 50 // Teal robe bottoms
        2932 x 50 // Teal gloves
        2924 x 50 // Teal boots

        2940 x 50 // Purple hat
        2936 x 50 // Purple robe top
        2938 x 50 // Purple robe bottoms
        2942 x 50 // Purple gloves
        2934 x 50 // Purple boots

        1007 x 50 // Red cape
        1021 x 50 // Blue cape
        1023 x 50 // Yellow cape
        1027 x 50 // Green cape
    }

    open {
        npc2 += 1039
    }
}
