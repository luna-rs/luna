package api.bot.action.trading

import api.bot.isRealBot
import api.bot.script.InventoryProductionFixtures
import api.predef.world
import engine.bot.speech.BotChatMessage
import engine.trade.OfferTradeInterface
import engine.trade.tradingWith
import game.bot.scripts.trading.TradeBotScript
import io.luna.game.action.ActionQueue
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.Locatable
import io.luna.game.model.Position
import io.luna.game.model.WorldLocator
import io.luna.game.model.def.DefinitionRepository
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerContextMenuOption.TRADE
import io.luna.game.model.mob.PlayerPrivacy
import io.luna.game.model.mob.PlayerPrivacy.PrivacyMode
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.attr.AttributeMap
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.net.msg.GameMessageWriter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant
import java.util.Optional
import java.util.OptionalInt
import java.util.function.Predicate
import kotlin.time.Duration.Companion.minutes

/**
 * Supplies current player sessions and real prepared trading scripts without starting a server or network.
 * Global locator and player-lookup stubs are restored when the local fixture closes.
 *
 * @author lare96
 */
internal class AdvertisementRuntimeFixture(marketOverride: PlayerTradeAdvertisementMarket? = null) : AutoCloseable {

    var now: Instant = Instant.EPOCH
    var resolutions = 0
    val audience = ArrayList<Bot>()
    private val previousLocator = world.locator
    private val locator = mock(WorldLocator::class.java)
    private val previousPlayers = LinkedHashMap<Long, Optional<Player>>()
    private val flax = ItemDefinition.ALL.retrieve(1779)
    private val previousFlaxNote = flax.notedId
    private val suppliedFlaxNote = ItemDefinition.ALL.get(1780).isEmpty
    private val previousDefinitionCount = ItemDefinition.ALL.size
    private var nextHash = 816_000L
    val market = marketOverride ?: PlayerTradeAdvertisementMarket(clock = { now },
        parser = PlayerTradeAdvertisementParser { name ->
            resolutions++
            when (name) {
                "feather", "feathers" -> intArrayOf(314)
                "flax" -> intArrayOf(1779)
                else -> null
            }
        }, sample = { 0.0 })

    init {
        // The common item fixture omits notes. Supply the native pair only for this fixture's lifetime.
        if (suppliedFlaxNote) {
            val note = ItemDefinition(1780, "Flax", "", 0, 0, 0, 0, 0, 0, 0,
                true, 0, false, emptyArray(), emptyArray(), OptionalInt.of(1779),
                0, 0, 0, OptionalInt.empty(), 0.0, true)
            ItemDefinition.ALL.storeDefinition(note.toNote(flax))
        } else ItemDefinition::class.java.getDeclaredField("notedId").apply {
            isAccessible = true
            set(flax, OptionalInt.of(1780))
        }
        `when`(world.locator).thenReturn(locator)
        doAnswer {
            val predicate = it.getArgument<Predicate<Player>>(1)
            audience.filter { bot -> predicate.test(bot) }.toCollection(LinkedHashSet<Player>())
        }.`when`(locator).findViewablePlayers(any(Locatable::class.java),
            any<Predicate<Player>>() ?: Predicate { true })
    }

    fun human(connected: Boolean = false, privateChat: PrivacyMode = PrivacyMode.ON): Player {
        val player = if (connected) mock(Bot::class.java, RETURNS_DEEP_STUBS)
            else mock(Player::class.java, RETURNS_DEEP_STUBS)
        if (player is Bot) {
            `when`(player.isBot).thenReturn(false)
            `when`(player.isConnectedPlayer).thenReturn(true)
        }
        `when`(player.attributes()).thenReturn(AttributeMap())
        `when`(player.state).thenReturn(EntityState.ACTIVE)
        `when`(player.isAlive).thenReturn(true)
        `when`(player.rights).thenReturn(PlayerRights.PLAYER)
        `when`(player.world).thenReturn(world)
        `when`(player.privacyOptions).thenReturn(PlayerPrivacy(PrivacyMode.ON, privateChat, PrivacyMode.ON))
        `when`(player.ignores).thenReturn(HashSet())
        `when`(player.friends).thenReturn(HashSet())
        `when`(player.overlays.player).thenReturn(player)
        `when`(player.overlays.hasWindow()).thenReturn(false)
        `when`(player.overlays.getOverlay(OfferTradeInterface::class.java)).thenReturn(null)
        player.tradingWith = -1
        register(player)
        return player
    }

