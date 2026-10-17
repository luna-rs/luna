package game.bot.scripts.trading

import api.bot.action.trading.*
import api.bot.script.InventoryProductionFixtures
import engine.trade.ConfirmTradeInterface
import engine.trade.OfferTradeInterface
import io.luna.game.model.EntityState
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.OptionalInt
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.time.Duration.Companion.minutes

class TradeBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() {
            InventoryProductionFixtures.initialize()
            for (id in listOf(3323, 3321, 3443, 3213)) {
                if (WidgetDefinition.ALL.get(id).isEmpty) {
                    val widget = mock(WidgetDefinition::class.java)
                    `when`(widget.id()).thenReturn(id)
                    WidgetDefinition.ALL.storeDefinition(widget)
                }
            }
            if (ItemDefinition.ALL.get(1512).isEmpty) {
                val note = mock(ItemDefinition::class.java)
                `when`(note.id()).thenReturn(1512)
                `when`(note.unnotedId).thenReturn(OptionalInt.of(1511))
                ItemDefinition.ALL.storeDefinition(note)
            }
        }

        private fun set(script: TradeBotScript, name: String, value: Any) {
            TradeBotScript::class.java.getDeclaredField(name).apply { isAccessible = true }.set(script, value)
        }

        private fun call(script: TradeBotScript, name: String, vararg args: Any): Any? {
            val method = TradeBotScript::class.java.declaredMethods.single { it.name == name }
            method.isAccessible = true
            return method.invoke(script, *args)
        }

        private fun script(purpose: BotTradePurpose, stock: Map<Int, Int>, id: Int = 314,
                           amount: Int = 100, price: Long = 1): TradeBotScript {
            val bot = InventoryProductionFixtures.bot()
            `when`(bot.username).thenReturn("trade_fixture_${System.identityHashCode(bot)}")
            `when`(bot.overlays.player).thenReturn(bot)
            `when`(bot.overlays.getOverlay(OfferTradeInterface::class.java)).thenReturn(null)
            `when`(bot.overlays.getOverlay(ConfirmTradeInterface::class.java)).thenReturn(null)
            `when`(bot.state).thenReturn(EntityState.ACTIVE)
            val script = TradeBotScript(bot, purpose, duration = 10.minutes)
            val buying = purpose == BotTradePurpose.BUY || purpose == BotTradePurpose.MERCHANT_BUY
            set(script, "prepared", true)
            set(script, "carriedOffer", stock)
            set(script, "offering", listOf(BotTradeItem(if (buying) Item(995, stock[995] ?: 1) else Item(id, amount),
                if (buying) 1 else price, purpose, bot)))
            set(script, "expected", listOf(BotTradeItem(if (buying) Item(id, amount) else Item(995, 100),
                if (buying) price else 1, purpose, bot)))
            `when`(bot.scriptStack.current()).thenReturn(script)
            return script
        }

        @Suppress("UNCHECKED_CAST")
        private fun exchange(script: TradeBotScript, other: TradeBotScript) =
            call(script, "botExchange", other) as Pair<Map<Int, Int>, Map<Int, Int>>?

        @Suppress("UNCHECKED_CAST")
        private fun target(script: TradeBotScript, received: Map<Int, Int>) =
            call(script, "humanTarget", received, hashMapOf<Int, Long>()) as Map<Int, Int>

        private suspend fun apply(script: TradeBotScript, offer: BotTradeOfferStage, target: Map<Int, Int>): Boolean =
            suspendCoroutineUninterceptedOrReturn { continuation ->
                TradeBotScript::class.java.getDeclaredMethod("applyTarget", BotTradeOfferStage::class.java,
                    Map::class.java, Continuation::class.java).apply { isAccessible = true }
                    .invoke(script, offer, target, continuation)
            }
    }

    @Test fun bothParticipantsAgreeOnPartialPurchaseAtSellerPrice() {
        val buyer = script(BotTradePurpose.BUY, mapOf(995 to 50), price = 2)
        val seller = script(BotTradePurpose.SELL, mapOf(314 to 100))
        assertEquals(mapOf(995 to 50) to mapOf(314 to 50), exchange(buyer, seller))
        assertEquals(mapOf(314 to 50) to mapOf(995 to 50), exchange(seller, buyer))
    }

    @Test fun independentlyConstructedTradeItemsMatchByIdForAllPurposePairs() {
        for (buying in listOf(BotTradePurpose.BUY, BotTradePurpose.MERCHANT_BUY)) {
            for (selling in listOf(BotTradePurpose.SELL, BotTradePurpose.MERCHANT_SELL)) {
                val buyer = script(buying, mapOf(995 to 100))
                val seller = script(selling, mapOf(314 to 100))
                assertEquals(true, call(buyer, "canTradeWith", seller.bot))
                assertEquals(true, call(seller, "canTradeWith", buyer.bot))
            }
        }
    }

    @Test fun matchingUsesActualNoteIdsButAgreesOnUnnotedPricesAndDemand() {
        val buyer = script(BotTradePurpose.BUY, mapOf(995 to 50), id = 1511, price = 2)
        val seller = script(BotTradePurpose.SELL, mapOf(1512 to 100), id = 1511)
        assertEquals(mapOf(995 to 50) to mapOf(1512 to 50), exchange(buyer, seller))
        assertEquals(mapOf(1512 to 50) to mapOf(995 to 50), exchange(seller, buyer))
    }

    @Test fun botPartnersAgreeOnNotedBarterPaymentsWithoutCoins() {
        val buyer = script(BotTradePurpose.BUY, mapOf(1512 to 1))
        val seller = script(BotTradePurpose.SELL, mapOf(314 to 100))
        set(buyer, "offering", listOf(BotTradeItem(Item(1511, 1), 50, BotTradePurpose.SELL, buyer.bot)))
        assertEquals(mapOf(1512 to 1) to mapOf(314 to 50), exchange(buyer, seller))
        assertEquals(mapOf(314 to 50) to mapOf(1512 to 1), exchange(seller, buyer))
    }

    @Test fun noPaymentIncompatiblePurposesAndInsufficientWholeItemFundsAreRejected() {
        val buyer = script(BotTradePurpose.BUY, mapOf(995 to 50), price = 60)
        val seller = script(BotTradePurpose.SELL, mapOf(314 to 1), amount = 1, price = 60)
        assertNull(exchange(buyer, seller))
        assertNull(exchange(buyer, buyer))
        set(buyer, "carriedOffer", emptyMap<Int, Int>())
        assertNull(exchange(buyer, seller))
    }

    @Test fun humanPartialPurchasesUseQuantitiesAndRecoverAfterAnOfferIncreasesAgain() {
        val buyer = script(BotTradePurpose.BUY, mapOf(995 to 100))
        assertEquals(mapOf(995 to 50), target(buyer, mapOf(314 to 50)))
        assertEquals(mapOf(995 to 25), target(buyer, mapOf(314 to 25)))
        assertEquals(mapOf(995 to 75), target(buyer, mapOf(314 to 75)))
        assertTrue(target(buyer, mapOf(1511 to 50)).isEmpty())
    }

    @Test fun humanCoinPaymentsResizeTheOriginalSaleStock() {
        val seller = script(BotTradePurpose.SELL, mapOf(314 to 100))
        assertEquals(mapOf(314 to 50), target(seller, mapOf(995 to 50)))
        assertEquals(mapOf(314 to 25), target(seller, mapOf(995 to 25)))
        assertEquals(mapOf(314 to 75), target(seller, mapOf(995 to 75)))
        assertEquals(mapOf(314 to 100), target(seller, mapOf(995 to 200)))
    }

    @Test fun emptyAndUnrelatedOffersNeverCountAsUsefulPurchases() {
        val buyer = script(BotTradePurpose.BUY, mapOf(995 to 100))
        assertEquals(false, call(buyer, "hasUsefulReturn", emptyMap<Int, Int>(), hashMapOf<Int, Long>()))
        assertEquals(false, call(buyer, "hasUsefulReturn", mapOf(1511 to 50), hashMapOf<Int, Long>()))
        assertEquals(true, call(buyer, "hasUsefulReturn", mapOf(314 to 50), hashMapOf<Int, Long>()))
    }

    @Test fun targetApplicationOnlyAddsTheMissingQuantityAndIsIdempotent(): Unit = runBlocking {
        val script = script(BotTradePurpose.SELL, mapOf(314 to 100))
        val offer = mock(BotTradeOfferStage::class.java)
        var amount = 20
        `when`(offer.getTradeItems()).thenAnswer { listOf(Item(314, amount)) }
        `when`(offer.offer(Item(314, 30))).thenAnswer { amount += 30; true }
        assertTrue(apply(script, offer, mapOf(314 to 50)))
        assertTrue(apply(script, offer, mapOf(314 to 50)))
        verify(offer, times(1)).offer(Item(314, 30))
        verify(offer, never()).remove(Item(314, 50))
        Unit
    }

    @Test fun failedTargetMutationStopsInsteadOfLooping(): Unit = runBlocking {
        val script = script(BotTradePurpose.SELL, mapOf(314 to 100))
        val offer = mock(BotTradeOfferStage::class.java)
        `when`(offer.getTradeItems()).thenReturn(listOf(Item(314, 100)))
        `when`(offer.remove(Item(314, 50))).thenReturn(false)
        assertFalse(apply(script, offer, mapOf(314 to 50)))
        verify(offer, times(1)).remove(Item(314, 50))
        verify(offer, never()).offer(Item(314, 50))
        Unit
    }

    @Test fun aSuccessfulClickWithoutTheRequestedInventoryDeltaCannotPassTargetValidation() = runBlocking {
        val script = script(BotTradePurpose.SELL, mapOf(314 to 100))
        val offer = mock(BotTradeOfferStage::class.java)
        `when`(offer.getTradeItems()).thenReturn(listOf(Item(314, 20)))
        `when`(offer.offer(Item(314, 30))).thenReturn(true)
        assertFalse(apply(script, offer, mapOf(314 to 50)))
    }

    @Test fun partialHumanSaleAdvancesBothRealTradeScreensAndTransfersTheAgreedItems(): Unit = runBlocking {
        val script = script(BotTradePurpose.SELL, mapOf(314 to 10), amount = 10)
        val other = script(BotTradePurpose.BUY, mapOf(995 to 5), amount = 10).bot
        val bot = script.bot
        InventoryProductionFixtures.inventory(bot, Item(314, 10))
        InventoryProductionFixtures.inventory(other, Item(995, 5))
        val own = OfferTradeInterface(other)
        val theirs = OfferTradeInterface(bot)
        var ownConfirm: ConfirmTradeInterface? = null
        var theirConfirm: ConfirmTradeInterface? = null
        val ownOverlays = bot.overlays
        val otherOverlays = other.overlays
        `when`(bot.overlays.getOverlay(OfferTradeInterface::class.java)).thenAnswer { if (ownConfirm == null) own else null }
        `when`(other.overlays.getOverlay(OfferTradeInterface::class.java)).thenAnswer { if (theirConfirm == null) theirs else null }
        `when`(bot.overlays.getOverlay(ConfirmTradeInterface::class.java)).thenAnswer { ownConfirm }
        `when`(other.overlays.getOverlay(ConfirmTradeInterface::class.java)).thenAnswer { theirConfirm }
        doAnswer { ownConfirm = it.getArgument(0); null }.`when`(ownOverlays).open(any(ConfirmTradeInterface::class.java))
        doAnswer { theirConfirm = it.getArgument(0); null }.`when`(otherOverlays).open(any(ConfirmTradeInterface::class.java))
        theirs.add(other, other.inventory.computeIndexForId(995), 5)

        val stage = mock(BotTradeOfferStage::class.java)
        `when`(stage.other).thenReturn(other)
        `when`(stage.getTradeItems()).thenAnswer { own.items.filterNotNull() }
        `when`(stage.getOtherTradeItems()).thenAnswer { theirs.items.filterNotNull() }
        `when`(stage.offer(Item(314, 5))).thenAnswer {
            own.add(bot, bot.inventory.computeIndexForId(314), 5)
            true
        }
        val confirmation = mock(BotTradeConfirmStage::class.java)
        `when`(confirmation.confirmAndAwait()).thenAnswer {
            ownConfirm!!.accept(bot)
            theirConfirm!!.accept(other)
            ownConfirm!!.completed
        }
        doAnswer {
            val valid = it.getArgument<() -> Boolean>(0)
            assertTrue(valid())
            own.accept(bot)
            theirs.accept(other)
            confirmation
        }.`when`(stage).offerAndAwait(any<() -> Boolean>() ?: { false })
        // Invoke the human target and application path, then the same confirmation path used by negotiation.
        val target = target(script, mapOf(995 to 5))
        assertEquals(mapOf(314 to 5), target)
        assertTrue(apply(script, stage, target))
        val completed: Boolean = suspendCoroutineUninterceptedOrReturn { continuation ->
            TradeBotScript::class.java.getDeclaredMethod("finishTradeWith", BotTradeOfferStage::class.java,
                Map::class.java, Map::class.java, Continuation::class.java).apply { isAccessible = true }
                .invoke(script, stage, target, mapOf(995 to 5), continuation)
        }
        assertTrue(completed)
        assertTrue(ownConfirm!!.completed)
        assertTrue(theirConfirm!!.completed)
        assertEquals(5, bot.inventory.computeAmountForId(995))
        assertEquals(5, bot.inventory.computeAmountForId(314))
        assertEquals(5, other.inventory.computeAmountForId(314))
        assertEquals(0, other.inventory.computeAmountForId(995))
    }
}
