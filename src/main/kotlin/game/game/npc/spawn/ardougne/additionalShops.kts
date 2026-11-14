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
package game.npc.spawn.ardougne

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import game.npc.spawn.StallMerchant
import game.skill.thieving.stealFromStall.ThievingStallType
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: silverstall.
ShopHandler.create("Ardougne Silver Stall.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.7, 0.02, 1.0)
    botAccess = { !StallMerchant.recentTheft(this, ThievingStallType.SILVER, 1000) }

    sell {
        1714 x 2 every 100 // Unstrung symbol
        442 x 1 every 2000 // Silver ore
        2355 x 1 every 1000 // Silver bar
    }
}

npc1(569) { StallMerchant.open(plr, 569, "Ardougne Silver Stall.", ThievingStallType.SILVER, 1000) }
npc2(569) { StallMerchant.open(plr, 569, "Ardougne Silver Stall.", ThievingStallType.SILVER, 1000) }

// Lost City inventory: ardougnegemstall.
ShopHandler.create("Ardougne Gem Stall.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.8, 0.03, 1.5)
    merchantsAccessOnly(strict = true) { !StallMerchant.recentTheft(this, ThievingStallType.GEM, 1000) }

    sell {
        1607 x 2 every 15000 // Sapphire
        1605 x 1 every 35000 // Emerald
        1603 x 1 every 60000 // Ruby
        1601 x 0 every 4000 // Diamond
    }
}

npc1(570) { StallMerchant.open(plr, 570, "Ardougne Gem Stall.", ThievingStallType.GEM, 1000) }
npc2(570) { StallMerchant.open(plr, 570, "Ardougne Gem Stall.", ThievingStallType.GEM, 1000) }

// Lost City inventory: bakery.
ShopHandler.create("Ardougne Baker's Stall.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.8, 0.02, 1.0)
    botAccess = { !StallMerchant.recentTheft(this, ThievingStallType.BAKERY, 1500) }

    sell {
        2309 x 10 every 100 // Bread
        1891 x 3 every 800 // Cake
        1901 x 8 every 200 // Chocolate slice
        1973 x 7 every 200 // Chocolate bar
    }
}

npc1(571) { StallMerchant.open(plr, 571, "Ardougne Baker's Stall.", ThievingStallType.BAKERY, 1500) }
npc2(571) { StallMerchant.open(plr, 571, "Ardougne Baker's Stall.", ThievingStallType.BAKERY, 1500) }

// Lost City inventory: spicestall.
ShopHandler.create("Ardougne Spice Stall.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.6, 0.02, 1.0)
    botAccess = { !StallMerchant.recentTheft(this, ThievingStallType.SPICE, 600) }

    sell {
        2007 x 1 every 100 // Spice
        946 x 1 every 2 // Knife
        1550 x 2 every 5 // Garlic
    }
}

npc1(572) { StallMerchant.open(plr, 572, "Ardougne Spice Stall.", ThievingStallType.SPICE, 600) }
npc2(572) { StallMerchant.open(plr, 572, "Ardougne Spice Stall.", ThievingStallType.SPICE, 600) }

// Lost City inventory: furshop.
ShopHandler.create("Ardougne Fur Stall.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.95, 0.02, 1.2)
    botAccess = { !StallMerchant.recentTheft(this, ThievingStallType.FUR, 1000) }

    sell {
        948 x 3 every 100 // Bear fur
        958 x 3 every 100 // Grey wolf fur
    }
}

npc1(573) { StallMerchant.open(plr, 573, "Ardougne Fur Stall.", ThievingStallType.FUR, 1000) }
npc2(573) { StallMerchant.open(plr, 573, "Ardougne Fur Stall.", ThievingStallType.FUR, 1000) }

// Lost City inventory: topshop.
ShopHandler.create("Zenesha's Plate Mail Body Shop.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.6, 0.02, 1.0)
    botAccess = { true }

    sell {
        1117 x 3 every 100 // Bronze platebody
        1115 x 1 every 200 // Iron platebody
        1119 x 1 every 400 // Steel platebody
        1125 x 1 every 3000 // Black platebody
        1121 x 1 every 3000 // Mithril platebody
    }

    open {
        npc2 += 589
    }
}

npc1(589) { GeneralStore.open(plr, "Zenesha's Plate Mail Body Shop.") }

// Lost City inventory: farming_shop_3.
ShopHandler.create("Richard's Farming shop.") {
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
        npc2 += 2306
    }
}

npc1(2306) { GeneralStore.open(plr, "Richard's Farming shop.") }
