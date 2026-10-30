package api.bot.action

import api.bot.SuspendableFuture
import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotItemTracker
import engine.controllers.WildernessLocatableController.wildernessLevel
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.model.item.Equipment
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPkPreference
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.overlay.AbstractOverlay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import java.util.EnumSet
import java.util.OptionalInt

/**
 * Exercises native PK quality selection and purchases funded through physical non-combat bank stock.
 *
 * @author lare96
 */
class PkSuppliesTest {
    companion object {
        @JvmStatic @BeforeAll fun definitions() {
            InventoryProductionFixtures.initializeCombat()
            for (id in listOf(3824, 3822)) {
                if (WidgetDefinition.ALL.get(id).isEmpty) {
                    val definition = mock(WidgetDefinition::class.java)
                    `when`(definition.id()).thenReturn(id)
                    WidgetDefinition.ALL.storeDefinition(definition)
                }
            }
        }
    }

    private lateinit var luna: MockedStatic<Luna>

    @BeforeEach fun settings() {
        luna = mockStatic(Luna::class.java)
        val settings = mock(LunaSettings::class.java, RETURNS_DEEP_STUBS)
        luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)
    }

    @AfterEach fun restoreSettings() { luna.close() }

    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.asPlr()).thenReturn(it)
        it.wildernessLevel = 0
        `when`(it.preferences.pk).thenReturn(BotPkPreference(BotPkPreference.Purpose.VALUE,
            BotPkPreference.Styles.TRIBRID, BotPkPreference.Build.MAIN))
        `when`(it.preferences.activities).thenReturn(mutableMapOf())
        `when`(it.spellbook).thenReturn(Spellbook.REGULAR)
        doReturn(EnumSet.of(SubZone.HOME)).`when`(it).subZones
        for (skill in 0..6) {
            `when`(it.skill(skill).level).thenReturn(99)
            `when`(it.skill(skill).staticLevel).thenReturn(99)
        }
        val equipment = Equipment(it).also { equipment -> equipment.setListeners(BotItemTracker(it)) }
        `when`(it.equipment).thenReturn(equipment)
    }

    @Test fun missingWaveRunesRequestTheStrongestSpellInsteadOfSettlingForOwnedWindStrike() {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(1381), Item(558, 1000), Item(556, 1000))
        val selection = PkLoadoutSelection(bot)
        val desired = requireNotNull(selection.select(BotGearPurpose.MAGIC, desired = true))
        assertEquals(CombatSpell.FIRE_WAVE, desired.spell?.spell)
        assertEquals(mapOf(554 to 7, 565 to 1), desired.runes)
        assertNull(selection.select(BotGearPurpose.MAGIC))
        InventoryProductionFixtures.bank(bot, Item(554, 350), Item(565, 50))
        assertEquals(CombatSpell.FIRE_WAVE, selection.select(BotGearPurpose.MAGIC)?.spell?.spell)
    }

    @Test fun strongestCompatibleArrowsAndAHighHittingMeleeWeaponWinOverWeakOwnedAlternatives() {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(841), Item(861), Item(882, 1000), Item(892, 100),
            Item(1333), Item(1319), Item(1381))
        val selection = PkLoadoutSelection(bot)
        val ranged = requireNotNull(selection.select(BotGearPurpose.RANGED))
        assertEquals(861, ranged.gear[Equipment.WEAPON])
        assertEquals(892, ranged.gear[Equipment.AMMUNITION])
        assertEquals(1319, selection.select(BotGearPurpose.MELEE)?.gear?.get(Equipment.WEAPON))
    }

    @Test fun ordinaryTrainingMayStillUseOwnedLowTierSpells() {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(1381), Item(558, 1000))
        assertEquals(CombatSpell.WIND_STRIKE, CombatLoadout.find(bot, BotGearPurpose.MAGIC)?.spell?.spell)
        assertNull(PkLoadoutSelection(bot).select(BotGearPurpose.MAGIC))
    }

    /**
     * Shop and bank operations move real containers while native async actions report completed transactions.
     *
     * @author lare96
     */
    private inner class Funding {
        val bot = bot()
        val handler = mock(BotActionHandler::class.java, RETURNS_DEEP_STUBS)
        val supply = PkSupplyAcquisition(bot, handler)
        val shop = mock(Shop::class.java, RETURNS_DEEP_STUBS)
        val stock = ItemContainer(40, ItemContainer.StackPolicy.ALWAYS, -1)
        val sales = mutableListOf<Pair<Int, Int>>()
        var opened: ShopInterface? = null
        var bankVisits = 0
        var noProgress = false

        init {
            val inventory = spy(bot.inventory)
            `when`(bot.inventory).thenReturn(inventory)
            for (id in listOf(995, 565, 554, 556, 558, 882, 892)) doReturn(true).`when`(inventory).stackable(id)
            `when`(shop.currency).thenReturn(Currency.COINS)
            `when`(shop.items).thenReturn(stock)
            `when`(shop.itemIds).thenReturn(setOf(565))
            `when`(shop.botAccess.apply(bot)).thenReturn(true)
            `when`(shop.computeCanSell(anyInt())).thenReturn(true)
            `when`(shop.computeSellValue(anyInt(), anyInt())).thenAnswer { it.getArgument<Int>(1) * 10 }
            `when`(shop.computeBuyValue(anyInt(), anyInt())).thenAnswer { OptionalInt.of(it.getArgument<Int>(1) * 20) }
            `when`(bot.world.shops.values()).thenReturn(listOf(shop))
            stock.add(Item(565, 1000))
            val overlays = bot.overlays
            `when`(overlays.player).thenReturn(bot)
            doAnswer { opened = it.getArgument<AbstractOverlay>(0) as? ShopInterface; null }
                .`when`(overlays).open(any(AbstractOverlay::class.java))
            doAnswer { opened = null; null }.`when`(overlays).closeWindows()
            `when`(overlays.getOverlay(ShopInterface::class.java)).thenAnswer { opened }
            for ((amount, action) in listOf(1 to handler.shop::sell1, 5 to handler.shop::sell5, 10 to handler.shop::sell10)) {
                `when`(action(anyInt())).thenAnswer {
                    val id = it.getArgument<Int>(0)
                    sales += id to amount
                    if (!noProgress && inventory.remove(Item(id, amount))) inventory.add(Item(995, amount * 10))
                    SuspendableFuture().signal(true)
                }
            }
            runBlocking {
                `when`(handler.banking.travelToBankOpen()).thenAnswer { bankVisits++; true }
                `when`(handler.banking.depositInventory()).thenAnswer {
                    for (item in inventory.filterNotNull()) {
                        check(bot.bank.add(item))
                        check(inventory.remove(item))
                    }
                    true
                }
                `when`(handler.banking.withdraw(any(Item::class.java) ?: Item(995))).thenAnswer {
                    val item = it.getArgument<Item>(0)
                    bot.bank.remove(item) && inventory.add(item)
                }
                `when`(handler.shop.obtainWantedItem(anyInt(), anyInt())).thenAnswer {
                    val id = it.getArgument<Int>(0)
                    val amount = it.getArgument<Int>(1)
                    val needed = amount - inventory.computeAmountForId(id)
                    val price = needed * 20
                    var coins = inventory.computeAmountForId(995)
                    if (coins < price) {
                        val withdrawn = minOf(price - coins, bot.bank.computeAmountForId(995))
                        if (withdrawn > 0) { bot.bank.remove(Item(995, withdrawn)); inventory.add(Item(995, withdrawn)) }
                        coins += withdrawn
                    }
                    if (coins < price) false else {
                        inventory.remove(Item(995, price)) && inventory.add(Item(id, needed))
                    }
                }
            }
        }
    }

    @Test fun insufficientGoldSellsNonPkStockThenObtainsTheRequiredQuantity() = runBlocking {
        val fixture = Funding()
        InventoryProductionFixtures.bank(fixture.bot, Item(1511, 100), Item(1381), Item(882, 100), Item(333, 20))
        assertTrue(fixture.supply.obtain(mapOf(565 to 20)))
        verify(fixture.handler.shop).obtainWantedItem(565, 20)
        assertEquals(20, fixture.bot.inventory.computeAmountForId(565))
        assertTrue(fixture.sales.isNotEmpty())
        assertTrue(fixture.sales.all { it.first == 1511 })
        assertEquals(1, fixture.bot.bank.computeAmountForId(1381))
        assertEquals(100, fixture.bot.bank.computeAmountForId(882))
        assertEquals(20, fixture.bot.bank.computeAmountForId(333))
        assertTrue(fixture.sales.sumOf { it.second } < 100)
    }

    @Test fun ordinaryAcquisitionWithdrawsTheRuneBudgetOnceAndBuysInBatches() = runBlocking<Unit> {
        val fixture = Funding()
        InventoryProductionFixtures.bank(fixture.bot, Item(995, 1000))
        val shopping = spy(BotShopActionHandler(fixture.bot, fixture.handler))
        fun buy(id: Int, amount: Int): SuspendableFuture {
            val paid = fixture.bot.inventory.remove(Item(995, amount * 20))
            val bought = paid && fixture.stock.remove(Item(id, amount)) && fixture.bot.inventory.add(Item(id, amount))
            return SuspendableFuture().signal(bought)
        }
        doAnswer { buy(it.getArgument(0), 1) }.`when`(shopping).buy1(anyInt())
        doAnswer { buy(it.getArgument(0), 5) }.`when`(shopping).buy5(anyInt())
        doAnswer { buy(it.getArgument(0), 10) }.`when`(shopping).buy10(anyInt())
        assertTrue(shopping.obtainWantedItem(565, 50))
        assertEquals(50, fixture.bot.inventory.computeAmountForId(565))
        assertEquals(0, fixture.bot.inventory.computeAmountForId(995))
        assertEquals(1, fixture.bankVisits)
        verify(shopping, times(5)).buy10(565)
        verify(shopping, never()).buy1(565)
    }

    @Test fun unavailableSuppliesCannotTriggerLiquidationAndFalseSuccessCannotLoop() = runBlocking {
        val fixture = Funding()
        InventoryProductionFixtures.bank(fixture.bot, Item(1511, 100))
        fixture.stock.clear()
        assertNull(fixture.supply.quoteMissing(mapOf(565 to 20)))
        assertFalse(fixture.supply.obtain(mapOf(565 to 20)))
        assertTrue(fixture.sales.isEmpty())
        fixture.stock.add(Item(565, 1000))
        fixture.noProgress = true
        assertFalse(fixture.supply.fund(400, setOf(565)))
        assertEquals(1, fixture.sales.size)
    }

    @Test fun allPkSuppliesAndDynamicOrUntradeableItemsAreProtectedFromFundingSales() {
        val fixture = Funding()
        for (id in listOf(995, 565, 554, 882, 333, 1333, 1381, 2434, 2552, 4315)) {
            assertTrue(fixture.supply.protectedItem(Item(id)), "id=$id must remain reserved")
        }
        assertFalse(fixture.supply.protectedItem(Item(1511)))
        assertTrue(fixture.supply.protectedItem(Item(1511), setOf(1511)))
    }
}
