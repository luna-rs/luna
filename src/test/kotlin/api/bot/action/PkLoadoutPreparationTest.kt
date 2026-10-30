package api.bot.action

import api.bot.script.InventoryProductionFixtures
import engine.bot.gear.BotItemTracker
import engine.bot.gear.BotGearPurpose
import engine.controllers.WildernessLocatableController.wildernessLevel
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPkPreference
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import java.math.BigInteger

/**
 * Replays bank preparation with real owned containers and native equipment, including bank-closing equip clicks.
 *
 * @author lare96
 */
class PkLoadoutPreparationTest {
    companion object {
        @JvmStatic @BeforeAll fun definitions() = InventoryProductionFixtures.initializeCombat()
    }

    private lateinit var luna: MockedStatic<Luna>

    @BeforeEach fun settings() {
        val settings = mock(LunaSettings::class.java, RETURNS_DEEP_STUBS)
        luna = mockStatic(Luna::class.java)
        luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)
    }

    @AfterEach fun restoreSettings() { luna.close() }

    /**
     * An owned tribrid kit with packet-equivalent equipment changes and controllable bank failures.
     *
     * @author lare96
     */
    private class Fixture {
        val bot: Bot = InventoryProductionFixtures.bot()
        val handler = mock(BotActionHandler::class.java, RETURNS_DEEP_STUBS)
        val preparation = PkLoadoutPreparation(bot, handler)
        val events = mutableListOf<String>()
        var bankOpen = false
        var opens = 0
        var rejectReopen = false
        var interruptEquip = false
        var partialWithdrawal = false

        init {
            `when`(bot.asPlr()).thenReturn(bot)
            bot.wildernessLevel = 0
            `when`(bot.username).thenReturn("pk_fixture")
            `when`(bot.preferences.pk).thenReturn(BotPkPreference(BotPkPreference.Purpose.VALUE,
                BotPkPreference.Styles.TRIBRID, BotPkPreference.Build.MAIN))
            `when`(bot.preferences.activities).thenReturn(mutableMapOf())
            `when`(bot.world.botManager.clanRegistry.capeFor("pk_fixture")).thenReturn(4315)
            `when`(bot.spellbook).thenReturn(Spellbook.REGULAR)
            for (skill in 0..6) {
                `when`(bot.skill(skill).level).thenReturn(99)
                `when`(bot.skill(skill).staticLevel).thenReturn(99)
            }
            val inventory = spy(bot.inventory)
            `when`(bot.inventory).thenReturn(inventory)
            for (id in listOf(882, 556, 558, 554, 565)) doReturn(true).`when`(inventory).stackable(id)
            val bank = spy(bot.bank)
            `when`(bot.bank).thenReturn(bank)
            doAnswer { bankOpen }.`when`(bank).isOpen
            val equipment = Equipment(bot).also { it.setListeners(BotItemTracker(bot)) }
            `when`(bot.equipment).thenReturn(equipment)
            val overlays = bot.overlays
            doAnswer { bankOpen = false; null }.`when`(overlays).closeWindows()
            InventoryProductionFixtures.bank(bot, Item(4315), Item(1333), Item(841), Item(1381),
                Item(882, 100), Item(556, 250), Item(554, 350), Item(565, 50), Item(333, 20))
            `when`(handler.combat.pkRiskBudget()).thenReturn(PkRiskBudget(1500, BigInteger.ZERO, BigInteger.ZERO))
            runBlocking {
                `when`(handler.banking.travelToBankOpen()).thenAnswer {
                    opens++
                    events += "open"
                    bankOpen = !(rejectReopen && opens > 1)
                    bankOpen
                }
                `when`(handler.banking.depositInventory()).thenAnswer { bankOpen && bot.inventory.none { it != null } }
                `when`(handler.equipment.unequipAll()).thenReturn(true)
                `when`(handler.banking.withdraw(any(Item::class.java) ?: Item(995))).thenAnswer { invocation ->
                    val requested = invocation.getArgument<Item>(0)
                    events += "withdraw:${requested.id}"
                    if (!bankOpen) false else {
                        val item = if (partialWithdrawal && requested.amount > 1)
                            requested.withAmount(requested.amount - 1) else requested
                        bank.remove(item) && inventory.add(item)
                    }
                }
                `when`(handler.equipment.equip(anyInt())).thenAnswer { invocation ->
                    val id = invocation.getArgument<Int>(0)
                    events += "equip:$id"
                    bot.overlays.closeWindows()
                    val equipped = equipment.equip(inventory.computeIndexForId(id))
                    if (interruptEquip) `when`(bot.isLocked).thenReturn(true)
                    equipped
                }
            }
        }

        fun owned(): Map<Int, Long> = (bot.inventory.filterNotNull() + bot.equipment.filterNotNull() +
            bot.bank.filterNotNull()).groupBy { it.id }.mapValues { (_, items) -> items.sumOf { it.amount.toLong() } }
    }

    @Test fun completesTheWholeOutfitDespiteNativeEquipClosingTheBank() = runBlocking {
        val fixture = Fixture()
        val before = fixture.owned()
        val plan = requireNotNull(fixture.preparation.plan())
        assertTrue(plan.equipment.size >= 3)
        assertTrue(fixture.preparation.prepare())
        assertTrue(fixture.preparation.ready())
        assertEquals(before, fixture.owned())
        assertEquals(plan.equipment, fixture.bot.equipment.filterNotNull())
        assertEquals(2, fixture.opens)
        assertFalse(fixture.bankOpen)
        val firstEquip = fixture.events.indexOfFirst { it.startsWith("equip:") }
        for (item in plan.equipment) {
            assertTrue(fixture.events.indexOf("withdraw:${item.id}") < firstEquip)
        }
        assertTrue(fixture.bot.inventory.contains(333))
        assertTrue((fixture.bot.inventory.filterNotNull() + fixture.bot.equipment.filterNotNull())
            .map { it.id }.containsAll(listOf(4315, 1333, 841, 1381, 882, 565)))
    }

    @Test fun reopeningFailureLeavesOwnedItemsIntactAndRejectsDeparture() = runBlocking {
        val fixture = Fixture().apply { rejectReopen = true }
        val before = fixture.owned()
        assertFalse(fixture.preparation.prepare())
        assertEquals(before, fixture.owned())
        assertFalse(fixture.bot.inventory.contains(333))
        assertFalse(fixture.bankOpen)
    }

    @Test fun aLockAcquiredDuringEquippingStopsFurtherBankActions() = runBlocking {
        val fixture = Fixture().apply { interruptEquip = true }
        val before = fixture.owned()
        assertFalse(fixture.preparation.prepare())
        assertEquals(before, fixture.owned())
        assertEquals(1, fixture.opens)
        assertEquals(1, fixture.events.count { it.startsWith("equip:") })
        assertFalse(fixture.bankOpen)
    }

    @Test fun partialSupplyWithdrawalCannotPassFinalReadiness() = runBlocking {
        val fixture = Fixture().apply { partialWithdrawal = true }
        val before = fixture.owned()
        assertFalse(fixture.preparation.prepare())
        assertEquals(before, fixture.owned())
        assertFalse(fixture.bankOpen)
    }

    @Test fun aChangedLiveRiskBudgetStillRejectsThePreparedKit() = runBlocking {
        val fixture = Fixture()
        `when`(fixture.handler.combat.pkRiskBudget()).thenReturn(PkRiskBudget(1500, BigInteger.ZERO, BigInteger.ONE))
        assertFalse(fixture.preparation.prepare())
        assertFalse(fixture.bankOpen)
    }

    @Test fun legalUnarmedCombatStillCountsAsMissingPkWeaponSupplies() {
        val fixture = Fixture()
        `when`(fixture.bot.preferences.pk).thenReturn(BotPkPreference(BotPkPreference.Purpose.VALUE,
            BotPkPreference.Styles.MELEE, BotPkPreference.Build.MAIN))
        for (id in listOf(1333, 841, 1381)) assertTrue(fixture.bot.bank.remove(Item(id)))
        val training = requireNotNull(CombatLoadout.find(fixture.bot, BotGearPurpose.MELEE))
        assertNull(training.gear[Equipment.WEAPON])
        assertNull(fixture.preparation.plan())
        assertTrue(fixture.preparation.missingSupplies())
    }
}
