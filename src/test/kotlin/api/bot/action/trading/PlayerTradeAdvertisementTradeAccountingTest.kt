package api.bot.action.trading

import api.bot.action.trading.PlayerTradeAdvertisementMarket.Opportunity
import api.bot.action.trading.PlayerTradeAdvertisementPayment.Plan
import api.bot.script.InventoryProductionFixtures
import api.predef.itemDef
import game.bot.scripts.trading.TradeBotScript
import io.luna.game.model.item.Item
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import io.luna.net.msg.out.GameChatboxMessageWriter
import org.mockito.Mockito.*

/**
 * Exercises the live trade script's payment recap, lot ratios and cumulative marketplace spending boundaries.
 * No trade-screen items are transferred and no server or network is started by these local regression checks.
 *
 * @author lare96
 */
class PlayerTradeAdvertisementTradeAccountingTest {

    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private lateinit var fixture: AdvertisementRuntimeFixture

    @BeforeEach fun initialize() {
        fixture = AdvertisementRuntimeFixture()
    }

    @AfterEach fun restoreWorldLookups() {
        fixture.close()
    }

    @Test fun paymentAlreadyOnTheOfferScreenRemainsAvailableWhenThePlayerChangesQuantity() {
        val seller = fixture.human()
        val buyer = fixture.trader(seller, coins = 100)
        fixture.publish(seller, "selling 100 feathers 1 gp ea")
        val context = fixture.market.claim(buyer)!!
        assertTrue(buyer.bot.inventory.remove(Item(995, 100)))

        assertNull(replan(buyer, context, mapOf(314 to 100), emptyList()))
        val stable = replan(buyer, context, mapOf(314 to 100), listOf(Item(995, 100)))!!
        val reduced = replan(buyer, context, mapOf(314 to 75), listOf(Item(995, 100)))!!

        assertEquals(mapOf(995 to 100), stable.payment)
        assertEquals(100, stable.quantity)
        assertEquals(mapOf(995 to 75), reduced.payment)
        assertEquals(75, reduced.quantity)
        assertEquals(mapOf(995 to 75), physicalPayment(buyer, context, reduced.payment, listOf(Item(995, 100))))
    }

    @Test fun remainingStockKeepsTheOriginalTotalPriceDenominatorAndRoundsOnlyThePurchasedLot() {
        val seller = fixture.human()
        val first = fixture.trader(seller, coins = 1_000)
        val entry = fixture.publish(seller, "selling 100 feathers 70 gp total")
        val original = entry.offers.single()
        val old = fixture.market.registry.claim(entry, first.bot, original)!!
        assertTrue(fixture.market.registry.recordCompleted(old, mapOf(original to 25)))
        assertTrue(fixture.market.registry.release(old))
        val second = fixture.trader(seller, coins = 1_000)
        fixture.publish(seller, "selling 100 feathers 70 gp total")

        val context = fixture.market.claim(second)!!
        val fullRemainder = replan(second, context, mapOf(314 to 75), emptyList())!!
        val smallerLot = replan(second, context, mapOf(314 to 10), emptyList())!!

        assertSame(entry, context.entry)
        assertEquals(100, context.offer.quantity)
        assertEquals(75, entry.remainingQuantity(original))
        assertEquals(75, fullRemainder.quantity)
        assertEquals(53L, fullRemainder.cost)
        assertEquals(7L, smallerLot.cost)
        assertEquals(70L, context.pricing!!.quote.price.coins)
    }

    @Test fun aFundedSellerReservesItsCarriedBuyingAllowanceOnceRatherThanOnEveryPoll() {
        val seller = fixture.human()
        val buyer = fixture.trader(seller, target = 1_000, coins = 1_000, purpose = BotTradePurpose.SELL)
        val entry = fixture.publish(seller, "selling 1000 feathers 1 gp ea")

        val first = buyer.advertisementCandidate(fixture.market, entry, entry.offers.single())!!
        val second = buyer.advertisementCandidate(fixture.market, entry, entry.offers.single())!!

        assertEquals(mapOf(995 to 800), first.allowance)
        assertEquals(first.allowance, second.allowance)
        assertEquals(800, first.plan!!.quantity)
        assertEquals(800, second.plan!!.quantity)
        assertEquals(1_000, buyer.bot.inventory.computeAmountForId(995))
    }

    @Test fun completedCuriosityPurchasesReduceTheWholeVisitAllowanceExactlyOnce() {
        val seller = fixture.human()
        val buyer = fixture.trader(seller, coins = 1_009, specific = false)
        fixture.set(buyer, "curiousVisit\$delegate", lazyOf(true))
        fixture.publish(seller, "selling 60 feathers 1 gp ea")
        val first = fixture.market.claim(buyer)!!
        assertTrue(first.unexpected)
        assertEquals(60L, first.plan!!.paidValue)

        complete(buyer, first)
        complete(buyer, first)
        fixture.market.release(first)
        fixture.publish(seller, "selling 100 feathers 1 gp ea")
        val next = fixture.market.claim(buyer)!!

        assertTrue(next.unexpected)
        assertEquals(40, next.plan!!.quantity)
        assertEquals(40L, next.plan!!.paidValue)
        assertEquals(40L, field(buyer, "curiosityRemaining"))
    }

