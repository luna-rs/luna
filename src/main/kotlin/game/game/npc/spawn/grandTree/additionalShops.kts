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
package game.npc.spawn.grandTree

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: gnomeshop_gulluck.
ShopHandler.create("Gulluck and Sons") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.25, 0.01, 1.0)
    botAccess = { true }

    sell {
        882 x 200 every 10 // Bronze arrow
        877 x 150 every 10 // Bolts
        880 x 1 every 1500 // Pearl bolts
        841 x 4 every 100 // Shortbow
        839 x 2 every 200 // Longbow
        837 x 2 every 400 // Crossbow
        39 x 200 every 10 // Bronze arrowtips
        40 x 180 every 20 // Iron arrowtips
        41 x 160 every 40 // Steel arrowtips
        42 x 140 every 80 // Mithril arrowtips
        1349 x 5 every 200 // Iron axe
        1353 x 3 every 400 // Steel axe
        1363 x 5 every 100 // Iron battleaxe
        1365 x 2 every 200 // Steel battleaxe
        1369 x 1 every 3000 // Mithril battleaxe
        1307 x 4 every 200 // Bronze 2h sword
        1309 x 3 every 300 // Iron 2h sword
        1311 x 2 every 500 // Steel 2h sword
        1313 x 1 every 700 // Black 2h sword
        1315 x 1 every 1000 // Mithril 2h sword
        1317 x 1 every 15000 // Adamant 2h sword
    }

    open {
        npc2 += 602
    }
}

npc1(602) { GeneralStore.open(plr, "Gulluck and Sons") }

// Lost City inventory: blurberrybar.
ShopHandler.create("Blurberry Bar") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.25, 0.01, 1.0)
    botAccess = { true }

    sell {
        2028 x 10 every 100 // Blurberry special
        2030 x 10 every 100 // Choc saturday
        2032 x 10 every 100 // Drunk dragon
        2034 x 10 every 100 // Fruit blast
        2036 x 10 every 100 // Pineapple punch
        2038 x 10 every 100 // Short green guy
        2040 x 10 every 100 // Wizard blizzard
    }

    open {
        npc2 += 849
    }
}

npc1(849) { GeneralStore.open(plr, "Blurberry Bar") }

// Lost City inventory: giannerestaurant.
ShopHandler.create("Gianne's Restaurant") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.25, 0.01, 1.0)
    botAccess = { true }

    sell {
        2227 x 3 every 100 // Vegetable batta
        2219 x 3 every 100 // Worm batta
        2221 x 3 every 100 // Toad batta
        2225 x 3 every 100 // Fruit batta
        2223 x 3 every 100 // Cheese+tom batta
        2233 x 3 every 100 // Worm hole
        2231 x 3 every 100 // Tangled toad's legs
        2235 x 3 every 100 // Veg ball
        2229 x 3 every 100 // Chocolate bomb
        2237 x 3 every 100 // Worm crunchies
        2243 x 3 every 100 // Toad crunchies
        2239 x 3 every 100 // Chocchip crunchies
        2241 x 3 every 100 // Spicy crunchies
    }

    open {
        npc2 += 851
    }
}

npc1(851) { GeneralStore.open(plr, "Gianne's Restaurant") }
