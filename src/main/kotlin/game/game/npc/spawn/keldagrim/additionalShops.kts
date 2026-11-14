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
package game.npc.spawn.keldagrim

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import game.npc.spawn.StallMerchant
import game.skill.thieving.stealFromStall.ThievingStallType
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: keldagrim_warhammer_shop.
ShopHandler.create("Vigr's Warhammers") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        1337 x 5 every 100 // Bronze warhammer
        1335 x 4 every 200 // Iron warhammer
        1339 x 3 every 400 // Steel warhammer
        1341 x 3 every 600 // Black warhammer
        1343 x 2 every 800 // Mithril warhammer
        1345 x 1 every 1000 // Addy warhammer
    }

    open {
        npc2 += 2151
    }
}

npc1(2151) { GeneralStore.open(plr, "Vigr's Warhammers") }

// Lost City inventory: keldagrim_quality_weapons_shop.
ShopHandler.create("Quality Weapons Shop") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        1365 x 2 every 200 // Steel battleaxe
        1369 x 1 every 3000 // Mithril battleaxe
        1285 x 3 every 3000 // Mithril sword
        1287 x 2 every 12000 // Adamant sword
        1325 x 2 every 600 // Steel scimitar
        1297 x 2 every 1500 // Black longsword
        1303 x 1 every 4000 // Rune longsword
        853 x 1 every 100 // Maple shortbow
        851 x 1 every 100 // Maple longbow
        888 x 800 every 15 // Mithril arrow
        890 x 600 every 20 // Adamant arrow
    }

    open {
        npc2 += 2152
    }
}

npc1(2152) { GeneralStore.open(plr, "Quality Weapons Shop") }

// Lost City inventory: keldagrim_quality_armour_shop.
ShopHandler.create("Quality Armour Shop") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        1105 x 3 every 400 // Steel chainbody
        1109 x 1 every 3000 // Mithril chainbody
        1107 x 1 every 2500 // Black chainbody
        1111 x 1 every 9000 // Adamant chainbody
        1141 x 3 every 400 // Steel med helm
        1143 x 1 every 3000 // Mithril med helm
        1145 x 1 every 12000 // Adamant med helm
        1177 x 0 every 1500 // Steel sq shield
        1195 x 0 every 1500 // Black kiteshield
    }

    open {
        npc2 += 2153
    }
}

npc1(2153) { GeneralStore.open(plr, "Quality Armour Shop") }

// Lost City inventory: keldagrim_bread_stall.
ShopHandler.create("Randivor's Bakery") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { !StallMerchant.recentTheft(this, ThievingStallType.BAKERY, 1500) }

    sell {
        2309 x 10 every 100 // Bread
        1891 x 3 every 800 // Cake
        1901 x 8 every 200 // Chocolate slice
    }
}

npc1(2156) { StallMerchant.open(plr, 2156, "Randivor's Bakery", ThievingStallType.BAKERY, 1500) }
npc2(2156) { StallMerchant.open(plr, 2156, "Randivor's Bakery", ThievingStallType.BAKERY, 1500) }

// Lost City inventory: keldagrim_silver_stall.
ShopHandler.create("Silver Cog Silver Stall") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { !StallMerchant.recentTheft(this, ThievingStallType.SILVER, 1000) }

    sell {
        1714 x 2 every 100 // Unstrung symbol
        442 x 1 every 2000 // Silver ore
        2355 x 1 every 1000 // Silver bar
    }
}

npc1(2159) { StallMerchant.open(plr, 2159, "Silver Cog Silver Stall", ThievingStallType.SILVER, 1000) }
npc2(2159) { StallMerchant.open(plr, 2159, "Silver Cog Silver Stall", ThievingStallType.SILVER, 1000) }

// Lost City inventory: keldagrim_gem_stall.
ShopHandler.create("Green Gemstone Gems") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    merchantsAccessOnly(strict = true) { !StallMerchant.recentTheft(this, ThievingStallType.GEM, 1000) }

    sell {
        1607 x 3 every 15000 // Sapphire
        1605 x 1 every 1000 // Emerald
        1603 x 1 every 2000 // Ruby
        1601 x 0 every 4000 // Diamond
    }
}

npc1(2157) { StallMerchant.open(plr, 2157, "Green Gemstone Gems", ThievingStallType.GEM, 1000) }
npc2(2157) { StallMerchant.open(plr, 2157, "Green Gemstone Gems", ThievingStallType.GEM, 1000) }