    fun trader(seller: Player, id: Int = 314, target: Int = 100, coins: Int = 10_000,
               maxUnit: Long = 1, purpose: BotTradePurpose = BotTradePurpose.BUY,
               specific: Boolean = true): TradeBotScript {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.isBot).thenReturn(true)
        `when`(bot.state).thenReturn(EntityState.ACTIVE)
        `when`(bot.isAlive).thenReturn(true)
        `when`(bot.isLocked).thenReturn(false)
        `when`(bot.rights).thenReturn(PlayerRights.PLAYER)
        `when`(bot.world).thenReturn(world)
        `when`(bot.position).thenReturn(Position(3180, 3450))
        `when`(bot.ignores).thenReturn(HashSet())
        `when`(bot.friends).thenReturn(HashSet())
        `when`(bot.contextMenu.contains(TRADE)).thenReturn(true)
        `when`(bot.overlays.player).thenReturn(bot)
        `when`(bot.overlays.hasWindow()).thenReturn(false)
        `when`(bot.overlays.getOverlay(OfferTradeInterface::class.java)).thenReturn(null)
        `when`(bot.combat.target).thenReturn(null)
        `when`(bot.reflex.activeReflex).thenReturn(null)
        `when`(bot.personality.intelligence).thenReturn(0.0)
        `when`(bot.personality.confidence).thenReturn(0.0)
        `when`(bot.personality.social).thenReturn(0.0)
        `when`(bot.personality.kindness).thenReturn(1.0)
        `when`(bot.preferences.activities).thenReturn(mapOf(BotActivity.MERCHANTING to 0.0))
        `when`(bot.preferences.isMerchant).thenReturn(false)
        `when`(bot.preferences.isSkiller).thenReturn(true)
        `when`(bot.isViewableFrom(seller)).thenReturn(true)
        `when`(seller.isViewableFrom(bot)).thenReturn(true)
        bot.tradingWith = -1
        register(bot)
        val index = (bot.usernameHash - 815_000).toInt()
        `when`(bot.index).thenReturn(index)
        if (coins > 0) InventoryProductionFixtures.inventory(bot, Item(995, coins))
        val script = InventoryProductionFixtures.active(TradeBotScript(bot, purpose, duration = 10.minutes,
            purchaseTarget = if (specific) Item(id, target) else null, purchasePrice = maxUnit))
        `when`(bot.scriptStack.current()).thenReturn(script)
        set(script, "prepared", true)
        set(script, "carriedOffer", if (coins > 0) mapOf(995 to coins) else emptyMap<Int, Int>())
        set(script, "offering", listOf(BotTradeItem(Item(995, maxOf(1, coins)), 1, purpose, bot)))
        set(script, "expected", listOf(BotTradeItem(Item(id, target), maxUnit, BotTradePurpose.BUY, bot)))
        audience += bot
        return script
    }

    fun publish(seller: Player, text: String): PlayerTradeAdvertisementRegistry.Entry {
        assertTrue(market.handle(BotChatMessage(seller, text)))
        return market.registry.find(seller)!!
    }

    fun set(script: TradeBotScript, name: String, value: Any?) {
        TradeBotScript::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(script, value)
        }
    }

    private fun register(player: Player) {
        val hash = nextHash++
        previousPlayers[hash] = world.getPlayer(hash)
        `when`(player.usernameHash).thenReturn(hash)
        `when`(player.username).thenReturn("player$hash")
        `when`(world.getPlayer(hash)).thenReturn(Optional.of(player))
    }

    override fun close() {
        `when`(world.locator).thenReturn(previousLocator)
        for ((hash, player) in previousPlayers) {
            world.getPlayer(hash).orElse(null)?.let(market.registry::remove)
            `when`(world.getPlayer(hash)).thenReturn(player)
        }
        ItemDefinition::class.java.getDeclaredField("notedId").apply {
            isAccessible = true
            set(flax, previousFlaxNote)
        }
        if (suppliedFlaxNote) {
            val definitions = ItemDefinition.ALL.javaClass.getDeclaredField("definitions").apply {
                isAccessible = true
            }.get(ItemDefinition.ALL)
            java.lang.reflect.Array.set(definitions, 1780, null)
            DefinitionRepository::class.java.getDeclaredField("size").apply {
                isAccessible = true
                setInt(ItemDefinition.ALL, previousDefinitionCount)
            }
        }
    }
}

