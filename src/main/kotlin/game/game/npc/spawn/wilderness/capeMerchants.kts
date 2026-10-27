package game.npc.spawn.wilderness

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.Player

/**
 * The ten original Wilderness cape merchants. Their initials spell WILDERNESS, and each sells the five capes
 * whose numbers end in his position in that word (Sam sells 10, 20, 30, 40 and 50).
 *
 * Original stock is 100 of each cape, starting at 50 coins to buy / 30 to sell, with a 1% stock price change.
 * Stock returns toward its original amount by one cape every 100 ticks, including excess capes sold by players.
 */
val merchants = listOf("William", "Ian", "Larry", "Darren", "Edward", "Richard", "Neil", "Edmond", "Simon", "Sam")

merchants.forEachIndexed { index, name ->
    val npcId = 1778 + index
    val shopName = "$name's Wilderness Cape Shop."
    ShopHandler.create(shopName) {
        buy = BuyPolicy.EXISTING
        currency = Currency.COINS
        restock = RestockPolicy.BALANCED
        stockPricing = Shop.StockPricing(0.60, 0.01)

        sell {
            for (number in index + 1..50 step 10) {
                // Odd IDs are the wearable capes; the even IDs are their banknotes.
                (4313 + number * 2) x 100 every 100
            }
        }

        // Trade is the third action in the 377 NPC definitions (the second slot is empty).
        open { npc3 += npcId }
    }

    npc1(npcId) {
        plr.newDialogue()
            .npc(npcId, "Would you like to buy a Wilderness cape?")
            .then { offerCapes(it, npcId, shopName) }
            .open()
    }
}

/**
 * Buying, declining, and asking how team capes work all return through the same merchant's shop.
 */
fun offerCapes(player: Player, npcId: Int, shopName: String) {
    player.newDialogue()
        .options("What's so special about your capes?", {
            it.newDialogue()
                .player("What's so special about your capes?")
                .npc(npcId, "Matching capes help you recognise your team on the", "minimap and avoid attacking them by accident.")
                .npc(npcId, "They're useful for travelling through the Wilderness", "with your friends. Would you like to see them?")
                .then { plr -> offerCapes(plr, npcId, shopName) }
                .open()
        }, "Yes please!", {
            it.overlays.open(ShopInterface(world, shopName))
        }, "No thanks.", {
            it.newDialogue().player("No thanks.").open()
        })
        .open()
}