// Lost City inventory: keldagrim_crafting_stall.
ShopHandler.create("Carefree Crafting Stall") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        1755 x 2 every 100 // Chisel
        1592 x 4 every 100 // Ring mould
        1597 x 2 every 100 // Necklace mould
        1733 x 3 every 100 // Needle
        1734 x 100 every 5 // Thread
        1759 x 100 every 5 // Ball of wool
    }

    open {
        npc2 += 2158
    }
}

npc1(2158) { GeneralStore.open(plr, "Carefree Crafting Stall") }

// Lost City inventory: keldagrim_clothes_stall.
ShopHandler.create("Vermundi's Clothes Stall") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        5048 x 3 every 40 // Skirt
        5036 x 3 every 30 // Trousers
        5042 x 3 every 25 // Shorts
        5024 x 3 every 45 // Woven top
        5030 x 3 every 40 // Shirt
        950 x 5 every 100 // Silk
    }

    open {
        npc2 += 2162
    }
}

npc1(2162) { GeneralStore.open(plr, "Vermundi's Clothes Stall") }

// Lost City inventory: keldagrim_clothes_shop.
ShopHandler.create("Agmundi's Quality Clothes") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        5050 x 3 every 50 // Skirt
        5052 x 3 every 50 // Skirt
        5038 x 3 every 40 // Trousers
        5040 x 3 every 40 // Trousers
        5044 x 3 every 35 // Shorts
        5046 x 3 every 35 // Shorts
        5026 x 3 every 55 // Woven top
        5028 x 3 every 55 // Woven top
        5032 x 3 every 50 // Shirt
        5034 x 3 every 50 // Shirt
    }

    open {
        npc2 += 2161
    }
}

npc1(2161) { GeneralStore.open(plr, "Agmundi's Quality Clothes") }

// Lost City inventory: keldagrim_pickaxe_shop.
ShopHandler.create("Pickaxe-Is-Mine") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        1265 x 6 every 50 // Bronze pickaxe
        1269 x 4 every 200 // Steel pickaxe
        1273 x 3 every 300 // Mithril pickaxe
        1271 x 2 every 500 // Adamant pickaxe
        1275 x 1 every 600 // Rune pickaxe
    }

    open {
        npc2 += 2160
    }
}

npc1(2160) { GeneralStore.open(plr, "Pickaxe-Is-Mine") }

// Lost City inventory: smithing_guild_ore_seller.
ShopHandler.create("Ore Seller") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        436 x 100 every 200 // Copper ore
        438 x 100 every 200 // Tin ore
        440 x 100 every 200 // Iron ore
        447 x 100 every 200 // Mithril ore
        442 x 100 every 200 // Silver ore
        444 x 100 every 200 // Gold ore
        453 x 100 every 200 // Coal
    }

    open {
        npc2 += 2564
    }
}

npc1(2564) { GeneralStore.open(plr, "Ore Seller") }

// Lost City inventory: smithing_guild_buyer.
ShopHandler.create("Jorzik's Armour Shop") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        1147 x 0 every 500 // Rune med helm
        1163 x 0 every 500 // Rune full helm
        1127 x 0 every 500 // Rune platebody
        1079 x 0 every 500 // Rune platelegs
        1093 x 0 every 500 // Rune plateskirt
        1185 x 0 every 500 // Rune sq shield
        1201 x 0 every 500 // Rune kiteshield
        1113 x 0 every 500 // Rune chainbody
        1145 x 0 every 500 // Adamant med helm
        1161 x 0 every 500 // Adamant full helm
        1123 x 0 every 500 // Adamant platebody
        1073 x 0 every 500 // Adamant platelegs
        1091 x 0 every 500 // Adamant plateskirt
        1183 x 0 every 500 // Adamant sq shield
        1199 x 0 every 500 // Adamant kiteshield
        1111 x 0 every 500 // Adamant chainbody
        1143 x 0 every 500 // Mithril med helm
        1159 x 0 every 500 // Mithril full helm
        1121 x 0 every 500 // Mithril platebody
        1071 x 0 every 500 // Mithril platelegs
        1085 x 0 every 500 // Mithril plateskirt
        1181 x 0 every 500 // Mithril sq shield
        1197 x 0 every 500 // Mithril kiteshield
        1109 x 0 every 500 // Mithril chainbody
    }

    open {
        npc2 += 2565
    }
}

npc1(2565) { GeneralStore.open(plr, "Jorzik's Armour Shop") }