/**
 * Exercises public advertisements through the runtime market and real prepared trading-script eligibility.
 *
 * @author lare96
 */
class PlayerTradeAdvertisementRuntimeAcceptanceTest {

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

    @Test fun autonomousBotAdvertisementsAreSkippedBeforeAnyNameResolution() {
        val speaker = mock(Bot::class.java)
        `when`(speaker.isBot).thenReturn(true)

        assertFalse(fixture.market.handle(BotChatMessage(speaker, "selling feathers 1 gp ea")))

        assertEquals(0, fixture.resolutions)
    }

    @Test fun aConnectedHumanBotWrapperCanPublishAnActionableSale() {
        val seller = fixture.human(connected = true)
        val buyer = fixture.trader(seller)

        val entry = fixture.publish(seller, "selling 100 feathers 1 gp ea")

        assertFalse(seller.isRealBot())
        assertSame(seller, entry.player)
        assertTrue(fixture.market.hasPriority(buyer))
        assertEquals(100, fixture.market.claim(buyer)?.plan?.quantity)
    }

    @Test fun theLargestWillingBuyerPrecedesSmallerWillingBuyersAndLargerCounteroffers() {
        val seller = fixture.human()
        val counter = fixture.trader(seller, id = 1779, target = 200, coins = 4_000, maxUnit = 20)
        val small = fixture.trader(seller, id = 1779, target = 200, coins = 500, maxUnit = 25)
        val large = fixture.trader(seller, id = 1779, target = 200, coins = 2_500, maxUnit = 25)
        val entry = fixture.publish(seller, "selling 200 flax 25 gp ea")

        assertFalse(fixture.market.hasPriority(counter))
        assertFalse(fixture.market.hasPriority(small))
        assertTrue(fixture.market.hasPriority(large))
        assertNull(fixture.market.claim(small))
        val response = fixture.market.claim(large)!!

        assertSame(entry, response.entry)
        assertEquals(100, response.plan?.quantity)
        assertFalse(response.pricing!!.quote.counteroffer)
        assertEquals(25L, response.pricing!!.quote.price.coins)
    }

    @Test fun theHighestCounterofferWinsEvenWhenAnotherBotCouldBuyMore() {
        val seller = fixture.human()
        val lower = fixture.trader(seller, id = 1779, target = 200, coins = 6_000, maxUnit = 30)
        val higher = fixture.trader(seller, id = 1779, target = 200, coins = 4_000, maxUnit = 40)
        fixture.publish(seller, "selling 200 flax 50 gp ea")

        assertFalse(fixture.market.hasPriority(lower))
        assertTrue(fixture.market.hasPriority(higher))
        val response = fixture.market.claim(higher)!!

        assertTrue(response.pricing!!.quote.counteroffer)
        assertEquals(40L, response.pricing!!.quote.price.coins)
        assertEquals(100, response.plan?.quantity)
    }

    @Test fun privateChatRestrictionsExcludeCountersWithoutDisablingAcceptedAskingPrices() {
        val seller = fixture.human(privateChat = PrivacyMode.OFF)
        val counter = fixture.trader(seller, maxUnit = 1)
        val accepting = fixture.trader(seller, maxUnit = 2)
        val entry = fixture.publish(seller, "selling 100 feathers 2 gp ea")
        val offer = entry.offers.single()

        assertNull(counter.advertisementCandidate(fixture.market, entry, offer))
        assertNotNull(accepting.advertisementCandidate(fixture.market, entry, offer))
        assertFalse(fixture.market.hasPriority(counter))
        assertTrue(fixture.market.hasPriority(accepting))
        assertNotNull(fixture.market.claim(accepting))
        verify(seller, never()).queue(any(GameMessageWriter::class.java))
    }

