package game.skill.slayer

import api.bot.zone.SubZone
import api.shop.dsl.ShopHandler
import io.luna.game.model.area.SimpleBoxArea
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Create the slayer equipment shop.
ShopHandler.create("Slayer Equipment") {
    botAccess {
        npcSeller(4, SlayerMaster.TURAEL.id, subZone = SubZone.TURAEL)
        npcSeller(4, SlayerMaster.MAZCHNA.id, subZone = SubZone.CANIFIS_MAIN)
        npcSeller(4, SlayerMaster.VANNAKA.id, subZone = SubZone.EDGEVILLE_DUNGEON)
        npcSeller(4, SlayerMaster.CHAELDAR.id, subZone = SubZone.ZANARIS_MAIN)
        npcSeller(4, SlayerMaster.DURADEL.id, area = SimpleBoxArea.of(2864, 2977, 2874, 2987, 1))
    }
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.DEFAULT
    currency = Currency.COINS

    sell {
        "Enchanted gem" x 50
        "Mirror shield" x 100
        "Leaf-bladed spear" x 50
        4160 x 50000 // Broad arrows
        "Bag of salt" x 5000
        "Rock hammer" x 50
        "Facemask" x 50
        "Earmuffs" x 50
        "Nose peg" x 50
        "Slayer's staff" x 50
        "Spiny helmet" x 50
        "Fishing explosive" x 5000
        "Ice cooler" x 5000
        6720 x 50 // Wearable Slayer gloves; the same name also belongs to the skill-guide icon.
        "Unlit bug lantern" x 50
        "Fungicide spray 10" x 50
        "Fungicide" x 5000
    }
}
