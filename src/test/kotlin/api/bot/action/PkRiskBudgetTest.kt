package api.bot.action

import api.bot.script.InventoryProductionFixtures
import api.combat.death.DeathHookHandler
import api.combat.death.PlayerDeathItems
import engine.combat.prayer.CombatPrayer
import game.skill.runecrafting.essencePouch.EssencePouch
import game.bot.scripts.combat.PkBotScript
import io.luna.Luna
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.MobDeathAction.DeathStage
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.SkullIcon
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotPersonality
import io.luna.game.model.mob.bot.brain.BotPersonalityManager
import io.luna.game.model.mob.bot.brain.BotPkPreference
import io.luna.game.model.mob.bot.brain.BotPkPreference.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlinx.coroutines.runBlocking
import java.math.BigInteger
import kotlin.time.Duration.Companion.minutes

/**
 * Checks actual retained stack quantities, guide-price loss and personality allowances with native containers.
 * The default death-script integration uses a fixture world; no live server or economy service is started.
 *
 * @author lare96
 */
class PkRiskBudgetTest {
    companion object {
        @JvmStatic @BeforeAll fun definitions() = InventoryProductionFixtures.initialize()
    }

    private lateinit var bot: Bot
    private val originalValues = HashMap<Int, Int>()
    private val originalStackability = HashMap<Int, Boolean>()

    @BeforeEach fun setup() {
        bot = InventoryProductionFixtures.bot()
        val fixtureWorld = mock(World::class.java, RETURNS_DEEP_STUBS)
        val equipment = Equipment(bot).also { it.setListeners() }
        `when`(bot.world).thenReturn(fixtureWorld)
        `when`(bot.equipment).thenReturn(equipment)
        `when`(bot.skullIcon).thenReturn(SkullIcon.NONE)
        `when`(bot.rights).thenReturn(PlayerRights.PLAYER)
        `when`(bot.preferences.pk).thenReturn(BotPkPreference(Purpose.GENERIC, Styles.TRIBRID, Build.MAIN))
        `when`(bot.preferences.activities).thenReturn(mapOf(BotActivity.PKING to 0.5))
        val personality = BotPersonality.Builder(mock(BotPersonalityManager::class.java))
            .setIntelligence(0.5).setConfidence(0.5).setKindness(0.5).setSocial(0.5).setDexterity(0.5).build()
        `when`(bot.personality).thenReturn(personality)
        // The shared dump fixture omits stackability; restore native quantities for coins, arrows and runes.
        val stackable = ItemDefinition::class.java.getDeclaredField("stackable").apply { isAccessible = true }
        for (id in listOf(995, 882, 556)) {
            val definition = ItemDefinition.ALL.retrieve(id)
            originalStackability[id] = definition.isStackable
            stackable.setBoolean(definition, true)
        }
        for ((id, base) in mapOf(4151 to 10_000, 1333 to 8_000, 1127 to 6_000, 1079 to 4_000,
            995 to 1, 882 to 7, 556 to 4, 1436 to 3, 7936 to 5)) {
            val definition = ItemDefinition.ALL.retrieve(id)
            originalValues[id] = definition.value
            ItemDefinition::class.java.getDeclaredField("value").apply { isAccessible = true }.setInt(definition, base)
            `when`(bot.world.economy.getPrice(id)).thenReturn(base.toLong())
        }
    }

    @AfterEach fun restoreDefinitions() {
        val field = ItemDefinition::class.java.getDeclaredField("value").apply { isAccessible = true }
        originalValues.forEach { (id, value) -> field.setInt(ItemDefinition.ALL.retrieve(id), value) }
        originalValues.clear()
        val stackable = ItemDefinition::class.java.getDeclaredField("stackable").apply { isAccessible = true }
        originalStackability.forEach { (id, value) -> stackable.setBoolean(ItemDefinition.ALL.retrieve(id), value) }
        originalStackability.clear()
    }

    private fun assess(losesItems: Boolean = true) = PkRiskBudget.assess(bot, losesItems)