    @Test fun counterEligibilityRechecksPublicPrivateChatWithoutRerollingItsQuote() {
        val seller = fixture.human()
        val buyer = fixture.trader(seller, maxUnit = 1)
        fixture.publish(seller, "selling 100 feathers 2 gp ea")
        assertTrue(fixture.market.hasPriority(buyer))

        for (mode in listOf(PrivacyMode.OFF, PrivacyMode.FRIENDS)) {
            `when`(seller.privacyOptions).thenReturn(PlayerPrivacy(PrivacyMode.ON, mode, PrivacyMode.ON))
            assertFalse(fixture.market.hasPriority(buyer), mode.name)
        }
        `when`(seller.privacyOptions).thenReturn(PlayerPrivacy())
        assertTrue(fixture.market.hasPriority(buyer))
    }

    @Test fun unknownSaleItemsAttractMerchantsButNotUncuriousOrdinaryBuyers() {
        val seller = fixture.human()
        val ordinary = fixture.trader(seller)
        val merchant = fixture.trader(seller, purpose = BotTradePurpose.MERCHANT_BUY)
        fixture.publish(seller, "selling mystery widget")

        assertFalse(fixture.market.hasPriority(ordinary))
        assertTrue(fixture.market.hasPriority(merchant))
        val response = fixture.market.claim(merchant)!!

        assertTrue(response.inspection)
        assertNull(response.pricing)
        assertNull(response.plan)
    }

    @Test fun repeatAdvertisementsKeepQuotesAndPriorityChecksDoNotConsumeTheOpportunity() {
        val seller = fixture.human()
        val buyer = fixture.trader(seller)
        val entry = fixture.publish(seller, "selling 100 feathers 1 gp ea")
        val first = buyer.advertisementCandidate(fixture.market, entry, entry.offers.single())!!
        repeat(3) { assertTrue(fixture.market.hasPriority(buyer)) }

        fixture.now = fixture.now.plusSeconds(59)
        assertSame(entry, fixture.publish(seller, "selling 100 feathers 1 gp ea"))
        val repeated = buyer.advertisementCandidate(fixture.market, entry, entry.offers.single())!!

        assertSame(first.pricing, repeated.pricing)
        assertEquals(2, fixture.resolutions)
        assertEquals(listOf(entry), fixture.market.registry.availableFor(buyer.bot))
        val claimed = fixture.market.claim(buyer)!!
        assertTrue(fixture.market.registry.isCurrent(claimed.response!!))
        assertNull(fixture.market.claim(buyer))
    }

    @Test fun aMatchingAdvertisementCanInterruptAnUnansweredBotApproachButNotAnOpenTrade() {
        val seller = fixture.human()
        val buyer = fixture.trader(seller)
        val pendingBot = mock(Bot::class.java)
        `when`(pendingBot.isBot).thenReturn(true)
        fixture.set(buyer, "negotiating", true)
        fixture.set(buyer, "pendingPartner", pendingBot)
        fixture.publish(seller, "selling 100 feathers 1 gp ea")

        assertTrue(fixture.market.hasPriority(buyer))

        val openTrade = mock(OfferTradeInterface::class.java)
        `when`(openTrade.other).thenReturn(pendingBot)
        `when`(buyer.bot.overlays.getOverlay(OfferTradeInterface::class.java)).thenReturn(openTrade)
        `when`(buyer.bot.overlays.hasWindow()).thenReturn(true)

        assertFalse(fixture.market.hasPriority(buyer))
        assertNull(fixture.market.claim(buyer))
    }

    @Test fun acceptedTermsInALaterClausePrecedeAnEarlierClausesCounteroffer() {
        val seller = fixture.human()
        val counter = fixture.trader(seller, maxUnit = 1)
        val accepting = fixture.trader(seller, id = 1779, maxUnit = 25)
        fixture.publish(seller, "selling 100 feathers 2 gp ea, 100 flax 25 gp ea")

        assertFalse(fixture.market.hasPriority(counter))
        assertTrue(fixture.market.hasPriority(accepting))
        val response = fixture.market.claim(accepting)!!

        assertEquals(setOf(1779), response.offer.itemIds)
        assertFalse(response.pricing!!.quote.counteroffer)
    }

