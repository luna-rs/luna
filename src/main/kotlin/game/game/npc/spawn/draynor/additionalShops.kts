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
package game.npc.spawn.draynor

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: pillory_rottenfruitshop; opened through the vegetable crate.
ShopHandler.create("Pelter's Veg Stall") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.55, 0.001, 1.0)
    botAccess = { false } // A punishment-event supply, rather than an ordinary bot shopping destination.

    sell {
        2518 x 100 every 100 // Rotten tomato; no item-specific restock interval is recorded.
    }

    open {
        object1 += 6839
    }
}

// Lost City inventory: aprilfoolshorseshop.
ShopHandler.create("Diango's Toy Store") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.5, 0.02, 1.5)
    botAccess = { true }

    sell {
        2520 x 5 every 20 // Toy horsey
        2522 x 5 every 20 // Toy horsey
        2524 x 5 every 20 // Toy horsey
        2526 x 5 every 20 // Toy horsey
        4613 x 250 every 20 // Spinning plate
    }

    open {
        npc2 += 970
    }
}

npc1(970) { GeneralStore.open(plr, "Diango's Toy Store") }

// Lost City inventory: seed_stall.
ShopHandler.create("Draynor Seed Market.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.6, 0.03, 1.2)
    botAccess = { true }

    sell {
        5318 x 20 every 100 // Potato seed
        5319 x 10 every 100 // Onion seed
        5324 x 5 every 100 // Cabbage seed
        5322 x 0 every 100 // Tomato seed
        5320 x 0 every 100 // Sweetcorn seed
        5323 x 0 every 100 // Strawberry seed
        5321 x 0 every 100 // Watermelon seed
        5305 x 20 every 100 // Barley seed
        5306 x 5 every 100 // Jute seed
        5097 x 20 every 100 // Rosemary seed
        5096 x 20 every 100 // Marigold seed
        5307 x 20 every 100 // Hammerstone seed
        5308 x 10 every 100 // Asgarnian seed
        5309 x 0 every 100 // Yanillian seed
        5310 x 0 every 100 // Krandorian seed
        5311 x 0 every 100 // Wildblood seed
    }

    open {
        npc2 += 2233
    }
}

npc1(2233) { GeneralStore.open(plr, "Draynor Seed Market.") }

// Lost City inventory: wine_merchant.
ShopHandler.create("Wine Shop") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        1993 x 5 every 1500 // Jug of wine
        1935 x 3 every 100 // Jug
        7919 x 2 every 1500 // Bottle of wine
    }

    open {
        npc2 += 3671
    }
}

npc1(3671) { GeneralStore.open(plr, "Wine Shop") }
