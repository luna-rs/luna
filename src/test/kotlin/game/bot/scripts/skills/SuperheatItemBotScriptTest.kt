package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.script.productionRuneCosts
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.MagicScriptFactory
import game.bot.scripts.skills.SuperheatItemBotScript.Companion.SuperheatItemData
import game.skill.magic.*
import game.skill.magic.superheatItem.SuperheatItemAction
import game.skill.smithing.BarType
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.model.EntityState
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
class SuperheatItemBotScriptTest {
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
        `when`(it.smithing.staticLevel).thenReturn(99)
        `when`(it.smithing.level).thenReturn(99)
        `when`(it.state).thenReturn(EntityState.ACTIVE)
        `when`(it.spellbook).thenReturn(Spellbook.REGULAR)
        `when`(it.rights).thenReturn(PlayerRights.PLAYER)
        `when`(it.personality.isDextrous).thenReturn(true)
        val inventory = spy(it.inventory)
        for (id in Rune.entries.map { rune -> rune.id } + CombinationRune.entries.map { rune -> rune.id }) {
            doReturn(true).`when`(inventory).stackable(id)
        }
        `when`(it.inventory).thenReturn(inventory)
    }
    private fun script(bot: Bot, type: BarType) =
        InventoryProductionFixtures.active(SuperheatItemBotScript(bot, type, 10.minutes))
    private fun supplies(bot: Bot, type: BarType, casts: Int = 100) {
        type.oreList.forEach { InventoryProductionFixtures.bank(bot, Item(it.id, it.amount * casts)) }
        SuperheatItemAction.RUNES.forEach {
            InventoryProductionFixtures.bank(bot, Item(it.rune.id, it.amount * casts))
        }
    }

    @Test fun allRecipesBalanceOresRuneBudgetsAndPartialBankStock() {
        for (type in BarType.entries) {
            val bot = bot()
            val script = script(bot, type)
            assertFalse(script.isEligible())
            assertTrue(script.bankBatch().isEmpty())
            supplies(bot, type)
            assertTrue(script.isEligible())
            val costs = bot.productionRuneCosts(SuperheatItemAction.RUNES, bankedOnly = true)
            val amount = (28 - costs.size) / type.oreList.sumOf { it.amount }
            assertEquals((type.oreList + costs).map { Item(it.id, it.amount * amount) }, script.bankBatch())
            bot.bank.remove(Item(Rune.NATURE.id, 99))
            assertEquals(type.oreList + costs, script.bankBatch())
            bot.bank.remove(Item(Rune.NATURE.id))
            assertTrue(script.bankBatch().isEmpty())
            InventoryProductionFixtures.bank(bot, Item(Rune.NATURE.id, 100))
            bot.bank.remove(Item(type.oreRequired.first.id, 98))
            assertEquals((type.oreList + costs).map { Item(it.id, it.amount * 2) }, script.bankBatch())
        }
    }

    @Test fun combinationRuneStaffAndFreeCastPlansReserveCorrectSlots() {
        for (type in BarType.entries) {
            val bot = bot()
            type.oreList.forEach { InventoryProductionFixtures.bank(bot, Item(it.id, it.amount * 100)) }
            InventoryProductionFixtures.bank(bot, Item(CombinationRune.LAVA.id, 400), Item(Rune.NATURE.id, 100))
            val script = script(bot, type)
            val oresPerBar = type.oreList.sumOf { it.amount }
            val comboAmount = 26 / oresPerBar
            val comboCosts = listOf(Item(CombinationRune.LAVA.id, 4), Item(Rune.NATURE.id))
            assertTrue(script.isEligible())
            assertEquals((type.oreList + comboCosts).map { Item(it.id, it.amount * comboAmount) }, script.bankBatch())
            `when`(bot.equipment.weapon).thenReturn(Item(Staff.FIRE.ids.first()))
            val staffAmount = 27 / oresPerBar
            assertEquals((type.oreList + Item(Rune.NATURE.id)).map { Item(it.id, it.amount * staffAmount) },
                script.bankBatch())
            `when`(bot.rights).thenReturn(PlayerRights.ADMINISTRATOR)
            val freeAmount = 28 / oresPerBar
            assertEquals(type.oreList.map { Item(it.id, it.amount * freeAmount) }, script.bankBatch())
            `when`(bot.rights).thenReturn(PlayerRights.PLAYER)
            `when`(settings.game().betaMode()).thenReturn(true)
            assertEquals(type.oreList.map { Item(it.id, it.amount * freeAmount) }, script.bankBatch())
            `when`(settings.game().betaMode()).thenReturn(false)
        }
    }

    @Test fun realPlayerActionsConvertEachBarRecipeAndPayOneCastInNormalAndBetaMode() = runBlocking<Unit> {
        val fixtureWorld = world
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { invocation -> scheduler.invoke(invocation.getArgument<Task>(0)); null }
            .`when`(fixtureWorld).schedule(any(Task::class.java))
        try {
            for (beta in listOf(false, true)) for (type in BarType.entries) {
                `when`(settings.game().betaMode()).thenReturn(beta)
                val bot = bot()
                val script = script(bot, type)
                supplies(bot, type)
                val batch = script.bankBatch()
                batch.forEach { InventoryProductionFixtures.inventory(bot, it) }
                val occupied = type.oreList.sumOf { it.amount } * batch.first().amount + if (beta) 0 else 2
                assertEquals(28 - occupied, bot.inventory.computeRemainingSize())
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                val primary = type.oreRequired.first.id
                val before = bot.inventory.computeAmountForId(primary)
                val inventoryHandler = bot.actionHandler.inventory
                doAnswer {
                    SuperheatItemAction(bot, bot.inventory.computeIndexForId(primary)).execute()
                    true
                }.`when`(inventoryHandler).useSpellOnItem(SuperheatItemAction.SPELL_ID, primary, -1)
                assertTrue(script.executeInZone())
                verify(inventoryHandler).useSpellOnItem(SuperheatItemAction.SPELL_ID, primary, -1)
                assertEquals(before - 1, bot.inventory.computeAmountForId(primary))
                assertEquals(type.id, bot.inventory.computeIdForIndex(0))
                assertEquals(1, bot.inventory.computeAmountForId(type.id))
                type.oreRequired.second?.let {
                    assertEquals(it.amount * (before - 1), bot.inventory.computeAmountForId(it.id))
                }
                if (!beta) for (cost in SuperheatItemAction.RUNES) {
                    assertEquals(cost.amount * (before - 1), bot.inventory.computeAmountForId(cost.rune.id))
                }
                verify(bot.magic).addExperience(53.0)
                verify(bot.smithing).addExperience(type.xp)
                assertEquals(0, script.snapshot().failures)
            }
        } finally { doNothing().`when`(fixtureWorld).schedule(any(Task::class.java)) }
    }

    @Test fun factoryModesSelectOwnedBarsBySmithingLevelAndRespectBothSkillRequirements() {
        for (type in BarType.entries) for (training in listOf(true, false)) {
            val bot = bot()
            supplies(bot, type, 1)
            val script = script(bot, type)
            val selected = MagicScriptFactory.getScript(bot, 43, mutableListOf(), training)
            assertInstanceOf(SuperheatItemBotScript::class.java, selected)
            assertEquals(type, (selected as SuperheatItemBotScript).barType)
            assertNull(MagicScriptFactory.getSuperheatScript(bot, 42))
            `when`(bot.magic.staticLevel).thenReturn(42)
            assertFalse(script.isEligible())
            assertNull(MagicScriptFactory.getSuperheatScript(bot, 99))
            `when`(bot.magic.staticLevel).thenReturn(99)
            `when`(bot.smithing.staticLevel).thenReturn(type.level - 1)
            assertFalse(script.isEligible())
            assertNotEquals(type, MagicScriptFactory.getSuperheatScript(bot, 99)?.barType)
            `when`(bot.smithing.staticLevel).thenReturn(99)
            `when`(bot.smithing.level).thenReturn(type.level - 1)
            assertNotEquals(type, MagicScriptFactory.getSuperheatScript(bot, 99)?.barType)
            `when`(bot.smithing.level).thenReturn(99)
            `when`(bot.spellbook).thenReturn(Spellbook.ANCIENT)
            assertNull(MagicScriptFactory.getSuperheatScript(bot, 99))
        }
        assertInstanceOf(SplashBotScript::class.java,
            MagicScriptFactory.getScript(bot(), 43, mutableListOf(), true))
    }

    @Test fun missingMaterialsRequestStockTargetsAndCurrentLevelLossStopsBeforeCasting() = runBlocking<Unit> {
        for (lowerMagic in listOf(true, false)) {
            val bot = bot()
            val type = BarType.RUNE
            assertFalse(script(bot, type).onInit(false))
            for (cost in type.oreList + bot.productionRuneCosts(SuperheatItemAction.RUNES)) {
                verify(bot.preferences).raiseWantedItemTarget(cost.id, 1_000)
            }
            supplies(bot, type)
            val script = script(bot, type)
            script.bankBatch().forEach { InventoryProductionFixtures.inventory(bot, it) }
            if (lowerMagic) `when`(bot.magic.level).thenReturn(42)
            else `when`(bot.smithing.level).thenReturn(84)
            assertTrue(script.executeInZone())
            assertTrue(script.isTerminated())
            verify(bot.actionHandler.inventory, never()).useSpellOnItem(anyInt(), anyInt(), anyInt())
        }
    }

    @Test fun ironSessionsBankUnexpectedCoalRatherThanCastingTheSteelRecipe() = runBlocking<Unit> {
        val bot = bot()
        val script = script(bot, BarType.IRON)
        supplies(bot, BarType.IRON)
        script.bankBatch().forEach { InventoryProductionFixtures.inventory(bot, it) }
        bot.inventory.remove(Item(BarType.IRON.oreRequired.first.id, 2))
        InventoryProductionFixtures.inventory(bot, BarType.STEEL.oreRequired.second!!)
        assertTrue(script.executeInZone())
        verify(bot.actionHandler.inventory, never()).useSpellOnItem(anyInt(), anyInt(), anyInt())
        assertTrue(script.onInventoryBankRequested())
        assertEquals(1, script.snapshot().bankFailures)
        assertTrue(script.bankBatch().none { it.id == BarType.STEEL.oreRequired.second!!.id })
    }

    @Test fun registryAndJsonRestoreEveryRecipeAndInheritedRetryBudgets() = runBlocking<Unit> {
        val previous = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            for (type in BarType.entries) {
                val bot = bot()
                val data = script(bot, type).snapshot().also { it.failures = 3; it.bankFailures = 2 }
                val json = JsonObject().also { data.save(it) }
                val loaded = SuperheatItemData().also { it.load(json) }
                val restored = registry.loadScript(SuperheatItemBotScript::class.qualifiedName, bot, loaded)
                assertInstanceOf(SuperheatItemBotScript::class.java, restored)
                restored as SuperheatItemBotScript
                assertEquals(type, restored.barType)
                assertEquals(10.minutes, restored.duration)
                assertEquals(script(bot, type).zones, restored.zones)
                assertEquals(2, restored.snapshot().bankFailures)
                InventoryProductionFixtures.active(restored)
                assertFalse(restored.onInit(true))
                assertTrue(restored.isTerminated())
            }
        } finally { `when`(world.botManager.scriptManager).thenReturn(previous) }
    }
}