    @Test fun riskShareIsBoundedAndEachApprovedTraitHasTheExpectedDirection() {
        assertEquals(500, PkRiskBudget.basisPoints(0.0, 1.0, 0.0))
        assertEquals(2500, PkRiskBudget.basisPoints(1.0, 0.0, 1.0))
        assertEquals(1500, PkRiskBudget.basisPoints(0.5, 0.5, 0.5))
        assertTrue(PkRiskBudget.basisPoints(0.9, 0.5, 0.5) > 1500)
        assertTrue(PkRiskBudget.basisPoints(0.5, 0.9, 0.5) < 1500)
        assertTrue(PkRiskBudget.basisPoints(0.5, 0.5, 0.9) > 1500)
        assertEquals(500, PkRiskBudget.basisPoints(-10.0, 10.0, -10.0))
        assertEquals(2500, PkRiskBudget.basisPoints(10.0, -10.0, 10.0))
        assertEquals(1500, PkRiskBudget.basisPoints(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY))
    }

    @Test fun onlyDroppedValueCountsWhileBankAndProtectedEquipmentRemainPartOfWealth() {
        bot.equipment.set(Equipment.WEAPON, Item(4151))
        bot.equipment.set(Equipment.CHEST, Item(1127))
        bot.equipment.set(Equipment.LEGS, Item(1079))
        InventoryProductionFixtures.inventory(bot, Item(882, 100))
        InventoryProductionFixtures.bank(bot, Item(995, 100_000))
        val risk = assess()
        assertEquals(BigInteger.valueOf(120_700), risk.wealth)
        assertEquals(BigInteger.valueOf(700), risk.deathLoss)
        assertEquals(BigInteger.valueOf(18_105), risk.maximumLoss)
        assertTrue(risk.withinBudget)
        assertEquals(100, bot.inventory.computeAmountForId(882))
        assertEquals(4151, bot.equipment.get(Equipment.WEAPON).id)
        assertEquals(100_000, bot.bank.computeAmountForId(995))
    }

    @Test fun protectionUsesBaseValueButLossUsesEconomyPrice() {
        InventoryProductionFixtures.inventory(bot, Item(4151), Item(1333), Item(1127), Item(1079))
        `when`(bot.world.economy.getPrice(1079)).thenReturn(1_000_000L)
        assertEquals(BigInteger.valueOf(1_000_000), assess().deathLoss)
        assertFalse(assess().withinBudget)
    }

    @Test fun aProtectedStackKeepsOnlyOneUnitThenTheNextStackUsesTheNextSlot() {
        ItemDefinition::class.java.getDeclaredField("value").apply { isAccessible = true }
            .setInt(ItemDefinition.ALL.retrieve(882), 20_000)
        val input = listOf(Item(882, 100), Item(1333), Item(1127), Item(1079))
        val selected = PlayerDeathItems.select(input, 3)
        assertEquals(listOf(Item(882), Item(1333), Item(1127)), selected.kept)
        assertEquals(listOf(Item(882, 99), Item(1079)), selected.lost)
        assertEquals(100, input.first().amount)
    }

    @Test fun skullAndActiveProtectItemChangeExactLostQuantities() {
        InventoryProductionFixtures.inventory(bot, Item(4151), Item(1333), Item(1127), Item(1079))
        assertEquals(BigInteger.valueOf(4_000), assess().deathLoss)
        `when`(bot.combat.prayers.isActive(CombatPrayer.PROTECT_ITEM)).thenReturn(true)
        assertEquals(BigInteger.ZERO, assess().deathLoss)
        `when`(bot.skullIcon).thenReturn(SkullIcon.WHITE)
        assertEquals(BigInteger.valueOf(18_000), assess().deathLoss)
        `when`(bot.combat.prayers.isActive(CombatPrayer.PROTECT_ITEM)).thenReturn(false)
        assertEquals(BigInteger.valueOf(28_000), assess().deathLoss)
    }

