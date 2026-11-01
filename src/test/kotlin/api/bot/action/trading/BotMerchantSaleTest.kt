package api.bot.action.trading

import api.bot.script.InventoryProductionFixtures
import api.predef.world
import com.google.common.collect.Iterators
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.item.economy.ItemPriceData
import io.luna.game.model.item.economy.WorldEconomy
import io.luna.game.model.mob.bot.BotSettings
import io.luna.util.GsonUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.OptionalInt

/**
 * Verifies merchant sale selection is applied to real transferable surplus without limiting buying payments.
 *
 * @author lare96
 */
class BotMerchantSaleTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private val bot = InventoryProductionFixtures.bot()
    private val fixtureWorld = world
    private val originalEconomy = fixtureWorld.economy
    private val economy = mock(WorldEconomy::class.java)
    private val priceStrategy = mock(BotTradePrice::class.java)
    private val ids = (554..563).toList()

    @BeforeEach
    fun prepareStock() {
        val wanted = ids.map {
            WantedItemDefinition(it, 0, 1, -1, -1,
                WantedItemDefinition.DEFAULT_PRIORITY, WantedItemDefinition.DEFAULT_TAGS)
        }
        val prices = ids.mapIndexed { index, id ->
            val price = when (index) {
                8 -> 10.0
                9 -> 5.0
                else -> 21.0 + index
            }
            id to ItemPriceData(id, price, price - 1, 1_000L - index * 10, 0)
        }.toMap()
        `when`(fixtureWorld.economy).thenReturn(economy)
        `when`(economy.prices).thenReturn(prices)
        `when`(economy.getRealPrice(anyInt())).thenAnswer { prices[it.getArgument<Int>(0)]?.price ?: 0.0 }
        `when`(priceStrategy.applySell(anyLong())).thenAnswer { it.getArgument<Long>(0) }
        `when`(bot.preferences.bot).thenReturn(bot)
        `when`(bot.preferences.wantedItems).thenAnswer {
            Iterators.unmodifiableIterator(wanted.iterator())
        }
        InventoryProductionFixtures.bank(bot, *ids.map { Item(it, 101) }.toTypedArray())
        InventoryProductionFixtures.bank(bot, Item(995, 50))
    }

    @AfterEach
    fun restoreEconomy() {
        `when`(fixtureWorld.economy).thenReturn(originalEconomy)
    }

    @Test
    fun merchantAccountsUseTheLimitedBatchForOrdinarySurplusSales() {
        `when`(bot.preferences.isMerchant).thenReturn(true)

        val offers = BotTradeManager.computeOfferItems(bot, priceStrategy, BotTradePurpose.SELL)

        assertEquals((555..561).toList(), offers.map { it.item.id })
        assertTrue(offers.all { it.item.amount == 100 && it.totalPrice() > 1_000 })
        assertEquals(2_200, offers.first().totalPrice())
        assertEquals(2_800, offers.last().totalPrice())
    }

    @Test
    fun merchantSellingPurposeAppliesTheBatchPolicyRegardlessOfAccountTemplate() {
        `when`(bot.preferences.isMerchant).thenReturn(false)

        val offers = BotTradeManager.computeOfferItems(bot, priceStrategy, BotTradePurpose.MERCHANT_SELL)

        assertEquals((555..561).toList(), offers.map { it.item.id })
        assertTrue(offers.all { it.item.amount == 100 && it.totalPrice() > 1_000 })
    }

    @Test
    fun configuredBatchSizeAndMinimumValueAffectActualMerchantOffers() {
        `when`(bot.preferences.isMerchant).thenReturn(true)
        val configuredBots = GsonUtils.GSON.fromJson(
            """{"merchant_sale_stack_limit": 2, "merchant_sale_minimum_stack_value": 2500}""",
            BotSettings::class.java)
        val thresholdBots = GsonUtils.GSON.fromJson(
            """{"merchant_sale_stack_limit": 7, "merchant_sale_minimum_stack_value": 2500}""",
            BotSettings::class.java)
        val settings = spy(Luna.settings())
        doReturn(configuredBots).`when`(settings).bots()

        mockStatic(Luna::class.java, CALLS_REAL_METHODS).use { luna ->
            luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)

            for ((bots, selectedIds) in listOf(configuredBots to listOf(560, 561),
                thresholdBots to listOf(559, 560, 561))) {
                doReturn(bots).`when`(settings).bots()
                for (purpose in listOf(BotTradePurpose.SELL, BotTradePurpose.MERCHANT_SELL)) {
                    val offers = BotTradeManager.computeOfferItems(bot, priceStrategy, purpose)

                    assertEquals(selectedIds, offers.map { it.item.id })
                    assertTrue(offers.all { it.totalPrice() > 2_500 })
                }
            }
        }
    }

    @Test
    fun unnoteableItemsQualifyAndTradeAsIndividualInventoryStacks() {
        val id = 554
        val originalDefinition = ItemDefinition.ALL.retrieve(id)
        val definitionEntries = ItemDefinition.ALL.javaClass.getDeclaredField("definitions").apply {
            isAccessible = true
        }.get(ItemDefinition.ALL)
        val definition = mock(ItemDefinition::class.java)
        `when`(definition.id()).thenReturn(id)
        `when`(definition.isTradeable).thenReturn(true)
        `when`(definition.isStackable).thenReturn(false)
        `when`(definition.notedId).thenReturn(OptionalInt.empty())
        `when`(definition.unnotedId).thenReturn(OptionalInt.empty())
        val wanted = WantedItemDefinition(id, 0, 1, -1, -1,
            WantedItemDefinition.DEFAULT_PRIORITY, WantedItemDefinition.DEFAULT_TAGS)
        `when`(bot.preferences.wantedItems).thenAnswer {
            Iterators.unmodifiableIterator(listOf(wanted).iterator())
        }
        `when`(bot.preferences.isMerchant).thenReturn(true)
        java.lang.reflect.Array.set(definitionEntries, id, definition)
        try {
            for (purpose in listOf(BotTradePurpose.SELL, BotTradePurpose.MERCHANT_SELL)) {
                val cheapPrices = mapOf(id to ItemPriceData(id, 30.0, 29.0, 1, 0))
                `when`(economy.prices).thenReturn(cheapPrices)
                assertTrue(BotTradeManager.computeOfferItems(bot, priceStrategy, purpose).isEmpty())

                val valuablePrices = mapOf(id to ItemPriceData(id, 2_100.0, 2_000.0, 1, 0))
                `when`(economy.prices).thenReturn(valuablePrices)
                val offers = BotTradeManager.computeOfferItems(bot, priceStrategy, purpose)
                assertEquals(Item(id, 1), offers.single().item)
                assertEquals(2_100, offers.single().totalPrice())
            }
        } finally {
            java.lang.reflect.Array.set(definitionEntries, id, originalDefinition)
        }
    }

    @Test
    fun merchantBuyingPaymentKeepsCoinsAndAllEligibleSurplusIncludingSmallStacks() {
        `when`(bot.preferences.isMerchant).thenReturn(true)

        for (purpose in listOf(BotTradePurpose.BUY, BotTradePurpose.MERCHANT_BUY)) {
            val payment = BotTradeManager.computeOfferItems(bot, priceStrategy, purpose)

            assertEquals((ids + 995).toSet(), payment.map { it.item.id }.toSet())
            assertEquals(Item(995, 50), payment.single { it.item.id == 995 }.item)
            assertEquals(500, payment.single { it.item.id == 563 }.totalPrice())
            assertEquals(1_000, payment.single { it.item.id == 562 }.totalPrice())
        }
    }
}