    @Test fun anOpenPurchaseStillChargesItsCuriosityBudgetAfterItsAdvertisementExpires() {
        val seller = fixture.human()
        val buyer = fixture.trader(seller, coins = 1_000, specific = false)
        fixture.set(buyer, "curiousVisit\$delegate", lazyOf(true))
        val entry = fixture.publish(seller, "selling 60 feathers 1 gp ea")
        val context = fixture.market.claim(buyer)!!
        fixture.now = fixture.now.plusSeconds(60)
        assertNull(fixture.market.registry.find(seller))

        complete(buyer, context)

        assertEquals(40L, field(buyer, "curiosityRemaining"))
        assertEquals(60, entry.remainingQuantity(context.offer))
        assertTrue(context.completed)
    }

    @Test fun aCurrentGenuineNeedCanSpendBeyondTheRemainingCuriosityAllowance() {
        val seller = fixture.human()
        val buyer = fixture.trader(seller, target = 150, coins = 1_000, specific = false)
        BotTradeIntentions.recordSupplies(buyer.bot, listOf(Item(314, 150)), stockTarget = 150)
        fixture.set(buyer, "curiosityRemaining", 0L)
        fixture.publish(seller, "selling 150 feathers 1 gp ea")

        val context = fixture.market.claim(buyer)!!

        assertFalse(context.unexpected)
        assertEquals(150, context.plan!!.quantity)
        assertEquals(150L, context.plan!!.paidValue)
        complete(buyer, context)
        assertEquals(0L, field(buyer, "curiosityRemaining"))
    }

    @Test fun actualUnnotedGoodsAreCappedByNativeSlotsWhileTheRemainingCoinStackKeepsItsSlot() {
        val seller = fixture.human()
        val buyer = fixture.trader(seller, id = 1779, target = 1_000, coins = 1_000)
        fixture.publish(seller, "selling 1000 flax 1 gp ea")
        val context = fixture.market.claim(buyer)!!
        assertEquals(1_000, context.plan!!.quantity)

        val raw = replan(buyer, context, mapOf(1779 to 100), emptyList())!!

        assertEquals(27, raw.quantity)
        assertEquals(mapOf(995 to 27), raw.payment)
        assertEquals(1_000, buyer.bot.inventory.computeAmountForId(995))
        assertEquals(0, buyer.bot.inventory.computeAmountForId(1779))
    }

    @Test fun actualNotesAndStackablesCanStillBePurchasedInBulkInsteadOfBeingCappedAtRawInventorySize() {
        val seller = fixture.human()
        val buyer = fixture.trader(seller, id = 1779, target = 1_000, coins = 2_000)
        fixture.publish(seller, "selling 1000 flax 1 gp ea")
        val context = fixture.market.claim(buyer)!!
        val noted = itemDef(1779).notedId.orElseThrow()

        assertEquals(1_000, replan(buyer, context, mapOf(noted to 1_000), emptyList())!!.quantity)

        fixture.market.release(context)
        val featherBuyer = fixture.trader(seller, target = 1_000, coins = 2_000)
        fixture.publish(seller, "selling 1000 feathers 1 gp ea")
        val feathers = fixture.market.claim(featherBuyer)!!
        assertEquals(1_000, replan(featherBuyer, feathers, mapOf(314 to 1_000), emptyList())!!.quantity)
    }

    @Test fun spendingAnEntirePaymentStackCanFreeTheLastSlotForOneUnnotedPurchase() {
        val seller = fixture.human()
        val buyer = fixture.trader(seller, id = 1779, target = 1, coins = 1)
        InventoryProductionFixtures.inventory(buyer.bot, Item(590, 27))
        assertEquals(0, buyer.bot.inventory.computeRemainingSize())
        fixture.publish(seller, "selling 1 flax 1 gp ea")
        val context = fixture.market.claim(buyer)!!

        val plan = replan(buyer, context, mapOf(1779 to 1), emptyList())!!

        assertEquals(1, plan.quantity)
        assertEquals(mapOf(995 to 1), plan.payment)
        assertEquals(0, buyer.bot.inventory.computeRemainingSize())
        assertEquals(1, buyer.bot.inventory.computeAmountForId(995))
    }