    @Test fun aBuyerStillInCombatDoesNotBlockAnAvailableSmallerBuyer() {
        val seller = fixture.human()
        val recovering = fixture.trader(seller, target = 100)
        val ready = fixture.trader(seller, target = 50)
        `when`(recovering.bot.combat.inCombat()).thenReturn(true)
        fixture.publish(seller, "selling 100 feathers 1 gp ea")

        assertFalse(fixture.market.hasPriority(recovering))
        assertTrue(fixture.market.hasPriority(ready))
        assertSame(ready, fixture.market.claim(ready)?.script)
    }

    @Test fun aSpecificPurchaseVisitCanAlsoRespondToAnotherGenuineCurrentNeed() {
        val seller = fixture.human()
        val buyer = fixture.trader(seller, id = 314, target = 100)
        BotTradeIntentions.recordSupplies(buyer.bot, listOf(Item(1779, 100)), stockTarget = 100)
        fixture.set(buyer, "expected", listOf(
            BotTradeItem(Item(314, 100), 1, BotTradePurpose.BUY, buyer.bot),
            BotTradeItem(Item(1779, 100), 25, BotTradePurpose.BUY, buyer.bot)
        ))
        fixture.publish(seller, "selling 100 flax 25 gp ea")

        assertTrue(fixture.market.hasPriority(buyer))
        val response = fixture.market.claim(buyer)!!
        assertEquals(setOf(1779), response.offer.itemIds)
        assertEquals(100, response.plan?.quantity)
        assertFalse(response.unexpected)
    }

    @Test fun aBuyerWithAStrongActionDoesNotBlockAnAvailableSmallerBuyer() {
        val seller = fixture.human()
        val busy = fixture.trader(seller, target = 100)
        val ready = fixture.trader(seller, target = 50)
        val actions = mock(ActionQueue::class.java)
        `when`(actions.size(ActionType.STRONG)).thenReturn(1)
        `when`(busy.bot.actions).thenReturn(actions)
        fixture.publish(seller, "selling 100 feathers 1 gp ea")

        assertFalse(fixture.market.hasPriority(busy))
        assertTrue(fixture.market.hasPriority(ready))
        assertSame(ready, fixture.market.claim(ready)?.script)
    }

    @Test fun aSentCounterCanResumeFromADirectRequestUntilTheOriginalAdvertisementExpires() {
        val seller = fixture.human()
        val buyer = fixture.trader(seller, maxUnit = 1)
        fixture.publish(seller, "selling 100 feathers 2 gp ea")
        val sent = fixture.market.claim(buyer)!!
        sent.counterSent = true
        fixture.market.remember(sent)
        fixture.market.release(sent)
        fixture.now = fixture.now.plusSeconds(59)
        `when`(seller.privacyOptions).thenReturn(PlayerPrivacy(PrivacyMode.ON, PrivacyMode.OFF, PrivacyMode.ON))

        val remembered = fixture.market.remembered(buyer, seller)!!
        val resumed = buyer.advertisementCandidate(fixture.market, remembered.entry, remembered.offer,
            remembered = remembered)!!
        val response = fixture.market.registry.resume(remembered.entry, buyer.bot)!!

        assertSame(sent.pricing, resumed.pricing)
        assertTrue(resumed.counterSent)
        assertTrue(resumed.continuation)
        assertTrue(fixture.market.registry.isCurrent(response))
        assertTrue(fixture.market.registry.release(response))
        fixture.now = fixture.now.plusSeconds(1)
        assertNull(fixture.market.remembered(buyer, seller))
        assertNull(fixture.market.registry.resume(remembered.entry, buyer.bot))
    }

