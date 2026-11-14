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
package game.npc.spawn.portPhasmatys

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: farming_shop_4.
ShopHandler.create("Alice's Farming shop.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.7, 0.001, 1.0)
    botAccess = { true }

    sell {
        5341 x 500 every 10 // Rake
        5343 x 500 every 10 // Seed dibber
        5329 x 500 every 10 // Secateurs
        952 x 500 every 10 // Spade
        5325 x 500 every 120 // Gardening trowel
        5331 x 500 every 10 // Watering can
        5354 x 500 every 10 // Plant pot
        6032 x 500 every 10 // Compost
        5418 x 500 every 10 // Empty sack
        5376 x 500 every 10 // Basket
        1925 x 100 every 100 // Bucket
        1942 x 0 every 100 // Potato
        1957 x 0 every 100 // Onion
        1965 x 0 every 100 // Cabbage
        1982 x 0 every 100 // Tomato
        5986 x 0 every 100 // Sweetcorn
        5504 x 0 every 100 // Strawberry
        5982 x 0 every 100 // Watermelon
        5994 x 0 every 100 // Hammerstone hops
        5996 x 0 every 100 // Asgarnian hops
        5998 x 0 every 100 // Yanillian hops
        6000 x 0 every 100 // Krandorian hops
        6002 x 0 every 100 // Wildblood hops
        5931 x 0 every 100 // Jute fibre
        6006 x 0 every 100 // Barley
        6036 x 100 every 10 // Plant cure
    }

    open {
        npc2 += 2307
    }
}

npc1(2307) { GeneralStore.open(plr, "Alice's Farming shop.") }
