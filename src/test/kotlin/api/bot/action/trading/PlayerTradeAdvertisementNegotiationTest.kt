package api.bot.action.trading

import api.bot.action.BotInteractionActionHandler
import api.bot.action.BotTradingActionHandler
import api.bot.action.trading.PlayerTradeAdvertisementMarket.Opportunity
import api.bot.script.InventoryProductionFixtures
import api.event.Matcher
import api.predef.ext.get
import api.predef.itemDef
import api.predef.scriptListeners
import api.predef.scriptMatchers
import api.predef.world
import com.google.common.collect.Multimap
import engine.trade.OfferTradeInterface
import game.bot.scripts.trading.TradeBotScript
import io.luna.game.event.Event
import io.luna.game.event.EventListener
import io.luna.game.event.EventMatcherListener
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.NumberInputEvent
import io.luna.game.event.impl.PlayerClickEvent.PlayerFourthClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.*
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.item.Inventory
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerContextMenuOption.TRADE
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.overlay.AbstractOverlaySet
import io.luna.game.task.Task
import io.luna.net.msg.out.PrivateChatMessageWriter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.time.Duration.Companion.seconds

/**
 * Exercises advertised negotiations through native trade handlers, real overlays and actual inventory transfers.
 * Client-output callbacks dispatch the compiled gameplay listeners without starting a server or network; coroutine
 * test time advances the bot's natural acceptance delays. No test callback fabricates a completed trade or payment.
 *
 * @author lare96
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerTradeAdvertisementNegotiationTest {

    /**
     * Loads the ordinary item and widget metadata needed by native trade screens.
     *
     * @author lare96
     */
    companion object {
        @JvmStatic @BeforeAll fun fixtures() {
            InventoryProductionFixtures.initialize()
            for (id in listOf(3323, 3443)) {
                if (WidgetDefinition.ALL.get(id).isEmpty) {
                    val definition = mock(WidgetDefinition::class.java)
                    `when`(definition.id()).thenReturn(id)
                    WidgetDefinition.ALL.storeDefinition(definition)
                }
            }
        }

        private val runTask = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        private val attemptTrade = TradeBotScript::class.java.getDeclaredMethod("attemptTrade", Player::class.java,
            Opportunity::class.java, Continuation::class.java).apply { isAccessible = true }
    }

    private lateinit var fixture: AdvertisementRuntimeFixture
    private lateinit var seller: Player
    private lateinit var buyer: TradeBotScript
    private lateinit var handlers: List<EventListener<*>>
    private lateinit var buttons: Map<Int, EventMatcherListener<ButtonClickEvent>>
    private var humanOffer = Item(314, 25)
    private var beforeNativeRequest: (() -> Unit)? = null

    @BeforeEach fun initialize() {
        fixture = AdvertisementRuntimeFixture()
        handlers = listOf("engine.trade.RequestTrade", "engine.trade.OfferItem", "engine.widget.EnterInput")
            .flatMap(::loadListeners)
        buttons = loadButtons()
        doAnswer {
            runTask.invoke(it.getArgument<Task>(0))
            null
        }.`when`(world).schedule(any(Task::class.java))
    }

    @AfterEach fun restoreWorld() {
        if (::buyer.isInitialized) buyer.bot.overlays.closeWindows()
        if (::seller.isInitialized) seller.overlays.closeWindows()
        doNothing().`when`(world).schedule(any(Task::class.java))
        fixture.close()
    }

    @Test fun aPartialCoinPurchaseCompletesBothNativeScreensAndLeavesTheUnsoldStockAdvertised(): Unit =
        runTest(timeout = 5.seconds) {
            val context = prepare()
            val entry = context.entry

            attempt(context)

            assertTrue(context.completed)
            assertEquals(25, buyer.bot.inventory.computeAmountForId(314))
            assertEquals(75, buyer.bot.inventory.computeAmountForId(995))
            assertEquals(75, seller.inventory.computeAmountForId(314))
            assertEquals(25, seller.inventory.computeAmountForId(995))
            assertSame(entry, fixture.market.registry.find(seller))
            assertEquals(75, entry.remainingQuantity(context.offer))
            assertEquals(100, context.offer.quantity)
            assertEquals(fixture.now.plusSeconds(60), entry.expiresAt)
            assertFalse(buyer.bot.overlays.hasWindow())
            assertFalse(seller.overlays.hasWindow())
            assertFalse(fixture.market.registry.release(context.response!!))
            verify(buyer.bot.output).sendPlayerInteraction(TRADE.index, seller)
            verify(buyer.bot.output).clickButton(3420)
            verify(buyer.bot.output).clickButton(3546)
            verify(seller, never()).queue(any(PrivateChatMessageWriter::class.java))
        }

    @Test fun backupItemsCanCompleteTheSamePartialTradeWhenTheBuyerHasNoCoins(): Unit =
        runTest(timeout = 5.seconds) {
            val context = prepare(barter = true)
            assertEquals(mapOf(556 to 3), context.plan!!.payment)
            assertEquals(25L, context.plan!!.cost)
            assertEquals(30L, context.plan!!.paidValue)

            attempt(context)

            assertTrue(context.completed)
            assertEquals(25, buyer.bot.inventory.computeAmountForId(314))
            assertEquals(0, buyer.bot.inventory.computeAmountForId(556))
            assertEquals(3, seller.inventory.computeAmountForId(556))
            assertEquals(0, seller.inventory.computeAmountForId(995))
            assertEquals(75, context.entry.remainingQuantity(context.offer))
            verify(buyer.bot.output).clickButton(3420)
            verify(buyer.bot.output).clickButton(3546)
        }

    @Test fun aDifferentCommodityDuringTheAcceptanceDelayRejectsTheTradeAndReturnsEscrow(): Unit =
        runTest(timeout = 5.seconds) {
            val context = prepare()
            val negotiation = async { attempt(context) }
            runCurrent()
            val offer = seller.overlays[OfferTradeInterface::class]!!
            assertEquals(25, buyer.bot.overlays[OfferTradeInterface::class]!!.items.computeAmountForId(995))
            assertFalse(negotiation.isCompleted)

            offer.remove(seller, offer.items.computeIndexForId(314), -1)
            offer.add(seller, seller.inventory.computeIndexForId(1779), 1)
            negotiation.await()

            assertFalse(context.completed)
            assertEquals(100, context.entry.remainingQuantity(context.offer))
            assertEquals(100, buyer.bot.inventory.computeAmountForId(995))
            assertEquals(0, buyer.bot.inventory.computeAmountForId(314))
            assertEquals(0, buyer.bot.inventory.computeAmountForId(1779))
            assertEquals(100, seller.inventory.computeAmountForId(314))
            assertEquals(1, seller.inventory.computeAmountForId(1779))
            assertFalse(buyer.bot.overlays.hasWindow())
            assertFalse(seller.overlays.hasWindow())
            assertFalse(fixture.market.registry.release(context.response!!))
            verify(buyer.bot.output, never()).clickButton(3420)
            verify(buyer.bot.output, never()).clickButton(3546)
        }

    @Test fun theFirstSentItemQuoteSurvivesALaterQuoteForAnotherItemOnADirectNativeTrade(): Unit =
        runTest(timeout = 5.seconds) {
            val entry = prepareRememberedOffers()
            val feathers = entry.offers.first()
            val flax = entry.offers.last()
            communicate(entry, feathers)
            communicate(entry, flax)
            assertEquals(listOf(feathers, flax), fixture.market.rememberedOffers(buyer, seller).map { it.offer })

            directAttempt()

            assertEquals(25, buyer.bot.inventory.computeAmountForId(314))
            assertEquals(82, buyer.bot.inventory.computeAmountForId(995))
            assertEquals(18, seller.inventory.computeAmountForId(995))
            assertEquals(75, entry.remainingQuantity(feathers))
            assertEquals(100, entry.remainingQuantity(flax))
            verify(buyer.bot.output).clickButton(3546)
        }

    @Test fun actualFlaxSelectsItsOwnSavedPriceInsteadOfTheEarlierFeatherQuote(): Unit =
        runTest(timeout = 5.seconds) {
            val entry = prepareRememberedOffers()
            entry.offers.forEach { communicate(entry, it) }
            humanOffer = Item(1779, 1)

            directAttempt()

            assertEquals(1, buyer.bot.inventory.computeAmountForId(1779))
            assertEquals(99, buyer.bot.inventory.computeAmountForId(995))
            assertEquals(1, seller.inventory.computeAmountForId(995))
            assertEquals(100, entry.remainingQuantity(entry.offers.first()))
            assertEquals(99, entry.remainingQuantity(entry.offers.last()))
            verify(buyer.bot.output).clickButton(3546)
        }

    @Test fun theMostRecentlyCommunicatedPriceForTheActualCommodityWinsWithoutDiscardingOtherItems(): Unit =
        runTest(timeout = 5.seconds) {
            val entry = prepareRememberedOffers(addLatestFeatherQuote = true)
            entry.offers.forEach { communicate(entry, it) }
            assertEquals(listOf(314, 1779, 314), fixture.market.rememberedOffers(buyer, seller)
                .map { it.pricing!!.itemId })

            directAttempt()

            assertEquals(25, buyer.bot.inventory.computeAmountForId(314))
            assertEquals(87, buyer.bot.inventory.computeAmountForId(995))
            assertEquals(13, seller.inventory.computeAmountForId(995))
            assertEquals(100, entry.remainingQuantity(entry.offers.first()))
            assertEquals(100, entry.remainingQuantity(entry.offers[1]))
            assertEquals(75, entry.remainingQuantity(entry.offers.last()))
        }

    @Test fun aQuoteThatExpiredBeforeTheNativeWindowOpensDoesNotReplaceOrdinaryTradePricing(): Unit =
        runTest(timeout = 5.seconds) {
            val entry = prepareRememberedOffers()
            communicate(entry, entry.offers.first())
            entry.expiresAt = Instant.EPOCH
            assertTrue(fixture.market.rememberedOffers(buyer, seller).isEmpty())

            directAttempt()

            assertEquals(25, buyer.bot.inventory.computeAmountForId(314))
            assertEquals(75, buyer.bot.inventory.computeAmountForId(995))
            assertEquals(25, seller.inventory.computeAmountForId(995))
            assertEquals(100, entry.remainingQuantity(entry.offers.first()))
            assertNull(fixture.market.registry.find(seller))
        }

    @Test fun aQuoteExpiringAfterRequestSubmissionButBeforeTheNativeWindowOpensIsNotReused(): Unit =
        runTest(timeout = 5.seconds) {
            val entry = prepareRememberedOffers()
            communicate(entry, entry.offers.first())
            beforeNativeRequest = { entry.expiresAt = Instant.EPOCH }

            directAttempt()

            assertEquals(25, buyer.bot.inventory.computeAmountForId(314))
            assertEquals(75, buyer.bot.inventory.computeAmountForId(995))
            assertEquals(25, seller.inventory.computeAmountForId(995))
            assertEquals(100, entry.remainingQuantity(entry.offers.first()))
            assertNull(fixture.market.registry.find(seller))
        }

    @Test fun aSavedPriceBoundToAnAlreadyOpenTradeSurvivesExpiryDuringItsAcceptanceDelay(): Unit =
        runTest(timeout = 5.seconds) {
            val entry = prepareRememberedOffers()
            communicate(entry, entry.offers.first())
            val negotiation = async { directAttempt() }
            runCurrent()
            assertEquals(18, buyer.bot.overlays[OfferTradeInterface::class]!!.items.computeAmountForId(995))

            entry.expiresAt = Instant.EPOCH
            assertNull(fixture.market.registry.find(seller))
            negotiation.await()

            assertEquals(25, buyer.bot.inventory.computeAmountForId(314))
            assertEquals(82, buyer.bot.inventory.computeAmountForId(995))
            assertEquals(18, seller.inventory.computeAmountForId(995))
            assertEquals(100, entry.remainingQuantity(entry.offers.first()))
            verify(buyer.bot.output).clickButton(3546)
        }

    @Test fun anUnfundableMatchingSavedQuoteDeclinesWithoutUsingAnOrdinaryReplacementOffer(): Unit =
        runTest(timeout = 5.seconds) {
            val entry = prepareRememberedOffers()
            communicate(entry, entry.offers.first())
            assertTrue(buyer.bot.inventory.remove(Item(995, 100)))

            directAttempt()

            assertEquals(0, buyer.bot.inventory.computeAmountForId(314))
            assertEquals(0, buyer.bot.inventory.computeAmountForId(995))
            assertEquals(100, seller.inventory.computeAmountForId(314))
            assertEquals(100, entry.remainingQuantity(entry.offers.first()))
            assertFalse(buyer.bot.overlays.hasWindow())
            assertFalse(seller.overlays.hasWindow())
            verify(buyer.bot.output, never()).clickButton(3420)
            verify(buyer.bot.output, never()).clickButton(3546)
        }

    @Test fun replacingNotesWithRawGoodsUpdatesTheSmallerQuantityNoticeOnceAndPaysOnlyForWhatFits(): Unit =
        runTest(timeout = 5.seconds) {
            prepareNativeTrader(id = 1779, target = 500, coins = 1_000)
            assertTrue(seller.inventory.remove(Item(314, 100)))
            assertTrue(seller.inventory.add(Item(1779, 26)))
            val note = itemDef(1779).notedId.orElseThrow()
            assertTrue(seller.inventory.add(Item(note, 500)))
            humanOffer = Item(note, 500)
            fixture.publish(seller, "selling 1000 flax 1 gp ea")
            val context = fixture.market.claim(buyer)!!
            val negotiation = async { attempt(context) }
            runCurrent()
            val offer = seller.overlays[OfferTradeInterface::class]!!
            assertEquals(500, buyer.bot.overlays[OfferTradeInterface::class]!!.items.computeAmountForId(995))

            offer.remove(seller, offer.items.computeIndexForId(note), -1)
            offer.add(seller, seller.inventory.computeIndexForId(1779), 27)
            negotiation.await()

            assertTrue(context.completed)
            assertEquals(27, buyer.bot.inventory.computeAmountForId(1779))
            assertEquals(973, buyer.bot.inventory.computeAmountForId(995))
            assertEquals(27, seller.inventory.computeAmountForId(995))
            assertEquals(973, context.entry.remainingQuantity(context.offer))
            val name = itemDef(1779).name
            verify(seller).sendMessage("@blu@${buyer.bot.username} wants up to 500 $name.@bla@")
            verify(seller).sendMessage("@blu@${buyer.bot.username} wants up to 27 $name.@bla@")
            verify(buyer.bot.output).clickButton(3546)
        }

    private fun prepare(barter: Boolean = false): Opportunity {
        prepareNativeTrader(coins = if (barter) 0 else 100)
        if (barter) {
            InventoryProductionFixtures.inventory(buyer.bot, Item(556, 3))
            fixture.set(buyer, "carriedOffer", mapOf(556 to 3))
            @Suppress("UNCHECKED_CAST")
            val guides = TradeBotScript::class.java.getDeclaredField("advertisementGuides")
                .apply { isAccessible = true }.get(buyer) as MutableMap<Int, Long>
            guides[556] = 10L
        }
        fixture.publish(seller, "selling 100 feathers 1 gp ea")
        return fixture.market.claim(buyer)!!
    }

    private fun prepareNativeTrader(id: Int = 314, target: Int = 25, coins: Int = 100) {
        seller = fixture.human()
        `when`(seller.inventory).thenReturn(Inventory(seller))
        `when`(seller.overlays).thenReturn(AbstractOverlaySet(seller))
        `when`(seller.type).thenReturn(EntityType.PLAYER)
        `when`(seller.index).thenReturn(816)
        `when`(seller.position).thenReturn(Position(3181, 3450))
        `when`(seller.contextMenu.contains(TRADE)).thenReturn(true)
        check(seller.inventory.add(Item(314, 100)))
        check(seller.inventory.add(Item(1779)))
        buyer = fixture.trader(seller, id = id, target = target, coins = coins)
        val bot = buyer.bot
        `when`(bot.overlays).thenReturn(AbstractOverlaySet(bot))
        `when`(bot.type).thenReturn(EntityType.PLAYER)
        `when`(bot.walking.isLocked).thenReturn(false)
        `when`(bot.walking.isEmpty).thenReturn(true)
        `when`(world.collisionManager.reached(bot, seller, InteractionPolicy.STANDARD_SIZE)).thenReturn(true)
        val actionHandler = bot.actionHandler
        `when`(actionHandler.interactions).thenReturn(BotInteractionActionHandler(bot, actionHandler))
        `when`(actionHandler.trading).thenReturn(BotTradingActionHandler(bot, actionHandler))
        bridgeOutput(bot)
    }

    private fun prepareRememberedOffers(addLatestFeatherQuote: Boolean = false): PlayerTradeAdvertisementRegistry.Entry {
        fixture.close()
        fixture = AdvertisementRuntimeFixture(PlayerTradeAdvertisementMarket.live)
        prepareNativeTrader()
        BotTradeIntentions.recordSupplies(buyer.bot, listOf(Item(1779, 10)), stockTarget = 10)
        fixture.set(buyer, "expected", listOf(
            BotTradeItem(Item(314, 25), 1, BotTradePurpose.BUY, buyer.bot),
            BotTradeItem(Item(1779, 10), 1, BotTradePurpose.BUY, buyer.bot)
        ))
        val text = "selling 100 feathers 70 gp total, 100 flax 1 gp ea" +
                if (addLatestFeatherQuote) ", 100 feathers 50 gp total" else ""
        return fixture.publish(seller, text)
    }

    private fun communicate(entry: PlayerTradeAdvertisementRegistry.Entry, offer: PlayerTradeAdvertisement) {
        val context = buyer.advertisementCandidate(fixture.market, entry, offer)!!
        context.response = fixture.market.registry.claim(entry, buyer.bot, offer)!!
        val announce = TradeBotScript::class.java.getDeclaredMethod("announceAdvertisement", Opportunity::class.java)
            .apply { isAccessible = true }
        assertEquals(true, announce.invoke(buyer, context))
        assertTrue(context.communicated)
        fixture.market.release(context)
    }

    private suspend fun directAttempt() {
        dispatch(PlayerFourthClickEvent(seller, buyer.bot))
        attempt(null)
    }

    private fun bridgeOutput(bot: Bot) {
        `when`(bot.output.sendPlayerInteraction(TRADE.index, seller)).thenAnswer {
            beforeNativeRequest?.invoke()
            dispatch(PlayerFourthClickEvent(bot, seller))
            dispatch(PlayerFourthClickEvent(seller, bot))
            seller.overlays[OfferTradeInterface::class]!!.apply {
                add(seller, seller.inventory.computeIndexForId(humanOffer.id), humanOffer.amount)
            }
            true
        }
        `when`(bot.output.sendItemWidgetClick(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer {
            val option = it.getArgument<Int>(0)
            val index = it.getArgument<Int>(1)
            val widget = it.getArgument<Int>(2)
            val id = it.getArgument<Int>(3)
            val event = when (option) {
                1 -> WidgetItemFirstClickEvent(bot, index, widget, id)
                2 -> WidgetItemSecondClickEvent(bot, index, widget, id)
                3 -> WidgetItemThirdClickEvent(bot, index, widget, id)
                4 -> WidgetItemFourthClickEvent(bot, index, widget, id)
                5 -> WidgetItemFifthClickEvent(bot, index, widget, id)
                else -> error("Unexpected trade option $option")
            }
            dispatch(event)
            true
        }
        `when`(bot.output.enterAmount(anyInt())).thenAnswer {
            dispatch(NumberInputEvent(bot, it.getArgument(0)))
            true
        }
        `when`(bot.output.clickButton(anyInt())).thenAnswer {
            val button = it.getArgument<Int>(0)
            buttons.getValue(button).apply(ButtonClickEvent(seller, button))
            buttons.getValue(button).apply(ButtonClickEvent(bot, button))
            true
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun dispatch(event: Event) {
        val matching = handlers.filter { it.eventType == event.javaClass }
        check(matching.isNotEmpty()) { "No native handler for ${event.javaClass.simpleName}" }
        matching.forEach { (it as EventListener<Event>).apply(event) }
    }

    private fun loadListeners(name: String): List<EventListener<*>> {
        val first = scriptListeners.size
        try {
            Class.forName(name).getConstructor(Array<String>::class.java).newInstance(emptyArray<String>() as Any)
            return scriptListeners.drop(first)
        } finally {
            scriptListeners.subList(first, scriptListeners.size).clear()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadButtons(): Map<Int, EventMatcherListener<ButtonClickEvent>> {
        val matcher = Matcher.get<ButtonClickEvent, Int>()
        val actions = Matcher::class.java.getDeclaredField("actions").apply { isAccessible = true }
            .get(matcher) as Multimap<Int, EventMatcherListener<ButtonClickEvent>>
        val first = scriptMatchers.size
        try {
            Class.forName("engine.trade.AcceptTrade").getConstructor(Array<String>::class.java)
                .newInstance(emptyArray<String>() as Any)
            val added = scriptMatchers.drop(first).toSet()
            return listOf(3420, 3546).associateWith { id -> actions[id].single { it in added } }
        } finally {
            val added = scriptMatchers.drop(first).toSet()
            actions.values().removeAll(added)
            scriptMatchers.subList(first, scriptMatchers.size).clear()
        }
    }

    private suspend fun attempt(context: Opportunity?): Unit = suspendCoroutineUninterceptedOrReturn { continuation ->
        attemptTrade.invoke(buyer, seller, context, continuation)
    }
}
