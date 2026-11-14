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
package game.npc.spawn.apeAtoll

import api.bot.zone.Zone
import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: mm_food_shop.
ShopHandler.create("Solihib's Food Stall") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { this in Zone.APE_ATOLL }

    sell {
        4012 x 200 every 100 // Monkey nuts
        1963 x 1000 every 1000 // Banana
        4016 x 10 every 100 // Banana stew
        4014 x 20 every 100 // Monkey bar
    }

    open {
        npc2 += 1433
    }
}

npc1(1433) { GeneralStore.open(plr, "Solihib's Food Stall") }

// Lost City inventory: mm_crafting_shop.
ShopHandler.create("Hamab's Crafting Emporium") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { this in Zone.APE_ATOLL }

    sell {
        1755 x 2 every 100 // Chisel
        1592 x 4 every 100 // Ring mould
        1597 x 2 every 100 // Necklace mould
        4020 x 2 every 100 // M'amulet mould
        1733 x 3 every 5 // Needle
        1734 x 100 every 5 // Thread
        1759 x 100 every 100 // Ball of wool
    }

    open {
        npc2 += 1437
    }
}

npc1(1437) { GeneralStore.open(plr, "Hamab's Crafting Emporium") }
