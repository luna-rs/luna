package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.bot.zone.Zone
import api.event.Matcher
import api.predef.scriptMatchers
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import api.predef.world
import api.predef.fishing
import com.google.gson.JsonParser
import game.skill.fishing.catchFish.CatchFishAction
import game.skill.fishing.catchFish.Fish
import game.skill.fishing.catchFish.FishingSpot
import game.skill.fishing.catchFish.Tool
import io.luna.game.event.Event
import io.luna.game.event.EventMatcherListener
import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.task.Task
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

/**
 * Exercises karambwan routing and repeated casts using the real vessel-loading listener and fishing action.
 *
 * @author lare96
 */
class KarambwanFishingTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun initialize() = InventoryProductionFixtures.initialize()
    }

    @Test
    fun zoneContainsBothLostCity377MainlandSpawns() {
        val zone = SubZone.KARAMJA_KARAMBWAN_FISHING
        val expected = setOf(Position(2899, 3119), Position(2912, 3119))
        val positions = Files.newBufferedReader(Path.of("data/game/world/npc_spawns.jsonc")).use { reader ->
            JsonParser.parseReader(reader).asJsonArray.map { it.asJsonObject }
                .filter { it["id"].asInt in setOf(1176, 1177) }
                .map { it.getAsJsonObject("position") }
                .map { Position(it["x"].asInt, it["y"].asInt, it["z"].asInt) }
                .filter { it in zone.area }.toSet()
        }
        assertEquals(expected, positions)
        assertTrue(zone.inside in zone.area)
        assertTrue(expected.all { it.regionId in Zone.KARAMJA.regions })
        assertTrue(setOf(1176, 1177).all { it in FishBotScript.FIRST_CLICK_SPOTS[Tool.KARAMBWAN_VESSEL] })
    }

    @Test
    fun reloadsBetweenCastsAndBanksOnlyAfterBaitRunsOut() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.fishing.staticLevel).thenReturn(65)
        `when`(bot.fishing.level).thenReturn(65)
        val script = FishBotScript(bot, Tool.KARAMBWAN_VESSEL, 1.minutes,
            mutableListOf(SubZone.KARAMJA_KARAMBWAN_FISHING))
        InventoryProductionFixtures.inventory(bot, Item(Tool.EMPTY_KARAMBWAN_VESSEL), Item(Fish.KARAMBWANJI.id, 2))
        assertTrue(script.onInit(false))
        assertFalse(script.onBankRequestedTargeting(true))
        assertEquals(Tool.EMPTY_KARAMBWAN_VESSEL, script.emergencyTool().id)
        val actionsField = Matcher::class.java.getDeclaredField("actions").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val actions = Matcher.ALL.values.map {
            actionsField.get(it) as Multimap<Any, EventMatcherListener<Event>>
        }
        val savedActions = actions.map { ArrayListMultimap.create(it) }
        val first = scriptMatchers.size
        Class.forName("game.skill.fishing.catchFish.StartFishing").getConstructor(Array<String>::class.java)
            .newInstance(emptyArray<String>() as Any)
        @Suppress("UNCHECKED_CAST")
        val itemActions = actionsField.get(Matcher.get<ItemOnItemEvent, Pair<Int, Int>>())
            as Multimap<Pair<Int, Int>, EventMatcherListener<ItemOnItemEvent>>
        val listener = itemActions[Fish.KARAMBWANJI.id to Tool.EMPTY_KARAMBWAN_VESSEL].last()
        val loading = bot.actionHandler.inventory.useItem(Fish.KARAMBWANJI.id)
        `when`(loading.onItem(Tool.EMPTY_KARAMBWAN_VESSEL)).thenAnswer {
            val event = ItemOnItemEvent(bot, Fish.KARAMBWANJI.id, Tool.EMPTY_KARAMBWAN_VESSEL,
                bot.inventory.computeIndexForId(Fish.KARAMBWANJI.id),
                bot.inventory.computeIndexForId(Tool.EMPTY_KARAMBWAN_VESSEL), 3214, 3214)
            listener.apply(event)
            true
        }
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { invocation -> scheduler.invoke(invocation.getArgument<Task>(0)); null }
            .`when`(world).schedule(any(Task::class.java))
        try {
            val spot = mock(Npc::class.java)
            `when`(spot.id).thenReturn(1176)
            `when`(spot.position).thenReturn(Position(2899, 3119))
            repeat(2) { cast ->
                assertEquals(1, script.interactionOption(spot))
                assertEquals(1 - cast, bot.inventory.computeAmountForId(Fish.KARAMBWANJI.id))
                assertFalse(script.onBankRequestedTargeting(true), "A loaded vessel is still the carried tool")
                script.onExecuteInZone(false)
                assertFalse(script.refocus())
                assertFalse(banking(script), "The last loaded bait can still be cast")
                val action = CatchFishAction(bot, spot, FishingSpot.KARAMBWAN, Tool.KARAMBWAN_VESSEL)
                repeat(5) { assertFalse(action.run()) }
                assertTrue(action.run())
                assertEquals(1, bot.inventory.computeAmountForId(Tool.EMPTY_KARAMBWAN_VESSEL))
                assertFalse(Tool.KARAMBWAN_VESSEL.id in bot.inventory)
            }
            assertNull(script.interactionOption(spot))
            assertTrue(banking(script))
            verify(loading, times(2)).onItem(Tool.EMPTY_KARAMBWAN_VESSEL)
            assertEquals(0, bot.bank.size())
        } finally {
            doNothing().`when`(world).schedule(any(Task::class.java))
            actions.zip(savedActions).forEach { (current, saved) -> current.clear(); current.putAll(saved) }
            scriptMatchers.subList(first, scriptMatchers.size).clear()
        }
    }

    @Test
    fun bankingWithdrawsRawKarambwanjiAndLoadedStartDoesNotCreateAnotherVessel() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.fishing.staticLevel).thenReturn(65)
        InventoryProductionFixtures.inventory(bot, Item(Tool.KARAMBWAN_VESSEL.id))
        InventoryProductionFixtures.bank(bot, Item(Fish.KARAMBWANJI.id, 10))
        val script = FishBotScript(bot, Tool.KARAMBWAN_VESSEL, 1.minutes,
            mutableListOf(SubZone.KARAMJA_KARAMBWAN_FISHING))
        assertTrue(script.onInit(false))
        assertFalse(Tool.EMPTY_KARAMBWAN_VESSEL in bot.bank)
        script.onBankOpenSkilling(true)
        verify(bot.actionHandler.banking).withdrawAll(Fish.KARAMBWANJI.id)
    }

    @Test
    fun withdrawsAnAlreadyLoadedVesselWithoutProvisioningAnEmptyOne() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.fishing.staticLevel).thenReturn(65)
        InventoryProductionFixtures.bank(bot, Item(Tool.KARAMBWAN_VESSEL.id), Item(Fish.KARAMBWANJI.id, 10))
        `when`(bot.actionHandler.has(Tool.KARAMBWAN_VESSEL.id)).thenReturn(true)
        `when`(bot.actionHandler.shop.obtainWantedItem(Tool.EMPTY_KARAMBWAN_VESSEL)).thenReturn(false)
        `when`(bot.actionHandler.banking.withdraw(Item(Tool.KARAMBWAN_VESSEL.id))).thenReturn(true)
        val script = FishBotScript(bot, Tool.KARAMBWAN_VESSEL, 1.minutes,
            mutableListOf(SubZone.KARAMJA_KARAMBWAN_FISHING))
        assertTrue(script.onInit(false))
        assertFalse(Tool.EMPTY_KARAMBWAN_VESSEL in bot.bank)
        assertTrue(script.onBankRequestedTargeting(true))
        script.onBankOpen(true)
        verify(bot.actionHandler.banking).withdraw(Item(Tool.KARAMBWAN_VESSEL.id))
        verify(bot.actionHandler.banking).withdrawAll(Fish.KARAMBWANJI.id)
    }

    @Test
    fun rejectedVesselLoadingDoesNotAllowAFishingClick() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(Tool.EMPTY_KARAMBWAN_VESSEL), Item(Fish.KARAMBWANJI.id))
        val script = FishBotScript(bot, Tool.KARAMBWAN_VESSEL, 1.minutes,
            mutableListOf(SubZone.KARAMJA_KARAMBWAN_FISHING))
        assertNull(script.interactionOption(mock(Npc::class.java)))
        assertEquals(1, bot.inventory.computeAmountForId(Fish.KARAMBWANJI.id))
        assertTrue(Tool.EMPTY_KARAMBWAN_VESSEL in bot.inventory)
    }

    private fun banking(script: FishBotScript): Boolean =
        api.bot.script.ZonedBotScript::class.java.getDeclaredField("forceBanking").run {
            isAccessible = true
            getBoolean(script)
        }
}
