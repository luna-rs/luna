package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.script.ZonedBotScript
import api.bot.zone.SubZone
import api.bot.zone.Zone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.CraftingScriptFactory
import game.bot.scripts.skills.WeaveTextileBotScript.Companion.TextileData
import game.skill.crafting.textileCrafting.MakeTextileActionItem
import game.skill.crafting.textileCrafting.Textile
import game.skill.crafting.textileCrafting.TextileType
import io.luna.game.action.ActionState
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

@Timeout(180)
class WeaveTextileBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
        private val recipes = Textile.entries.filter { it.type == TextileType.LOOM }
    }

    private fun script(bot: Bot, textile: Textile, zone: SubZone = SubZone.SOUTH_FALADOR_LOOM) =
        InventoryProductionFixtures.active(WeaveTextileBotScript(bot, textile, 10.minutes, mutableListOf(zone))).also {
            ZonedBotScript::class.java.getDeclaredField("activeZone").apply { isAccessible = true; set(it, zone) }
        }

    @Test fun suppliedZoneMetadataOwnsItsRegionsAnchorsAndBothLooms() {
        assertEquals(2000, Zone.TIRANNWN.price)
        assertEquals(Position(2351, 3172), Zone.TIRANNWN.anchor)
        assertEquals(setOf(8756, 9012, 8755, 8754, 8753, 9009, 9265, 9010, 9011), Zone.TIRANNWN.regions)
        assertEquals(listOf(Position(2352, 3166), Position(2354, 3166)), Zone.TIRANNWN.bankAnchors)
        assertEquals(Position(2204, 3253), SubZone.IORWERTH_CAMP.inside)
        assertEquals(Position(3039, 3288), SubZone.SOUTH_FALADOR_LOOM.inside)
        for ((zone, position) in WeaveTextileBotScript.LOOMS) {
            assertTrue(zone.area.contains(position))
            assertTrue(zone.area.contains(zone.inside))
            assertEquals(zone.parent(InventoryProductionFixtures.bot()), Zone.REGIONS[position.region.id])
        }
    }

    @Test fun completeGroupsUseAvailableStockAndCapacityInBothFactoryModes() {
        for (textile in recipes) {
            val bot = InventoryProductionFixtures.bot()
            val script = script(bot, textile)
            assertFalse(script.isEligible())
            InventoryProductionFixtures.bank(bot, Item(textile.rawItem.id, textile.rawItem.amount - 1))
            assertFalse(script.isEligible())
            assertTrue(script.bankBatch().isEmpty())
            InventoryProductionFixtures.bank(bot, Item(textile.rawItem.id, 2))
            assertEquals(listOf(textile.rawItem), script.bankBatch())
            `when`(bot.personality.isDextrous).thenReturn(true)
            `when`(bot.crafting.staticLevel).thenReturn(textile.level)
            for (training in listOf(true, false)) {
                val chosen = CraftingScriptFactory.getProductionScript(bot, textile.level, training)
                assertInstanceOf(WeaveTextileBotScript::class.java, chosen)
                assertEquals(textile, (chosen as WeaveTextileBotScript).textile)
                assertNull(CraftingScriptFactory.getProductionScript(bot, textile.level - 1, training))
            }
            InventoryProductionFixtures.bank(bot, Item(textile.rawItem.id, 100))
            val expected = 28 / textile.rawItem.amount * textile.rawItem.amount
            assertEquals(listOf(Item(textile.rawItem.id, expected)), script.bankBatch())
            `when`(bot.crafting.staticLevel).thenReturn(textile.level - 1)
            assertFalse(script.isEligible())
        }
        assertThrows(IllegalArgumentException::class.java) { script(InventoryProductionFixtures.bot(), Textile.BOWSTRING) }
    }

    @Test fun bothLocationsUseSingleRecipeDialoguesAndRealActionsForPartialAndMaximumBatches() = runBlocking<Unit> {
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { scheduler.invoke(it.getArgument<Task>(0)); null }.`when`(world).schedule(any(Task::class.java))
        try {
            for (textile in recipes) for ((zone, position) in WeaveTextileBotScript.LOOMS) {
                for (crafts in listOf(1, 28 / textile.rawItem.amount)) {
                    val bot = InventoryProductionFixtures.bot()
                    val loom = mock(GameObject::class.java)
                    `when`(loom.id).thenReturn(if (zone == SubZone.IORWERTH_CAMP) 787 else 8717)
                    `when`(loom.position).thenReturn(position)
                    val outside = mock(GameObject::class.java)
                    `when`(outside.id).thenReturn(787)
                    `when`(outside.position).thenReturn(Position(1, 1))
                    val wrong = mock(GameObject::class.java)
                    `when`(wrong.id).thenReturn(2644)
                    `when`(wrong.position).thenReturn(position)
                    val wrongFloor = mock(GameObject::class.java)
                    `when`(wrongFloor.id).thenReturn(787)
                    val upper = Position(position.x, position.y, 1)
                    `when`(wrongFloor.position).thenReturn(upper)
                    val center = zone.area.centerPosition
                    val locator = world.locator
                    doAnswer {
                        val filter = it.getArgument<Predicate<GameObject>>(2)
                        assertTrue(filter.test(loom))
                        assertFalse(filter.test(outside))
                        assertFalse(filter.test(wrong))
                        assertFalse(filter.test(wrongFloor))
                        setOf(loom)
                    }.`when`(locator).findObjects(eq(center), eq(zone.area.tileRadius), any())
                    InventoryProductionFixtures.inventory(bot, Item(textile.rawItem.id, crafts * textile.rawItem.amount))
                    `when`(bot.personality.isDextrous).thenReturn(true)
                    `when`(bot.overlays.player).thenReturn(bot)
                    `when`(bot.overlays.has(MakeItemDialogue::class.java)).thenReturn(true)
                    `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                    `when`(bot.actionHandler.inventory.useItem(textile.rawItem.id).onObject(loom)).thenReturn(true)
                    val widgets = bot.actionHandler.widgets
                    doAnswer {
                        val action = MakeTextileActionItem(bot, textile, crafts)
                        bot.actions.submit(action)
                        repeat(crafts * 2 + 2) { bot.actions.process() }
                        assertEquals(ActionState.COMPLETED, action.state)
                        null
                    }.`when`(widgets).clickMakeItem(0, crafts)
                    val script = script(bot, textile, zone)
                    assertTrue(script.executeInZone())
                    verify(widgets).clickMakeItem(0, crafts)
                    assertEquals(crafts, bot.inventory.computeAmountForId(textile.processedItem.id))
                    assertFalse(bot.inventory.contains(textile.rawItem.id))
                    verify(bot.crafting, times(crafts)).addExperience(textile.exp)
                    assertEquals(0, script.snapshot().failures)
                    assertTrue(script.onBankRequested(false))
                }
            }
        } finally {
            doNothing().`when`(world).schedule(any(Task::class.java))
            `when`(world.locator.findObjects(any(Locatable::class.java), anyInt(), any())).thenReturn(emptySet())
        }
    }

    @Test fun missingMaterialsRequestRealisticTargetsAndUnsafeBotsCannotStart() = runBlocking<Unit> {
        for (textile in recipes) {
            val bot = InventoryProductionFixtures.bot()
            assertFalse(script(bot, textile).onInit(false))
            verify(bot.preferences).raiseWantedItemTarget(textile.rawItem.id, 1000)
            InventoryProductionFixtures.bank(bot, textile.rawItem)
            for (state in 0..2) {
                `when`(bot.health).thenReturn(if (state == 0) 0 else 99)
                `when`(bot.isLocked).thenReturn(state == 1)
                `when`(bot.combat.inCombat()).thenReturn(state == 2)
                assertFalse(script(bot, textile).onInit(false))
            }
        }
    }

    @Test fun loweredCurrentLevelStopsInteractionsAndIncompleteGroupsRequestBanking() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Textile.BASKET.rawItem)
        `when`(bot.crafting.level).thenReturn(Textile.BASKET.level - 1)
        val drained = script(bot, Textile.BASKET)
        drained.executeInZone()
        assertTrue(drained.isTerminated())
        verify(bot.actionHandler.inventory, never()).useItem(anyInt(), anyInt())
        val incomplete = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(incomplete, Item(Textile.BASKET.rawItem.id, 5))
        assertTrue(script(incomplete, Textile.BASKET).onBankRequested(false))
    }

    @Test fun nativeBudgetsPersistAndUnverifiedBankWithdrawalsStop() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Textile.CLOTH.rawItem)
        val script = script(bot, Textile.CLOTH)
        assertTrue(script.onInit(false))
        repeat(3) { assertTrue(script.onBankRequested(false)) }
        assertFalse(script.onBankRequested(false))
        assertEquals(3, script.snapshot().bankFailures)
        assertFalse(InventoryProductionFixtures.active(WeaveTextileBotScript(bot, script.snapshot())).onInit(true))
        val banking = script(bot, Textile.CLOTH)
        assertTrue(banking.onInit(false))
        `when`(bot.actionHandler.banking.withdrawAll(anyList())).thenReturn(true)
        banking.onBankOpen(false)
        verify(bot.actionHandler.banking).clickBankingMode(false)
        assertTrue(banking.isTerminated())
    }

    @Test fun centralRegistrationPreservesRecipeOriginalZonesDurationAndFailureBudgets() {
        val previous = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val data = TextileData().apply {
                recipe = Textile.EMPTY_SACK.name
                duration = 11.minutes
                zones = mutableListOf(SubZone.IORWERTH_CAMP, SubZone.SOUTH_FALADOR_LOOM)
                failures = 2
                bankFailures = 1
            }
            val json = JsonObject()
            data.save(json)
            val loaded = TextileData().apply { load(json) }
            val restored = registry.loadScript(WeaveTextileBotScript::class.qualifiedName, InventoryProductionFixtures.bot(), loaded) as WeaveTextileBotScript
            val snapshot = restored.snapshot()
            assertEquals(data.recipe, snapshot.recipe)
            assertEquals(data.duration, snapshot.duration)
            assertEquals(data.zones, snapshot.zones)
            assertEquals(data.failures, snapshot.failures)
            assertEquals(data.bankFailures, snapshot.bankFailures)
        } finally { `when`(world.botManager.scriptManager).thenReturn(previous) }
    }
}
