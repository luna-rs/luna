/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.lighthouse

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.Position
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Lost City inventory: lighthouseshop.
ShopHandler.create("Lighthouse Store") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { position.isWithinDistance(Position(2509, 3639, 1), 64) }

    sell {
        954 x 10 every 200 // Rope
        2347 x 10 every 500 // Hammer
        1755 x 10 every 500 // Chisel
        946 x 10 every 500 // Knife
        952 x 10 every 500 // Spade
        590 x 5 every 500 // Tinderbox
        36 x 10 every 500 // Candle
        273 x 10 every 200 // Poison
        233 x 10 every 500 // Pestle and mortar
        1931 x 10 every 500 // Pot
        1925 x 5 every 500 // Bucket
        1929 x 5 every 500 // Bucket of water
        1935 x 5 every 200 // Jug
        1937 x 5 every 500 // Jug of water
        229 x 100 every 500 // Vial
        227 x 100 every 500 // Vial of water
        2019 x 5 every 500 // Gin
        2021 x 5 every 500 // Brandy
        2015 x 5 every 500 // Vodka
        1915 x 5 every 500 // Grog
        2017 x 5 every 500 // Whisky
        1909 x 5 every 500 // Greenman's ale
        1913 x 5 every 500 // Dwarven stout
        1907 x 5 every 500 // Wizard's mind bomb
    }

    open {
        npc2 += 1334
    }
}

npc1(1334) { GeneralStore.open(plr, "Lighthouse Store") }
