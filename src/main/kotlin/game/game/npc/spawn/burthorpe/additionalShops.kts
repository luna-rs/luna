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
package game.npc.spawn.burthorpe

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: 2handedshop.
ShopHandler.create("Gaius' Two Handed Shop.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.6, 0.02, 1.0)
    botAccess = { true }

    sell {
        1307 x 4 every 200 // Bronze 2h sword
        1309 x 3 every 300 // Iron 2h sword
        1311 x 2 every 500 // Steel 2h sword
        1313 x 1 every 700 // Black 2h sword
        1315 x 1 every 1000 // Mithril 2h sword
        1317 x 1 every 15000 // Adamant 2h sword
    }

    open {
        npc2 += 586
    }
}

npc1(586) { GeneralStore.open(plr, "Gaius' Two Handed Shop.") }

// Lost City inventory: death_pub.
ShopHandler.create("The Toad and Chicken") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.0, 0.01, 1.0)
    botAccess = { true }

    sell {
        1905 x 12 every 10 // Asgarnian ale
        1907 x 12 every 10 // Wizard's mind bomb
        1913 x 12 every 10 // Dwarven stout
    }

    open {
        npc2 += 1357
        npc2 += 1358
        npc2 += 1079
    }
}

npc1(1357) { GeneralStore.open(plr, "The Toad and Chicken") }
npc1(1358) { GeneralStore.open(plr, "The Toad and Chicken") }
npc1(1079) { GeneralStore.open(plr, "The Toad and Chicken") }

// Lost City inventory: death_generalshop.
ShopHandler.create("Burthorpe Supplies") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.4, 0.03, 1.3)
    botAccess = { true }

    sell {
        1931 x 3 every 100 // Pot
        1935 x 2 every 100 // Jug
        1735 x 2 every 100 // Shears
        1925 x 2 every 100 // Bucket
        590 x 2 every 100 // Tinderbox
        1755 x 2 every 100 // Chisel
        2347 x 5 every 100 // Hammer
        946 x 2 every 100 // Knife
        1980 x 20 every 50 // Empty cup
    }

    open {
        npc2 += 1083
    }
}

npc1(1083) { GeneralStore.open(plr, "Burthorpe Supplies") }
