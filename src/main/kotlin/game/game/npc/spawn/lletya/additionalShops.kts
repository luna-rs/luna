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
package game.npc.spawn.lletya

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: lletyaseamstressshop1.
ShopHandler.create("Oronwen's General Store") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        1734 x 8 every 100 // Thread
        1733 x 3 every 100 // Needle
        1759 x 5 every 100 // Ball of wool
        1763 x 10 every 100 // Red dye
        1765 x 10 every 100 // Yellow dye
        1767 x 10 every 100 // Blue dye
        1769 x 10 every 100 // Orange dye
        1771 x 10 every 100 // Green dye
        1773 x 10 every 100 // Purple dye
    }

    open {
        npc2 += 2353
    }
}

npc1(2353) { GeneralStore.open(plr, "Oronwen's General Store") }

// Lost City inventory: lletyaarcheryshop1.
ShopHandler.create("Lletya Archery Shop") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        884 x 2000 every 20 // Iron arrow
        886 x 500 every 40 // Steel arrow
        888 x 500 every 60 // Mithril arrow
        890 x 450 every 120 // Adamant arrow
        892 x 400 every 130 // Rune arrow
        877 x 1500 every 10 // Bolts
        843 x 5 every 200 // Oak shortbow
        845 x 5 every 200 // Oak longbow
        837 x 5 every 400 // Crossbow
        849 x 5 every 70 // Willow shortbow
        847 x 5 every 70 // Willow longbow
    }

    open {
        npc2 += 2356
    }
}

npc1(2356) { GeneralStore.open(plr, "Lletya Archery Shop") }

// Lost City inventory: lletyafoodshop1.
ShopHandler.create("Lletya Food Store") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        2309 x 10 every 200 // Bread
        379 x 15 every 200 // Lobster
        1993 x 3 every 500 // Jug of wine
        1985 x 10 every 200 // Cheese
        1891 x 5 every 200 // Cake
    }

    open {
        npc2 += 2357
    }
}

npc1(2357) { GeneralStore.open(plr, "Lletya Food Store") }
