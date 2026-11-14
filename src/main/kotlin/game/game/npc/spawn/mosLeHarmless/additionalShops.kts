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
import io.luna.game.model.item.shop.Shop

// Lost City inventory: charleyshop.
ShopHandler.create("Charley's Fishing Shop") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { position.isWithinDistance(Position(3673, 2970, 0), 64) }

    sell {
        317 x 10 every 300 // Raw shrimps
        327 x 10 every 600 // Raw sardine
        345 x 7 every 900 // Raw herring
        353 x 7 every 1000 // Raw mackerel
        341 x 6 every 1100 // Raw cod
        321 x 5 every 1200 // Raw anchovies
        359 x 4 every 2300 // Raw tuna
        377 x 2 every 2600 // Raw lobster
        363 x 2 every 2700 // Raw bass
        371 x 1 every 2900 // Raw swordfish
    }

    open {
        npc2 += 3161
    }
}

npc1(3161) { GeneralStore.open(plr, "Charley's Fishing Shop") }

// Lost City inventory: smithsshop.
ShopHandler.create("Smithing Smith's Shop") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { position.isWithinDistance(Position(3689, 2954, 0), 64) }

    sell {
        1321 x 5 every 100 // Bronze scimitar
        1323 x 3 every 200 // Iron scimitar
        1325 x 2 every 600 // Steel scimitar
        1329 x 1 every 4000 // Mithril scimitar
        2347 x 1 every 100 // Hammer
    }

    open {
        npc2 += 3162
    }
}

npc1(3162) { GeneralStore.open(plr, "Smithing Smith's Shop") }

// Lost City inventory: joeshop.
ShopHandler.create("Harpoon Joe's House of Rum") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { position.isWithinDistance(Position(3666, 2990, 0), 64) }

    sell {
        1917 x 3 every 100 // Beer
        7157 x 3 every 100 // Braindeath 'rum'
        1993 x 1 every 15000 // Jug of wine
        2003 x 5 every 150 // Stew
    }

    open {
        npc2 += 3163
    }
}

npc1(3163) { GeneralStore.open(plr, "Harpoon Joe's House of Rum") }

// Lost City inventory: joeshop.
ShopHandler.create("The Other Inn") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { position.isWithinDistance(Position(3663, 2980, 0), 64) }

    sell {
        1917 x 3 every 100 // Beer
        7157 x 3 every 100 // Braindeath 'rum'
        1993 x 1 every 15000 // Jug of wine
        2003 x 5 every 150 // Stew
    }

    open {
        npc2 += 3164
    }
}

npc1(3164) { GeneralStore.open(plr, "The Other Inn") }

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
