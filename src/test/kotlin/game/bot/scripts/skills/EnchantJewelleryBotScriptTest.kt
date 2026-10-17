package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.script.productionRuneCosts
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.MagicScriptFactory
import game.bot.scripts.skills.EnchantJewelleryBotScript.Companion.EnchantJewelleryData
import game.skill.magic.*
import game.skill.magic.enchantJewellery.EnchantJewelleryAction
import game.skill.magic.enchantJewellery.EnchantJewelleryType
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.model.item.Item
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.task.Task
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

@Timeout(90)
class EnchantJewelleryBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() {
            InventoryProductionFixtures.initialize()
            check(SubZone.HOME.area.contains(SubZone.HOME.inside))
        }
    }
    private lateinit var luna: MockedStatic<Luna>
    private lateinit var settings: LunaSettings
    @BeforeEach fun normalMode() {
        settings = mock(LunaSettings::class.java, RETURNS_DEEP_STUBS)
        `when`(settings.game().betaMode()).thenReturn(false)
        luna = mockStatic(Luna::class.java, CALLS_REAL_METHODS)
        luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)
    }
    @AfterEach fun restoreMode() { luna.close() }
    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.magic.staticLevel).thenReturn(99)
        `when`(it.magic.level).thenReturn(99)
        `when`(it.spellbook).thenReturn(Spellbook.REGULAR)
        `when`(it.rights).thenReturn(PlayerRights.PLAYER)
        `when`(it.personality.isDextrous).thenReturn(true)
        val inventory = spy(it.inventory)
        for (id in Rune.entries.map { rune -> rune.id } + CombinationRune.entries.map { rune -> rune.id }) {
            doReturn(true).`when`(inventory).stackable(id)
        }
        `when`(it.inventory).thenReturn(inventory)
    }
    private fun script(bot: Bot, type: EnchantJewelleryType, id: Int) =
        InventoryProductionFixtures.active(EnchantJewelleryBotScript(bot, type, id, 10.minutes))
    private fun supplies(bot: Bot, type: EnchantJewelleryType, id: Int, amount: Int) {
        InventoryProductionFixtures.bank(bot, Item(id, amount))
        type.requirements.filterIsInstance<RuneRequirement>().forEach {
            InventoryProductionFixtures.bank(bot, Item(it.rune.id, it.amount * amount))
        }
    }

    @Test fun everyRecipeUsesBalancedBatchesAndPartialRuneStock() {
        assertEquals(14, EnchantJewelleryBotScript.RECIPES.size)
        for ((type, id) in EnchantJewelleryBotScript.RECIPES) {
            val bot = bot()
            val script = script(bot, type, id)
            assertFalse(script.isEligible())
            assertTrue(script.bankBatch().isEmpty())
            supplies(bot, type, id, 100)
            assertTrue(script.isEligible())
            val costs = bot.productionRuneCosts(type.requirements, bankedOnly = true)
            val amount = 28 - costs.size
            assertEquals(listOf(Item(id, amount)) + costs.map { Item(it.id, it.amount * amount) }, script.bankBatch())
            bot.bank.remove(Item(Rune.COSMIC.id, 98))
            assertEquals(2, script.bankBatch().first().amount)
        }
    }

    @Test fun realPlayerActionsEnchantEveryRecipeInFullInventoriesInNormalAndBetaMode() = runBlocking<Unit> {
        val fixtureWorld = world
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { invocation -> scheduler.invoke(invocation.getArgument<Task>(0)); null }
            .`when`(fixtureWorld).schedule(any(Task::class.java))
        try {
            for (beta in listOf(false, true)) for ((type, id) in EnchantJewelleryBotScript.RECIPES) {
                `when`(settings.game().betaMode()).thenReturn(beta)
                val bot = bot()
                val script = script(bot, type, id)
                supplies(bot, type, id, 100)
                val batch = script.bankBatch()
                batch.forEach { InventoryProductionFixtures.inventory(bot, it) }
                assertTrue(bot.inventory.isFull)
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                val before = bot.inventory.computeAmountForId(id)
                val inventoryHandler = bot.actionHandler.inventory
                doAnswer {
                    EnchantJewelleryAction(bot, bot.inventory.computeIndexForId(id), type).execute()
                    true
                }.`when`(inventoryHandler).useSpellOnItem(type.spellId, id, -1)
                assertTrue(script.executeInZone())
                verify(inventoryHandler).useSpellOnItem(type.spellId, id, -1)
                assertEquals(before - 1, bot.inventory.computeAmountForId(id))
                assertEquals(script.outputId, bot.inventory.computeIdForIndex(0))
                assertTrue(bot.inventory.isFull)
                verify(bot.magic).addExperience(type.xp)
                assertEquals(0, script.snapshot().failures)
                for (cost in batch.drop(1)) {
                    val required = type.requirements.filterIsInstance<RuneRequirement>().single { it.rune.id == cost.id }.amount
                    assertEquals(cost.amount - required, bot.inventory.computeAmountForId(cost.id))
                }
            }
        } finally {
            doNothing().`when`(fixtureWorld).schedule(any(Task::class.java))
        }
    }

    @Test fun combinationStacksCoverBothElementsOnceAndStaffsReserveOnlyCosmicSlots() {
        for ((type, combo, staff) in listOf(
            Triple(EnchantJewelleryType.LVL_5, CombinationRune.MUD, Staff.MUD),
            Triple(EnchantJewelleryType.LVL_6, CombinationRune.LAVA, Staff.LAVA))) {
            val bot = bot()
            val id = type.enchantMap.keys.first()
            InventoryProductionFixtures.bank(bot, Item(id, 100), Item(combo.id, 2_000), Item(Rune.COSMIC.id, 100))
            val script = script(bot, type, id)
            assertTrue(script.isEligible())
            val amount = if (type == EnchantJewelleryType.LVL_5) 15 else 20
            val costs = bot.productionRuneCosts(type.requirements, bankedOnly = true)
            assertEquals(listOf(Item(combo.id, amount), Item(Rune.COSMIC.id)), costs)
            assertEquals(listOf(Item(id, 26), Item(combo.id, amount * 26), Item(Rune.COSMIC.id, 26)), script.bankBatch())
            costs.forEach { InventoryProductionFixtures.inventory(bot, it) }
            assertEquals(costs, Magic.checkRequirements(bot, type.level, type.requirements))
            `when`(bot.equipment.weapon).thenReturn(Item(staff.ids.first()))
            assertEquals(listOf(Item(id, 27), Item(Rune.COSMIC.id, 27)), script.bankBatch())
            assertEquals(listOf(Item(Rune.COSMIC.id)), bot.productionRuneCosts(type.requirements))
        }
    }

    @Test fun bothFactoryModesUseOwnedRecipesAndRejectWrongBookOrInsufficientLevel() {
        for ((type, id) in EnchantJewelleryBotScript.RECIPES) for (training in listOf(true, false)) {
            val bot = bot()
            supplies(bot, type, id, 1)
            val selected = MagicScriptFactory.getScript(bot, type.level, mutableListOf(), training)
            assertInstanceOf(EnchantJewelleryBotScript::class.java, selected)
            selected as EnchantJewelleryBotScript
            assertEquals(type, selected.type)
            assertEquals(id, selected.inputId)
            assertNull(MagicScriptFactory.getEnchantScript(bot, type.level - 1))
            `when`(bot.magic.staticLevel).thenReturn(type.level - 1)
            assertNull(MagicScriptFactory.getEnchantScript(bot, 99))
            `when`(bot.magic.staticLevel).thenReturn(99)
            `when`(bot.spellbook).thenReturn(Spellbook.ANCIENT)
            assertNull(MagicScriptFactory.getEnchantScript(bot, 99))
        }
    }

    @Test fun missingInputsRequestTargetsAndCurrentLevelLossStopsBeforeCasting() = runBlocking<Unit> {
        val type = EnchantJewelleryType.LVL_1
        val id = type.enchantMap.keys.first()
        val bot = bot()
        val missing = script(bot, type, id)
        assertFalse(missing.onInit(false))
        for (cost in listOf(Item(id)) + bot.productionRuneCosts(type.requirements)) {
            verify(bot.preferences).raiseWantedItemTarget(cost.id, 1_000)
        }
        val lowered = script(bot, type, id)
        InventoryProductionFixtures.inventory(bot, Item(id), Item(Rune.WATER.id), Item(Rune.COSMIC.id))
        `when`(bot.magic.level).thenReturn(type.level - 1)
        assertTrue(lowered.executeInZone())
        assertTrue(lowered.isTerminated())
        verify(bot.actionHandler.inventory, never()).useSpellOnItem(anyInt(), anyInt(), anyInt())
        assertThrows(IllegalArgumentException::class.java) { EnchantJewelleryBotScript(bot, type, 567, 10.minutes) }
    }

    @Test fun registryAndJsonRestoreEveryRecipeAndExhaustedRetryBudgets() = runBlocking<Unit> {
        val previous = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            for ((type, id) in EnchantJewelleryBotScript.RECIPES) {
                val bot = bot()
                val data = script(bot, type, id).snapshot().also { it.failures = 3; it.bankFailures = 2 }
                val json = JsonObject().also { data.save(it) }
                val loaded = EnchantJewelleryData().also { it.load(json) }
                val restored = registry.loadScript(EnchantJewelleryBotScript::class.qualifiedName, bot, loaded)
                assertInstanceOf(EnchantJewelleryBotScript::class.java, restored)
                restored as EnchantJewelleryBotScript
                assertEquals(type, restored.type)
                assertEquals(id, restored.inputId)
                assertEquals(10.minutes, restored.duration)
                assertEquals(script(bot, type, id).zones, restored.zones)
                assertEquals(2, restored.snapshot().bankFailures)
                InventoryProductionFixtures.active(restored)
                assertFalse(restored.onInit(true))
                assertTrue(restored.isTerminated())
            }
        } finally { `when`(world.botManager.scriptManager).thenReturn(previous) }
    }
}
