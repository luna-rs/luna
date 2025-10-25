package game.skill.slayer

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Create the slayer equipment shop.
ShopHandler.create("Slayer Equipment") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.DEFAULT
    currency = Currency.COINS

    sell {
        "Enchanted gem" x 50
        "Mirror shield" x 100
        "Leaf-bladed spear" x 50
        "Bag of salt" x 5000
        "Rock hammer" x 50
        "Facemask" x 50
        "Earmuffs" x 50
        "Nose peg" x 50
        "Slayer's staff" x 50
        "Spiny helmet" x 50
        "Fishing explosive" x 5000
        "Ice cooler" x 5000
        "Slayer gloves" x 50
        "Unlit bug lantern" x 50
        "Fungicide spray 10" x 50
        "Fungicide" x 5000
    }
}