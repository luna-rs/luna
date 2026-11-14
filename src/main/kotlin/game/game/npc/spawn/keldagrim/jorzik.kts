/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.keldagrim

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

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
