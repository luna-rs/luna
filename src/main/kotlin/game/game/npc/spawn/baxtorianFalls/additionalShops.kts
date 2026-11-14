/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 * MIT License
 *
 * Copyright (c) 2023-2026 Lost City
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package game.npc.spawn.baxtorianFalls

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: rasoolshop1.
ShopHandler.create("Rasolo the Wandering Merchant.") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.05, 0.02, 2.0)
    botAccess = { true }

    sell {
        1969 x 5 every 20 // Spinach roll
        2023 x 1 every 500 // Cocktail guide
        660 x 1 every 500 // Hat
        1215 x 1 every 500 // Dragon dagger
        550 x 20 every 500 // Newcomer map
        583 x 1 every 500 // Bailing bucket
        1941 x 1 every 500 // Swamp paste
        273 x 1 every 500 // Poison
        970 x 5 every 500 // Papyrus
        975 x 1 every 500 // Machete
        1599 x 1 every 500 // Holy mould
        2976 x 1 every 500 // Sickle mould
        1823 x 1 every 500 // Waterskin(4)
        1837 x 1 every 500 // Desert boots
        1854 x 1 every 500 // Shantay pass
        2524 x 10 every 20 // Toy horsey
        3377 x 10 every 20 // Sample bottle
        2894 x 1 every 500 // Boots
        1909 x 1 every 500 // Greenman's ale
        3787 x 1 every 500 // Fremennik cloak
        3801 x 20 every 20 // Keg of beer
        3678 x 1 every 500 // Flamtaer hammer
        3424 x 2 every 500 // Olive oil(3)
        3420 x 2 every 500 // Limestone brick
        5 x 1 every 500 // Instruction manual
    }

    open {
        npc2 += 1972
    }
}

npc1(1972) { GeneralStore.open(plr, "Rasolo the Wandering Merchant.") }