    @Test fun carriedSwitchWeaponsAndSupplyStacksCountWhileNonTradeablesAreRetained() {
        bot.equipment.set(Equipment.WEAPON, Item(4151))
        InventoryProductionFixtures.inventory(bot, Item(1333), Item(556, 100), Item(882, 100), Item(6570))
        `when`(bot.skullIcon).thenReturn(SkullIcon.WHITE)
        val risk = assess()
        assertEquals(BigInteger.valueOf(19_100), risk.deathLoss)
        assertEquals(risk.wealth, risk.deathLoss)
        val selection = PlayerDeathItems.select(listOf(Item(6570), Item(4151)), 0)
        assertEquals(listOf(Item(4151)), selection.lost)
        assertTrue(selection.kept.isEmpty())
    }

    @Test fun destroyedEssenceIsCountedOncePerPouchTypeAndBankedPouchesAreSafe() {
        InventoryProductionFixtures.inventory(bot, Item(5509), Item(5510))
        InventoryProductionFixtures.bank(bot, Item(5512))
        for (id in listOf(5509, 5510, 5512)) `when`(bot.world.economy.getPrice(id)).thenReturn(20L)
        for (pouch in listOf(EssencePouch.SMALL, EssencePouch.MEDIUM, EssencePouch.LARGE)) {
            bot.attributes()[pouch.essence] = 3
            bot.attributes()[pouch.essenceId] = EssencePouch.PURE_ESSENCE
        }
        assertEquals(BigInteger.valueOf(105), assess().wealth)
        assertEquals(BigInteger.valueOf(70), assess().deathLoss)
        assertEquals(3, bot.attributes()[EssencePouch.SMALL.essence])
    }

    @Test fun wealthAndRiskDoNotOverflowWhenSeveralMaximumStacksAreOwned() {
        for ((slot, id) in listOf(4151, 1333, 1127).withIndex()) {
            bot.inventory.set(slot, Item(id, Int.MAX_VALUE))
            `when`(bot.world.economy.getPrice(id)).thenReturn(Int.MAX_VALUE.toLong())
        }
        `when`(bot.skullIcon).thenReturn(SkullIcon.WHITE)
        val expected = BigInteger.valueOf(Int.MAX_VALUE.toLong()).pow(2) * BigInteger.valueOf(3)
        assertTrue(expected > BigInteger.valueOf(Long.MAX_VALUE))
        assertEquals(expected, assess().wealth)
        assertEquals(expected, assess().deathLoss)
        assertFalse(assess().withinBudget)
    }

    @Test fun maximumLossRoundsDownAndExactBoundaryIsAllowed() {
        assertEquals(BigInteger.valueOf(5), PkRiskBudget(500, BigInteger.valueOf(101), BigInteger.valueOf(5)).maximumLoss)
        assertTrue(PkRiskBudget(500, BigInteger.valueOf(101), BigInteger.valueOf(5)).withinBudget)
        assertFalse(PkRiskBudget(500, BigInteger.valueOf(101), BigInteger.valueOf(6)).withinBudget)
        assertEquals(BigInteger.ZERO, assess().maximumLoss)
        assertTrue(assess().withinBudget)
    }

    @Test fun skillerAndMissingPkIdentityNeverAuthorizeLoadoutRiskEvenWhenNothingWouldBeLost() {
        `when`(bot.preferences.isSkiller).thenReturn(true)
        assertFalse(assess(false).withinBudget)
        assertEquals(0, assess(false).basisPoints)
        `when`(bot.preferences.isSkiller).thenReturn(false)
        `when`(bot.preferences.pk).thenReturn(null)
        assertFalse(assess(false).withinBudget)
    }

    @Test fun configuredSafeDeathsLoseNothingWithoutChangingOwnedWealth() {
        InventoryProductionFixtures.inventory(bot, Item(995, 1000))
        `when`(bot.skullIcon).thenReturn(SkullIcon.WHITE)
        assertEquals(BigInteger.valueOf(1000), assess(false).wealth)
        assertEquals(BigInteger.ZERO, assess(false).deathLoss)
    }

