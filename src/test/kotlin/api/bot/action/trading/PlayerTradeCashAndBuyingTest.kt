package api.bot.action.trading

import api.bot.script.InventoryProductionFixtures
import game.bot.scripts.trading.TradeBotScript
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Verifies cash admission, current funding, and the explicit buying-message exception through prepared traders.
 *
 * @author lare96
 */
class PlayerTradeCashAndBuyingTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private lateinit var fixture: AdvertisementRuntimeFixture
    @BeforeEach fun initialize() { fixture = AdvertisementRuntimeFixture() }
    @AfterEach fun restore() { fixture.close() }

    @Test fun cashlessBuyersCannotRespondToSalesEvenWithBankedGoldAndValuableItems() {
        val player = fixture.human()
        val trader = fixture.trader(player, coins = 0)
        InventoryProductionFixtures.inventory(trader.bot, Item(556, 3))
        InventoryProductionFixtures.bank(trader.bot, Item(995, 10_000))
        fixture.set(trader, "carriedOffer", mapOf(556 to 3))
        fixture.set(trader, "offering", listOf(BotTradeItem(Item(556, 3), 10, BotTradePurpose.BUY, trader.bot)))
        val entry = fixture.publish(player, "selling 100 feathers 1 gp ea")

        assertNull(trader.advertisementCandidate(fixture.market, entry, entry.offers.single()))
        assertFalse(fixture.market.hasPriority(trader))
        val eligible = TradeBotScript::class.java.getDeclaredMethod("eligibleTradePartner", Player::class.java)
            .apply { isAccessible = true }
        assertEquals(false, eligible.invoke(trader, player))
        assertTrue(target(trader, mapOf(314 to 25)).isEmpty())
    }

    @Test fun partialCashCannotBeSupplementedWithSurplusItemsAndIsRecheckedBeforeClaiming() {
        val player = fixture.human()
        val trader = fixture.trader(player, coins = 10)
        InventoryProductionFixtures.inventory(trader.bot, Item(556, 3))
        fixture.set(trader, "carriedOffer", mapOf(995 to 10, 556 to 3))
        fixture.set(trader, "offering", listOf(
            BotTradeItem(Item(995, 10), 1, BotTradePurpose.BUY, trader.bot),
            BotTradeItem(Item(556, 3), 10, BotTradePurpose.BUY, trader.bot)))
        val entry = fixture.publish(player, "selling 100 feathers 1 gp ea")
        val candidate = trader.advertisementCandidate(fixture.market, entry, entry.offers.single())!!

        assertEquals(10, candidate.plan?.quantity)
        assertEquals(mapOf(995 to 10), candidate.plan?.payment)
        assertEquals(mapOf(995 to 10), target(trader, mapOf(314 to 10)))
        assertTrue(trader.bot.inventory.remove(Item(995, 10)))
        assertNull(fixture.market.claim(trader))
        assertEquals(3, trader.bot.inventory.computeAmountForId(556))
    }

    @Test fun cashlessSellersOfferOnlyTheExplicitRequestedCommodityAndRespectTheQuantity() {
        val player = fixture.human()
        val trader = fixture.trader(player, coins = 0, purpose = BotTradePurpose.SELL)
        InventoryProductionFixtures.inventory(trader.bot, Item(314, 50), Item(556, 100))
        fixture.set(trader, "carriedOffer", mapOf(314 to 50, 556 to 100))
        fixture.set(trader, "offering", listOf(
            BotTradeItem(Item(314, 50), 1, BotTradePurpose.SELL, trader.bot),
            BotTradeItem(Item(556, 100), 10, BotTradePurpose.SELL, trader.bot)))
        val entry = fixture.publish(player, "buying 25 feathers 1 gp ea")

        val candidate = fixture.market.claim(trader)!!

        assertEquals(PlayerTradeAdvertisement.Direction.BUY, candidate.offer.direction)
        assertEquals(mapOf(314 to 25), candidate.plan?.payment)
        assertEquals(25, candidate.plan?.quantity)
        assertEquals(25L, candidate.plan?.cost)
        assertEquals(25, entry.remainingQuantity(candidate.offer))
        assertEquals(100, trader.bot.inventory.computeAmountForId(556))
    }

    @Test fun sellingTradersCanBuyAPlayersSaleUsingTheirActualCarriedGoldOnly() {
        val player = fixture.human()
        val trader = fixture.trader(player, coins = 100, purpose = BotTradePurpose.SELL)
        InventoryProductionFixtures.inventory(trader.bot, Item(1779, 2))
        fixture.set(trader, "carriedOffer", mapOf(1779 to 2))
        fixture.set(trader, "offering", listOf(BotTradeItem(Item(1779, 2), 25, BotTradePurpose.SELL, trader.bot)))
        fixture.publish(player, "selling 100 feathers 1 gp ea")

        val candidate = fixture.market.claim(trader)!!

        assertEquals(80, candidate.plan?.quantity)
        assertEquals(mapOf(995 to 80), candidate.plan?.payment)
        assertEquals(2, trader.bot.inventory.computeAmountForId(1779))
    }

    @Test fun unrelatedStockCannotRespondToABuyingMessageAndDirectionChangesReplaceTerms() {
        val player = fixture.human()
        val trader = fixture.trader(player, coins = 0, purpose = BotTradePurpose.SELL)
        InventoryProductionFixtures.inventory(trader.bot, Item(556, 100))
        fixture.set(trader, "carriedOffer", mapOf(556 to 100))
        fixture.set(trader, "offering", listOf(BotTradeItem(Item(556, 100), 10, BotTradePurpose.SELL, trader.bot)))
        val buying = fixture.publish(player, "buying 25 feathers 1 gp ea")
        assertNull(fixture.market.claim(trader))
        val selling = fixture.publish(player, "selling 25 feathers 1 gp ea")
        assertNotSame(buying, selling)
        assertNull(fixture.market.claim(trader))
    }

    @Test fun liquidMerchantAsksAvoidASecondDiscountButStillRespectTheBuyingCeiling() {
        val offer = PlayerTradeAdvertisement("flax", setOf(1779), 100,
            PlayerTradeAdvertisement.Price(90, PlayerTradeAdvertisement.PriceBasis.EACH))
        val profile = PlayerTradeAdvertisementPolicy.Profile(merchant = true)
        val quote = PlayerTradeAdvertisementPolicy.quote(offer, 100, profile, 0.0, 1.0)!!
        assertEquals(90L, quote.price.coins)
        assertFalse(quote.counteroffer)
        val counter = PlayerTradeAdvertisementPolicy.quote(offer, 80, profile, 0.0, 1.0)!!
        assertEquals(80L, counter.price.coins)
        assertTrue(counter.counteroffer)
    }

    @Suppress("UNCHECKED_CAST")
    private fun target(trader: TradeBotScript, receiving: Map<Int, Int>): Map<Int, Int> =
        TradeBotScript::class.java.getDeclaredMethod("humanTarget", Map::class.java, Map::class.java)
            .apply { isAccessible = true }.invoke(trader, receiving, HashMap<Int, Long>()) as Map<Int, Int>
}
