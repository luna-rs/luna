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
package game.npc.spawn.feldipHills

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.Position
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Lost City inventory: uglug_nar_shop.
ShopHandler.create("Uglug's Stuffsies") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { position.isWithinDistance(Position(2442, 3049, 0), 64) }

    sell {
        4844 x 100 every 10 // Relicym's balm(3)
        2862 x 100 every 100 // Achey tree logs
        1777 x 10 every 200 // Bow string
        2876 x 0 every 500 // Raw chompy
        2878 x 10 every 100 // Cooked chompy
        4850 x 0 every 100 // Ogre coffin key
        946 x 5 every 100 // Knife
        4773 x 0 every 1000 // Bronze brutal
        4778 x 0 every 1000 // Iron brutal
        4783 x 0 every 1000 // Steel brutal
        4788 x 0 every 1500 // Black brutal
        4793 x 0 every 2000 // Mithril brutal
        4798 x 0 every 3000 // Adamant brutal
        4803 x 0 every 4000 // Rune brutal
        4827 x 0 every 2000 // Comp ogre bow
    }

    open {
        npc2 += 2039
    }
}

npc1(2039) { GeneralStore.open(plr, "Uglug's Stuffsies") }
