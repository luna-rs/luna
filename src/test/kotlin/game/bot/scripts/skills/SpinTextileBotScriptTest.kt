package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.script.ZonedBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.CraftingScriptFactory
import game.bot.scripts.HarvestBotScript
import game.bot.scripts.skills.SpinTextileBotScript.Companion.TextileData
import game.skill.crafting.textileCrafting.MakeTextileActionItem
import game.skill.crafting.textileCrafting.Textile
import game.skill.crafting.textileCrafting.TextileType
import io.luna.game.action.ActionState
import io.luna.game.model.Entity
import io.luna.game.model.Locatable
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import io.luna.game.model.`object`.GameObject
import io.luna.game.task.Task
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import java.util.function.Predicate
import kotlin.time.Duration.Companion.minutes

@Timeout(90)
class SpinTextileBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
        private val recipes = Textile.entries.filter { it.type == TextileType.SPINNING_WHEEL }
    }

    private fun script(bot: Bot, textile: Textile, zone: SubZone = SubZone.FLAX_SPINNING_MAIN) =
        InventoryProductionFixtures.active(SpinTextileBotScript(bot, 10.minutes, mutableListOf(zone), textile)).also {
            ZonedBotScript::class.java.getDeclaredField("activeZone").apply { isAccessible = true; set(it, zone) }
        }

    @Test fun allWheelRecipesQualifyForBothModesAtTheirLevelsAndUsePartialBatches() {
        for (textile in recipes) {
            val bot = InventoryProductionFixtures.bot()
            val script = script(bot, textile)
            assertFalse(script.isEligible())
            assertTrue(script.bankBatch().isEmpty())
            InventoryProductionFixtures.bank(bot, Item(textile.rawItem.id, 3))
            assertEquals(listOf(Item(textile.rawItem.id, 3)), script.bankBatch())
            InventoryProductionFixtures.bank(bot, Item(textile.rawItem.id, 50))
            assertEquals(listOf(Item(textile.rawItem.id, 28)), script.bankBatch())
            `when`(bot.crafting.staticLevel).thenReturn(textile.level)
            `when`(bot.personality.isDextrous).thenReturn(true)
            assertTrue(script.isEligible())
            for (training in listOf(false, true)) {
                val chosen = CraftingScriptFactory.getProductionScript(bot, textile.level, training)
                assertInstanceOf(SpinTextileBotScript::class.java, chosen)
                assertEquals(textile, (chosen as SpinTextileBotScript).textile)
                assertNull(CraftingScriptFactory.getProductionScript(bot, textile.level - 1, training))
            }
            `when`(bot.crafting.staticLevel).thenReturn(textile.level - 1)
            assertFalse(script.isEligible())
        }
        assertThrows(IllegalArgumentException::class.java) {
            script(InventoryProductionFixtures.bot(), Textile.CLOTH)
        }
    }

    @Test fun woolAndRootsRequestBulkTargetsWhileFlaxStillQueuesHarvesting() = runBlocking<Unit> {
        for (textile in recipes) {
            val bot = InventoryProductionFixtures.bot()
            assertFalse(script(bot, textile).onInit(false))
            if (textile == Textile.BOWSTRING) {
                verify(bot.scriptStack).push(any(HarvestBotScript::class.java))
                verify(bot.preferences, never()).raiseWantedItemTarget(textile.rawItem.id, 1_000)
            } else {
                verify(bot.preferences).raiseWantedItemTarget(textile.rawItem.id, 1_000)
                verify(bot.scriptStack, never()).push(any(HarvestBotScript::class.java))
            }
            InventoryProductionFixtures.inventory(bot, textile.rawItem)
            assertTrue(script(bot, textile).isEligible())
            assertTrue(script(bot, textile).onInit(false))
        }
    }

    @Test fun itemUseOpensOneRecipeOptionAndRealActionsProcessFullAndPartialInventories() = runBlocking<Unit> {
        val fixtureWorld = world
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { scheduler.invoke(it.getArgument<Task>(0)); null }
            .`when`(fixtureWorld).schedule(any(Task::class.java))
        try {
            for (textile in recipes) for (amount in listOf(1, 28)) {
                val bot = InventoryProductionFixtures.bot()
                val zone = SubZone.FLAX_SPINNING_MAIN
                `when`(bot.personality.isDextrous).thenReturn(true)
                `when`(bot.overlays.player).thenReturn(bot)
                `when`(bot.overlays.has(MakeItemDialogue::class.java)).thenReturn(true)
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                val wheel = mock(GameObject::class.java)
                `when`(wheel.id).thenReturn(2644)
                `when`(wheel.position).thenReturn(zone.inside)
                val decoy = mock(GameObject::class.java)
                `when`(decoy.id).thenReturn(-1)
                `when`(decoy.position).thenReturn(zone.inside)
                val outside = mock(GameObject::class.java)
                val distant = Position(1, 1)
                `when`(outside.id).thenReturn(2644)
                `when`(outside.position).thenReturn(distant)
                val locator = fixtureWorld.locator
                val center = zone.area.centerPosition
                doAnswer {
                    val predicate = it.getArgument<Predicate<GameObject>>(2)
                    assertTrue(predicate.test(wheel))
                    assertFalse(predicate.test(decoy))
                    assertFalse(predicate.test(outside))
                    setOf(wheel)
                }.`when`(locator).findObjects(eq(center), eq(zone.area.tileRadius), any())
                `when`(bot.actionHandler.inventory.useItem(textile.rawItem.id).onObject(wheel)).thenReturn(true)
                InventoryProductionFixtures.inventory(bot, Item(textile.rawItem.id, amount))
                val widgets = bot.actionHandler.widgets
                doAnswer {
                    val action = MakeTextileActionItem(bot, textile, amount)
                    bot.actions.submit(action)
                    repeat(amount * 2 + 2) { bot.actions.process() }
                    assertEquals(ActionState.COMPLETED, action.state)
                    null
                }.`when`(widgets).clickMakeItem(0, amount)
                val script = script(bot, textile)
                if (amount == 28) assertTrue(bot.inventory.isFull)
                assertFalse(script.onBankRequested(false))
                assertTrue(script.executeInZone())
                verify(widgets).clickMakeItem(0, amount)
                assertEquals(amount, bot.inventory.computeAmountForId(textile.processedItem.id))
                assertFalse(bot.inventory.contains(textile.rawItem.id))
                verify(bot.crafting, times(amount)).addExperience(textile.exp)
                assertEquals(0, script.snapshot().failures)
                assertTrue(script.onBankRequested(false))
            }
        } finally {
            doNothing().`when`(fixtureWorld).schedule(any(Task::class.java))
            `when`(fixtureWorld.locator.findObjects(any(Locatable::class.java), anyInt(), any())).thenReturn(emptySet())
        }
    }

    @Test fun seersSpinningUsesItsExistingUpstairsWheelAndDescendsBeforeBanking() = runBlocking<Unit> {
        val fixtureWorld = world
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { scheduler.invoke(it.getArgument<Task>(0)); null }
            .`when`(fixtureWorld).schedule(any(Task::class.java))
        try {
            val bot = InventoryProductionFixtures.bot()
            val upstairs = Position(2710, 3471, 1)
            val ladderPosition = Position(2715, 3470, 1)
            Entity::class.java.getDeclaredField("position").apply { isAccessible = true; set(bot, upstairs) }
            `when`(bot.position).thenReturn(upstairs)
            `when`(bot.x).thenReturn(2710)
            `when`(bot.y).thenReturn(3471)
            `when`(bot.z).thenReturn(1)
            `when`(bot.personality.isDextrous).thenReturn(true)
            `when`(bot.overlays.player).thenReturn(bot)
            `when`(bot.overlays.has(MakeItemDialogue::class.java)).thenReturn(true)
            `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
            val wheel = mock(GameObject::class.java)
            `when`(wheel.id).thenReturn(2644)
            val ladder = mock(GameObject::class.java)
            `when`(ladder.id).thenReturn(1747)
            val locator = fixtureWorld.locator
            doAnswer {
                val predicate = it.getArgument<Predicate<GameObject>>(1)
                assertTrue(predicate.test(wheel))
                setOf(wheel)
            }.`when`(locator).findObjectsOnTile(eq(upstairs), any())
            doAnswer {
                val predicate = it.getArgument<Predicate<GameObject>>(1)
                assertTrue(predicate.test(ladder))
                setOf(ladder)
            }.`when`(locator).findObjectsOnTile(eq(ladderPosition), any())
            val recipe = Textile.BALL_OF_WOOL
            InventoryProductionFixtures.inventory(bot, recipe.rawItem)
            `when`(bot.actionHandler.inventory.useItem(recipe.rawItem.id).onObject(wheel)).thenReturn(true)
            val widgets = bot.actionHandler.widgets
            doAnswer {
                val action = MakeTextileActionItem(bot, recipe, 1)
                bot.actions.submit(action)
                repeat(4) { bot.actions.process() }
                null
            }.`when`(widgets).clickMakeItem(0, 1)
            val interactions = bot.actionHandler.interactions
            doAnswer {
                `when`(bot.z).thenReturn(0)
                true
            }.`when`(interactions).interact(1, ladder)
            val script = script(bot, recipe, SubZone.SEERS_VILLAGE_MAIN)
            assertTrue(script.executeInZone())
            assertEquals(1, bot.inventory.computeAmountForId(recipe.processedItem.id))
            assertTrue(script.onBankRequested(false))
            verify(interactions).interact(1, ladder)
            assertEquals(0, bot.z)
        } finally {
            doNothing().`when`(fixtureWorld).schedule(any(Task::class.java))
            `when`(fixtureWorld.locator.findObjectsOnTile(any(Locatable::class.java), any())).thenReturn(emptySet())
        }
    }

    @Test fun missingWheelsExhaustNativeRetriesAndSnapshotsRetainTheRecipeAndBudgets() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        val textile = Textile.MAGIC_STRING
        InventoryProductionFixtures.inventory(bot, textile.rawItem)
        `when`(bot.personality.isDextrous).thenReturn(true)
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
        `when`(world.locator.findObjects(any(Locatable::class.java), anyInt(), any())).thenReturn(emptySet())
        val script = script(bot, textile)
        repeat(3) { script.executeInZone() }
        assertTrue(script.isTerminated())
        assertEquals(3, script.snapshot().failures)
        val json = JsonObject().also { script.snapshot().save(it) }
        val data = TextileData().apply { load(json) }
        assertEquals(textile, data.textile)
        assertFalse(InventoryProductionFixtures.active(SpinTextileBotScript(bot, data)).onInit(true))
    }

    @Test fun currentLevelLossAndUnsafeStatesPreventInteractionAndFinishStopsPendingActions() = runBlocking<Unit> {
        val recipe = Textile.MAGIC_STRING
        for (state in 0..3) {
            val bot = InventoryProductionFixtures.bot()
            InventoryProductionFixtures.inventory(bot, recipe.rawItem)
            when (state) {
                0 -> `when`(bot.crafting.level).thenReturn(recipe.level - 1)
                1 -> `when`(bot.health).thenReturn(0)
                2 -> `when`(bot.isLocked).thenReturn(true)
                3 -> `when`(bot.combat.inCombat()).thenReturn(true)
            }
            val script = script(bot, recipe)
            assertTrue(script.executeInZone())
            verify(bot.actionHandler.inventory, never()).useItem(recipe.rawItem.id, -1)
            assertTrue(bot.inventory.contains(recipe.rawItem.id))
            assertEquals(state == 0, script.isTerminated())
        }
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(recipe.rawItem.id, 2))
        val action = MakeTextileActionItem(bot, recipe, 2)
        bot.actions.submit(action)
        script(bot, recipe).finish()
        assertEquals(ActionState.INTERRUPTED, action.state)
        assertFalse(bot.inventory.contains(recipe.processedItem.id))
    }

    @Test fun bankingRequiresActualUnnotedWithdrawalsAndPersistsItsOwnBudget() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        val recipe = Textile.BALL_OF_WOOL
        InventoryProductionFixtures.bank(bot, Item(recipe.rawItem.id, 3))
        val script = script(bot, recipe)
        assertTrue(script.onInit(false))
        repeat(3) { assertTrue(script.onBankRequested(false)) }
        assertFalse(script.onBankRequested(false))
        assertFalse(InventoryProductionFixtures.active(SpinTextileBotScript(bot, script.snapshot())).onInit(true))
        val requested = listOf(Item(recipe.rawItem.id, 3))
        `when`(bot.actionHandler.banking.withdrawAll(requested)).thenReturn(true)
        val failed = script(bot, recipe)
        failed.onBankOpen(false)
        assertTrue(failed.isTerminated())
        InventoryProductionFixtures.inventory(bot, *requested.toTypedArray())
        val successful = script(bot, recipe)
        assertTrue(successful.onInit(false))
        assertTrue(successful.onBankRequested(false))
        successful.onBankOpen(false)
        assertFalse(successful.isTerminated())
        assertEquals(0, successful.snapshot().bankFailures)
        assertFalse(successful.onBankRequested(false))
        verify(bot.actionHandler.banking, times(2)).clickBankingMode(false)
    }

    @Test fun centralRegistrationRestoresNewRecipesAndLegacyFlaxClassNames() {
        val previous = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val bot = InventoryProductionFixtures.bot()
            val data = TextileData().apply {
                duration = 7.minutes
                zones = mutableListOf(SubZone.FLAX_SPINNING_MAIN)
                textile = Textile.MAGIC_STRING
                failures = 2
                bankFailures = 1
            }
            val json = JsonObject().also { data.save(it) }
            val restored = registry.loadScript(SpinTextileBotScript::class.qualifiedName, bot,
                TextileData().apply { load(json) }) as SpinTextileBotScript
            assertEquals(data.textile, restored.textile)
            assertEquals(data.duration, restored.snapshot().duration)
            assertEquals(data.zones, restored.snapshot().zones)
            assertEquals(data.failures, restored.snapshot().failures)
            assertEquals(data.bankFailures, restored.snapshot().bankFailures)
            val legacy = ZonedBotScriptData().apply { duration = data.duration; zones = data.zones }
            val old = registry.loadScript("game.bot.scripts.skills.SpinFlaxBotScript", bot, legacy) as SpinTextileBotScript
            assertEquals(Textile.BOWSTRING, old.textile)
            assertEquals(legacy.zones, old.snapshot().zones)
            assertEquals(0, old.snapshot().failures)
            assertEquals(Textile.BOWSTRING, TextileData().apply { load(JsonObject().also { legacy.save(it) }) }.textile)
        } finally { `when`(world.botManager.scriptManager).thenReturn(previous) }
    }
}