    @Test fun communicatedClausesKeepOtherCommoditiesAndTheirOwnCommunicationOrderAcrossRepeats() {
        val seller = fixture.human()
        val buyer = fixture.trader(seller, specific = false)
        BotTradeIntentions.recordSupplies(buyer.bot, listOf(Item(314, 100), Item(1779, 100)), stockTarget = 100)
        fixture.set(buyer, "expected", listOf(
            BotTradeItem(Item(314, 100), 1, BotTradePurpose.BUY, buyer.bot),
            BotTradeItem(Item(1779, 100), 1, BotTradePurpose.BUY, buyer.bot)
        ))
        val text = "selling 100 feathers 70 gp total, 100 flax 1 gp ea, 100 feathers 50 gp total"
        val entry = fixture.publish(seller, text)
        for (offer in entry.offers) {
            val context = buyer.advertisementCandidate(fixture.market, entry, offer)!!
            context.response = fixture.market.registry.claim(entry, buyer.bot, offer)!!
            fixture.market.remember(context)
            fixture.market.release(context)
        }

        fixture.now = fixture.now.plusSeconds(59)
        assertSame(entry, fixture.publish(seller, text))
        val remembered = fixture.market.rememberedOffers(buyer, seller)

        assertEquals(entry.offers, remembered.map { it.offer })
        assertEquals(listOf(314, 1779, 314), remembered.map { it.pricing!!.itemId })
        assertEquals(listOf(70L, 1L, 50L), remembered.map { it.pricing!!.quote.price.coins })
        assertEquals(remembered.last(), fixture.market.remembered(buyer, seller))
        assertNull(fixture.market.claim(buyer))
    }

    @Test fun allCommunicatedClauseSnapshotsExpireTogetherBeforeAnotherTradeIsOpened() {
        val seller = fixture.human()
        val buyer = fixture.trader(seller, specific = false)
        BotTradeIntentions.recordSupplies(buyer.bot, listOf(Item(314, 100), Item(1779, 100)), stockTarget = 100)
        fixture.set(buyer, "expected", listOf(
            BotTradeItem(Item(314, 100), 1, BotTradePurpose.BUY, buyer.bot),
            BotTradeItem(Item(1779, 100), 1, BotTradePurpose.BUY, buyer.bot)
        ))
        val entry = fixture.publish(seller, "selling 100 feathers 1 gp ea, 100 flax 1 gp ea")
        for (offer in entry.offers) {
            val context = buyer.advertisementCandidate(fixture.market, entry, offer)!!
            context.response = fixture.market.registry.claim(entry, buyer.bot, offer)!!
            fixture.market.remember(context)
            fixture.market.release(context)
        }
        val capturedBeforeExpiry = fixture.market.rememberedOffers(buyer, seller)
        assertEquals(2, capturedBeforeExpiry.size)

        fixture.now = fixture.now.plusSeconds(60)

        assertTrue(fixture.market.rememberedOffers(buyer, seller).isEmpty())
        assertNull(fixture.market.remembered(buyer, seller))
        assertEquals(entry.offers, capturedBeforeExpiry.map { it.offer })
        assertEquals(listOf(314, 1779), capturedBeforeExpiry.map { it.pricing!!.itemId })
    }

    @Test fun oneBuyerCanFinishOneClauseAndThenRespondToADifferentClauseInTheSameAdvertisement() {
        val seller = fixture.human()
        val buyer = fixture.trader(seller, specific = false)
        BotTradeIntentions.recordSupplies(buyer.bot, listOf(Item(314, 100), Item(1779, 100)), stockTarget = 100)
        fixture.set(buyer, "expected", listOf(
            BotTradeItem(Item(314, 100), 1, BotTradePurpose.BUY, buyer.bot),
            BotTradeItem(Item(1779, 100), 25, BotTradePurpose.BUY, buyer.bot)
        ))
        val entry = fixture.publish(seller, "selling 100 feathers 1 gp ea, 100 flax 25 gp ea")
        val first = fixture.market.claim(buyer)!!
        assertEquals(setOf(314), first.offer.itemIds)

        assertTrue(buyer.bot.inventory.remove(Item(995, 100)))
        InventoryProductionFixtures.inventory(buyer.bot, Item(314, 100))
        fixture.market.completed(first, 100)
        fixture.market.release(first)

        assertSame(entry, fixture.market.registry.find(seller))
        assertEquals(0, entry.remainingQuantity(first.offer))
        assertTrue(fixture.market.hasPriority(buyer))
        val second = fixture.market.claim(buyer)!!

        assertSame(entry, second.entry)
        assertEquals(setOf(1779), second.offer.itemIds)
        assertEquals(100, second.plan?.quantity)
        fixture.market.release(second)
        assertNull(fixture.market.claim(buyer))
    }
}