    @Test fun aFullInventoryWithOnlyPartialPaymentAvailableDoesNotClaimAnUnreceivablePurchase() {
        val seller = fixture.human()
        val buyer = fixture.trader(seller, id = 1779, target = 1, coins = 2)
        InventoryProductionFixtures.inventory(buyer.bot, Item(590, 27))
        fixture.publish(seller, "selling 1 flax 1 gp ea")

        assertFalse(fixture.market.hasPriority(buyer))
        assertNull(fixture.market.claim(buyer))
        assertEquals(2, buyer.bot.inventory.computeAmountForId(995))
    }

    @Test fun anExistingIncomingStackLimitsThePurchaseBeforeItsNativeIntegerQuantityWouldOverflow() {
        val seller = fixture.human()
        val buyer = generateSequence {
            fixture.trader(seller, coins = 1_000, purpose = BotTradePurpose.MERCHANT_BUY, specific = false)
        }.onEach { fixture.audience.remove(it.bot) }.take(100)
            .first { BotMerchantSpecialty.forBot(it.bot) == BotMerchantSpecialty.GENERAL }
        fixture.audience += buyer.bot
        InventoryProductionFixtures.inventory(buyer.bot, Item(314, Int.MAX_VALUE - 5))
        fixture.publish(seller, "selling 100 feathers 1 gp ea")

        val context = fixture.market.claim(buyer)!!

        assertEquals(5, context.plan!!.quantity)
        assertEquals(5, replan(buyer, context, mapOf(314 to 100), emptyList())!!.quantity)
        assertEquals(Int.MAX_VALUE - 5, buyer.bot.inventory.computeAmountForId(314))
    }

    @Test fun aQuantityUpdateUsesTheFeasibleMaximumInsteadOfThePlayersOneItemIncrementAndDoesNotBounce() {
        val seller = fixture.human()
        val buyer = fixture.trader(seller, id = 1779, target = 100, coins = 1_000)
        fixture.publish(seller, "selling 1000 flax 1 gp ea")
        val context = fixture.market.claim(buyer)!!
        val notify = TradeBotScript::class.java.getDeclaredMethod("notifyAdvertisementQuantity",
            Opportunity::class.java, Plan::class.java).apply { isAccessible = true }
        notify.invoke(buyer, context, context.plan!!)
        assertEquals(100, context.lastNotifiedQuantity)

        assertEquals(1, replan(buyer, context, mapOf(1779 to 1), emptyList())!!.quantity)
        val rawMaximum = maximum(buyer, context, mapOf(1779 to 1))!!
        assertEquals(27, rawMaximum.quantity)
        notify.invoke(buyer, context, rawMaximum)
        notify.invoke(buyer, context, maximum(buyer, context, mapOf(1779 to 2))!!)
        notify.invoke(buyer, context, maximum(buyer, context, mapOf(itemDef(1779).notedId.orElseThrow() to 1))!!)
        notify.invoke(buyer, context, rawMaximum)

        val messages = ArgumentCaptor.forClass(GameChatboxMessageWriter::class.java)
        verify(seller, times(2)).queue(messages.capture())
        assertEquals(listOf(100, 27).map {
            "${buyer.bot.username}:@blu@${buyer.bot.username} wants up to $it ${itemDef(1779).name}.@bla@:tradeinfo:"
        }, messages.allValues.map { it.message })
        assertTrue(context.quantityShrinkNotified)
        assertEquals(27, context.lastNotifiedQuantity)
    }

    private fun replan(script: TradeBotScript, context: Opportunity, receiving: Map<Int, Int>,
                       offered: Collection<Item>): Plan? =
        TradeBotScript::class.java.getDeclaredMethod("advertisementPlan", Opportunity::class.java,
            Map::class.java, Collection::class.java).apply { isAccessible = true }
            .invoke(script, context, receiving, offered) as Plan?

    private fun maximum(script: TradeBotScript, context: Opportunity, receiving: Map<Int, Int>): Plan? =
        TradeBotScript::class.java.getDeclaredMethod("advertisementMaximumPlan", Opportunity::class.java,
            Map::class.java, Collection::class.java).apply { isAccessible = true }
            .invoke(script, context, receiving, emptyList<Item>()) as Plan?

    @Suppress("UNCHECKED_CAST")
    private fun physicalPayment(script: TradeBotScript, context: Opportunity, selected: Map<Int, Int>,
                                offered: Collection<Item>): Map<Int, Int> =
        TradeBotScript::class.java.getDeclaredMethod("advertisementPhysicalPayment", Opportunity::class.java,
            Map::class.java, Collection::class.java).apply { isAccessible = true }
            .invoke(script, context, selected, offered) as Map<Int, Int>

    private fun complete(script: TradeBotScript, context: Opportunity) {
        TradeBotScript::class.java.getDeclaredMethod("completeAdvertisement", Opportunity::class.java,
            Plan::class.java).apply { isAccessible = true }.invoke(script, context, context.plan!!)
    }

    private fun field(script: TradeBotScript, name: String): Any? =
        TradeBotScript::class.java.getDeclaredField(name).apply { isAccessible = true }.get(script)
}
