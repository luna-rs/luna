package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.script.ZonedBotScript
import api.event.Matcher
import api.bot.zone.SubZone
import api.predef.*
import game.skill.crafting.hideTanning.Hide
import game.skill.crafting.hideTanning.TanInterface
import io.luna.game.event.EventMatcherListener
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcSecondClickEvent
import io.luna.game.model.item.Item
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.mob.Npc
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import java.util.function.Predicate
import kotlin.time.Duration.Companion.minutes

class TanningNpcBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() {
            InventoryProductionFixtures.initialize()
            if (WidgetDefinition.ALL.get(14670).isEmpty) {
                val definition = mock(WidgetDefinition::class.java)
                `when`(definition.id()).thenReturn(14670)
                WidgetDefinition.ALL.storeDefinition(definition)
            }
        }
    }

    private val zone = SubZone.AL_KHARID_BANK
    private val cachedTanner = TanHideBotScript::class.java.getDeclaredField("tannerNpc").apply { isAccessible = true }

    private fun npc(id: Int): Npc = mock(Npc::class.java).also { `when`(it.id).thenReturn(id) }

    private suspend fun script(): TanHideBotScript {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.crafting.staticLevel).thenReturn(1)
        `when`(bot.personality.isDextrous).thenReturn(true)
        val inventory = spy(bot.inventory)
        doReturn(true).`when`(inventory).stackable(995)
        `when`(bot.inventory).thenReturn(inventory)
        InventoryProductionFixtures.inventory(bot, Item(Hide.SOFT_LEATHER.hide, 3), Item(995, 6))
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        return InventoryProductionFixtures.active(TanHideBotScript(bot, 8.minutes)).also {
            ZonedBotScript::class.java.getDeclaredField("activeZone").apply { isAccessible = true; set(it, zone) }
            it.withdraw()
        }
    }

    private suspend fun withNpcs(available: () -> List<Npc>, action: suspend () -> Unit) {
        val locator = world.locator
        doAnswer {
            val predicate = it.getArgument<Predicate<Npc>>(2)
            available().filter { candidate -> predicate.test(candidate) }.toSet()
        }.`when`(locator).findNpcs(eq(zone.area.centerPosition), eq(zone.area.tileRadius), any<Predicate<Npc>>())
        try { action() }
        finally {
            doReturn(emptySet<Npc>()).`when`(locator)
                .findNpcs(eq(zone.area.centerPosition), eq(zone.area.tileRadius), any<Predicate<Npc>>())
        }
    }

    @ParameterizedTest @ValueSource(ints = [804, 2824, 1041])
    fun selectsEachImplementedTannerAmongUnsupportedNpcs(id: Int) = runBlocking<Unit> {
        val unsupported = npc(9999)
        val unrelated = npc(1)
        val supported = npc(id)
        val script = script()
        withNpcs({ listOf(unsupported, unrelated, supported) }) {
            assertTrue(script.executeInZone())
            assertSame(supported, cachedTanner.get(script))
            assertEquals(1, script.snapshot().failures)
            assertFalse(script.isTerminated())
        }
    }

    @Test fun unsupportedTannerDoesNotReceiveAnInteraction() = runBlocking<Unit> {
        val unsupported = npc(9999)
        val script = script()
        withNpcs({ listOf(unsupported) }) {
            assertFalse(script.executeInZone())
            assertTrue(script.isTerminated())
            assertNull(cachedTanner.get(script))
            verifyNoInteractions(script.bot.actionHandler.interactions)
        }
    }

    @Test fun native1041TalkAndTradeHandlersAreRegisteredAndTradeOpensTanning() {
        val start = scriptMatchers.size
        Class.forName("game.skill.crafting.hideTanning.TanHides")
            .getConstructor(Array<String>::class.java).newInstance(emptyArray<String>() as Any)
        val bot = InventoryProductionFixtures.bot()
        val tanner = npc(1041)
        val trade = NpcSecondClickEvent(bot, tanner)
        assertTrue(Matcher.get<NpcFirstClickEvent, Int>(NpcFirstClickEvent::class).has(NpcFirstClickEvent(bot, tanner)))
        assertTrue(Matcher.get<NpcSecondClickEvent, Int>(NpcSecondClickEvent::class).has(trade))
        assertEquals(6, scriptMatchers.size - start)
        @Suppress("UNCHECKED_CAST")
        val handler = scriptMatchers.last() as EventMatcherListener<NpcSecondClickEvent>
        handler.apply(trade)
        verify(bot.overlays).open(any(TanInterface::class.java))
    }

    @Test fun failedInteractionSearchesAgainAndKeepsTheFailureBudget() = runBlocking<Unit> {
        val first = npc(804)
        val replacement = npc(2824)
        var available = listOf(first)
        val script = script()
        val widgets = script.bot.actionHandler.widgets
        val interactions = script.bot.actionHandler.interactions
        `when`(widgets.clickCloseInterface()).thenReturn(true)
        `when`(interactions.interact(2, first)).thenReturn(false)
        `when`(interactions.interact(2, replacement)).thenReturn(false)
        withNpcs({ available }) {
            script.executeInZone()
            assertNull(cachedTanner.get(script))
            assertEquals(1, script.snapshot().failures)
            available = listOf(replacement)
            script.executeInZone()
            assertNull(cachedTanner.get(script))
            assertEquals(2, script.snapshot().failures)
            assertFalse(script.isTerminated())
            verify(interactions).interact(2, first)
            verify(interactions).interact(2, replacement)
        }
    }
}