    @Test fun overBudgetTripsCannotEnterOrPursueAndEscapeBeforeQueuingAnotherFight() = runBlocking<Unit> {
        val position = Position(3100, 3540)
        val targetPosition = Position(3101, 3540)
        `when`(bot.position).thenReturn(position)
        val target = mock(Player::class.java, RETURNS_DEEP_STUBS)
        `when`(target.position).thenReturn(targetPosition)
        `when`(bot.preferences.canChooseActivity(BotActivity.PKING)).thenReturn(true)
        InventoryProductionFixtures.inventory(bot, Item(995, 1000))
        `when`(bot.skullIcon).thenReturn(SkullIcon.WHITE)
        val session = PkBotScript(bot, 5.minutes)
        val escape = bot.actionHandler.combat
        doReturn(true).`when`(escape).fleeWilderness()
        val settings = mock(io.luna.LunaSettings::class.java, RETURNS_DEEP_STUBS)
        mockStatic(Luna::class.java).use { luna ->
            luna.`when`<io.luna.LunaSettings> { Luna.settings() }.thenReturn(settings)
            assertFalse(session.enterWild())
            assertFalse(session.allowsPursuit(target))
            assertTrue(session.run())
            verify(escape).fleeWilderness()
            InventoryProductionFixtures.bank(bot, Item(995, 10_000))
            assertTrue(session.allowsPursuit(target))
        }
    }

    @Test fun nativeDefaultDeathScriptUsesTheSameStackPartitionAsRiskPlanning() {
        val position = Position(3100, 3540)
        `when`(bot.position).thenReturn(position)
        InventoryProductionFixtures.inventory(bot, Item(4151), Item(1333), Item(1127), Item(882, 100), Item(995, 1000))
        InventoryProductionFixtures.inventory(bot, Item(5509))
        bot.attributes()[EssencePouch.SMALL.essence] = 3
        bot.attributes()[EssencePouch.SMALL.essenceId] = EssencePouch.PURE_ESSENCE
        `when`(bot.world.economy.getPrice(5509)).thenReturn(20L)
        bot.equipment.set(Equipment.LEGS, Item(1079))
        val risk = assess()
        assertEquals(BigInteger.valueOf(5_735), risk.deathLoss)
        val previousHook = DeathHookHandler.defaultPlayerHook
        val settings = mock(io.luna.LunaSettings::class.java, RETURNS_DEEP_STUBS)
        mockStatic(Luna::class.java).use { luna ->
            luna.`when`<io.luna.LunaSettings> { Luna.settings() }.thenReturn(settings)
            `when`(settings.game().betaMode()).thenReturn(true)
            try {
                assertEquals(risk, BotCombatActionHandler(bot, bot.actionHandler).pkRiskBudget())
                DeathHookHandler.defaultPlayerHook = null
                Class.forName("engine.combat.death.PlayerDeath").getConstructor(Array<String>::class.java)
                    .newInstance(emptyArray<String>() as Any)
                DeathHookHandler.onDeath(bot, null, DeathStage.DEATH)
                assertEquals(listOf(Item(4151), Item(1333), Item(1127)), bot.inventory.filterNotNull())
                assertTrue(bot.equipment.filterNotNull().isEmpty())
                assertEquals(0, bot.attributes()[EssencePouch.SMALL.essence])
                for (betaMode in listOf(false, true)) {
                    `when`(settings.game().betaMode()).thenReturn(betaMode)
                    for (rank in PlayerRights.values()) {
                        bot.inventory.clear()
                        `when`(bot.rights).thenReturn(rank)
                        `when`(bot.skullIcon).thenReturn(SkullIcon.WHITE)
                        InventoryProductionFixtures.inventory(bot, Item(995, 10))
                        val losesItems = betaMode || rank.ordinal < PlayerRights.ADMINISTRATOR.ordinal
                        assertEquals(losesItems, PlayerDeathItems.losesItems(bot, betaMode))
                        assertEquals(if (losesItems) BigInteger.TEN else BigInteger.ZERO,
                            BotCombatActionHandler(bot, bot.actionHandler).pkRiskBudget().deathLoss)
                        DeathHookHandler.onDeath(bot, null, DeathStage.DEATH)
                        assertEquals(if (losesItems) 0 else 10, bot.inventory.computeAmountForId(995),
                            "rank=$rank, betaMode=$betaMode")
                    }
                }
            } finally {
                DeathHookHandler.defaultPlayerHook = previousHook
            }
        }
    }
}
