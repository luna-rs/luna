package api.bot.action

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.predef.world
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.EnumSet

/**
 * Exercises bounded basket acquisition with real inventory and bank containers, without a live server.
 *
 * @author lare96
 */
class ObtainWantedItemsTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun initialize() {
            InventoryProductionFixtures.initialize()
            for (id in listOf(3822, 3824)) {
                if (WidgetDefinition.ALL[id].isEmpty) {
                    val definition = mock(WidgetDefinition::class.java)
                    `when`(definition.id()).thenReturn(id)
                    WidgetDefinition.ALL.storeDefinition(definition)
                }
            }
        }
    }

    @BeforeEach
    fun resetShops() {
        `when`(world.shops.values()).thenReturn(emptyList())
    }

    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        doReturn(EnumSet.of(SubZone.HOME)).`when`(it).subZones
    }

    private fun shop(bot: Bot, vararg ids: Int): Shop =
        spy(Shop(world, "Basket fixture", RestockPolicy.DEFAULT, BuyPolicy.ALL, Currency.COINS, { true }, false)).also { shop ->
            ids.forEach { check(shop.items.add(Item(it))) }
            `when`(bot.overlays.player).thenReturn(bot)
            `when`(bot.overlays.getOverlay(ShopInterface::class.java)).thenReturn(ShopInterface(shop))
        }

    private suspend fun withdrawNormally(bot: Bot) {
        `when`(bot.actionHandler.banking.travelToBankOpen()).thenReturn(true)
        `when`(bot.actionHandler.banking.withdraw(any(Item::class.java) ?: Item(995))).thenAnswer {
            val item = it.getArgument<Item>(0)
            val removed = bot.bank.remove(item)
            removed && bot.inventory.add(item)
        }
    }

    @Test
    fun carriedBasketAndEmptyBasketAvoidAllTravel(): Unit = runBlocking {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(1755), Item(946))
        val shopping = BotShopActionHandler(bot, bot.actionHandler)
        assertEquals(setOf(1755, 946), shopping.obtainWantedItems(listOf(1755, 946, 1755)))
        assertEquals(emptySet<Int>(), shopping.obtainWantedItems(emptyList()))
        verifyNoInteractions(bot.actionHandler)
    }

    @Test
    fun unavailableFirstItemDoesNotPreventBankedItems(): Unit = runBlocking {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(1755), Item(946))
        withdrawNormally(bot)
        val shopping = BotShopActionHandler(bot, bot.actionHandler)
        assertEquals(setOf(1755, 946), shopping.obtainWantedItems(listOf(4151, 1755, 946)))
        assertFalse(bot.bank.contains(1755))
        assertFalse(bot.bank.contains(946))
        verify(bot.actionHandler.banking, times(2)).withdraw(any(Item::class.java) ?: Item(995))
    }

    @Test
    fun buysDifferentItemsAndKeepsEarlierPurchases(): Unit = runBlocking {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(995, 30))
        val shop = shop(bot, 1755, 946)
        `when`(world.shops.values()).thenReturn(listOf(shop))
        val shopping = spy(BotShopActionHandler(bot, bot.actionHandler))
        assertEquals(setOf(1755, 946), shopping.obtainWantedItems(listOf(4151, 1755, 946)))
        assertEquals(28, bot.inventory.computeAmountForId(995))
        assertEquals(setOf(1755, 946), shopping.obtainWantedItems(listOf(1755, 946)))
        verify(shop).buy(bot, 0, 1)
        verify(shop).buy(bot, 1, 1)
    }

    @Test
    fun bankCapacityDepositPreservesWholeBasket(): Unit = runBlocking {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(1755), Item(1511, 27))
        InventoryProductionFixtures.bank(bot, Item(946))
        withdrawNormally(bot)
        `when`(bot.actionHandler.banking.depositInventory(setOf(1755, 946))).thenAnswer {
            check(bot.inventory.remove(Item(1511, 27)))
            true
        }
        assertEquals(setOf(1755, 946), BotShopActionHandler(bot, bot.actionHandler)
            .obtainWantedItems(listOf(1755, 946)))
        verify(bot.actionHandler.banking).depositInventory(setOf(1755, 946))
        assertTrue(bot.inventory.contains(1755))
    }

    @Test
    fun shopCapacityDepositPreservesWholeBasketAndCurrency(): Unit = runBlocking {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(1755), Item(1511, 27))
        InventoryProductionFixtures.bank(bot, Item(995, 20))
        withdrawNormally(bot)
        val shop = shop(bot, 946)
        `when`(world.shops.values()).thenReturn(listOf(shop))
        `when`(bot.actionHandler.banking.depositInventory(setOf(1755, 946, 995))).thenAnswer {
            check(bot.inventory.remove(Item(1511, 27)))
            true
        }
        val shopping = spy(BotShopActionHandler(bot, bot.actionHandler))
        assertEquals(setOf(1755, 946), shopping.obtainWantedItems(listOf(1755, 946)))
        verify(bot.actionHandler.banking).depositInventory(setOf(1755, 946, 995))
        assertTrue(bot.inventory.contains(1755))
    }

    @Test
    fun quantityTargetsStillUseBulkPurchases(): Unit = runBlocking {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(995, 1_000))
        val shop = shop(bot, 556)
        shop.items.set(0, Item(556, 100))
        `when`(world.shops.values()).thenReturn(listOf(shop))
        val shopping = spy(BotShopActionHandler(bot, bot.actionHandler))
        assertTrue(shopping.obtainWantedItem(556, 100))
        assertEquals(100, bot.inventory.computeAmountForId(556))
        verify(shop).buy(bot, 0, 100)
        verify(shopping, never()).buy1(556)
    }

    @Test
    fun purchaseWithoutInventoryProgressStopsAndReturnsPartialBasket(): Unit = runBlocking {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(1755), Item(995, 20))
        val shop = shop(bot, 946)
        `when`(world.shops.values()).thenReturn(listOf(shop))
        val shopping = spy(BotShopActionHandler(bot, bot.actionHandler))
        doReturn(true).`when`(shopping).buyUpTo(shop, 946, 1, 20)
        assertEquals(setOf(1755), shopping.obtainWantedItems(listOf(1755, 946)))
        verify(shopping).buyUpTo(shop, 946, 1, 20)
    }
}
