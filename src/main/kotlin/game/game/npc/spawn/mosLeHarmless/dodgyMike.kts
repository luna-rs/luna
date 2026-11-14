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
package game.npc.spawn.mosLeHarmless

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.Position
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Lost City inventory: mikeshop.
ShopHandler.create("Dodgy Mike's Clothing") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { position.isWithinDistance(Position(3695, 2978, 0), 64) }

    sell {
        7114 x 15 every 100 // Pirate boots
        7110 x 10 every 100 // Stripy pirate shirt
        7112 x 20 every 100 // Pirate bandanna
        7116 x 10 every 100 // Pirate leggings
        7122 x 10 every 100 // Stripy pirate shirt
        7124 x 20 every 100 // Pirate bandanna
        7126 x 10 every 100 // Pirate leggings
        7128 x 10 every 100 // Stripy pirate shirt
        7130 x 20 every 100 // Pirate bandanna
        7132 x 10 every 100 // Pirate leggings
        7134 x 10 every 100 // Stripy pirate shirt
        7136 x 20 every 100 // Pirate bandanna
        7138 x 10 every 100 // Pirate leggings
    }

    open {
        npc2 += 3166
    }
}

npc1(3166) { GeneralStore.open(plr, "Dodgy Mike's Clothing") }
