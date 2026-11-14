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
package game.npc.spawn.brimhaven

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: amuletshop.
ShopHandler.create("Davon's Amulet Store") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.9, 0.02, 1.2)
    botAccess = { true }

    sell {
        1718 x 0 every 200 // Holy symbol
        1727 x 0 every 200 // Amulet of magic
        1729 x 0 every 200 // Amulet of defence
        1725 x 0 every 200 // Amulet of strength
        1731 x 0 every 200 // Amulet of power
    }

    open {
        npc2 += 588
    }
}

npc1(588) { GeneralStore.open(plr, "Davon's Amulet Store") }

// Lost City inventory: fishrestaurant.
ShopHandler.create("The Shrimp and Parrot") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.75, 0.03, 1.3)
    botAccess = { true }

    sell {
        347 x 5 every 900 // Herring
        339 x 5 every 1100 // Cod
        361 x 5 every 2300 // Tuna
        379 x 3 every 2600 // Lobster
        373 x 2 every 2900 // Swordfish
        3144 x 3 every 900 // Cooked karambwan
    }

    open {
        npc2 += 793
    }
}

npc1(793) { GeneralStore.open(plr, "The Shrimp and Parrot") }
