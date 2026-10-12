package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.script.ZonedBotScript
import api.bot.zone.SubZone
import api.predef.*
import game.skill.cooking.cookFood.Food
import io.luna.game.model.Entity
import io.luna.game.model.mob.Npc
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.function.Predicate
import kotlin.time.Duration.Companion.minutes

class CookingBankerBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun script(zone: SubZone = SubZone.ROGUES_DEN): CookFoodBotScript {
        val bot = InventoryProductionFixtures.bot()
        val script = InventoryProductionFixtures.active(CookFoodBotScript(bot, Food.SHARK, 10.minutes, mutableListOf(zone)))
        ZonedBotScript::class.java.getDeclaredField("activeZone").apply { isAccessible = true; set(script, zone) }
        return script
    }

    private fun cached(script: CookFoodBotScript): Entity? =
        ZonedBotScript::class.java.getDeclaredField("cachedBank").apply { isAccessible = true }.get(script) as Entity?

    @Test fun missingBankerRequestsTheExistingFallbackInsteadOfThrowingOrStopping() = runBlocking<Unit> {
        val locator = world.locator
        `when`(locator.findViewableNpcs(eq(SubZone.ROGUES_DEN.inside), any<Predicate<Npc>>())).thenReturn(emptySet())
        val script = script()
        assertTrue(script.onBankRequested(false))
        assertNull(cached(script))
        assertFalse(script.isTerminated())
    }

    @Test fun bankingRequestsUseTheCurrentBankerAndValidateItsId() = runBlocking<Unit> {
        val locator = world.locator
        val first = mock(Npc::class.java)
        val replacement = mock(Npc::class.java)
        val wrong = mock(Npc::class.java)
        `when`(first.id).thenReturn(2271)
        `when`(replacement.id).thenReturn(2271)
        `when`(wrong.id).thenReturn(1)
        var available: Set<Npc> = setOf(first)
        doAnswer {
            val predicate = it.getArgument<Predicate<Npc>>(1)
            assertFalse(predicate.test(wrong))
            assertTrue(predicate.test(first))
            available.filter { npc -> predicate.test(npc) }.toSet()
        }.`when`(locator).findViewableNpcs(eq(SubZone.ROGUES_DEN.inside), any<Predicate<Npc>>())
        val script = script()
        assertTrue(script.onBankRequested(false))
        assertSame(first, cached(script))
        available = setOf(replacement)
        assertTrue(script.onBankRequested(false))
        assertSame(replacement, cached(script))
        available = emptySet()
        assertTrue(script.onBankRequested(false))
        assertNull(cached(script))
        assertFalse(script.isTerminated())
    }

    @Test fun separateBotsDoNotShareABankerFromAnEarlierRequest() = runBlocking<Unit> {
        val locator = world.locator
        val first = mock(Npc::class.java)
        val second = mock(Npc::class.java)
        `when`(locator.findViewableNpcs(eq(SubZone.ROGUES_DEN.inside), any<Predicate<Npc>>()))
            .thenReturn(setOf(first), setOf(second))
        val firstScript = script()
        val secondScript = script()
        assertTrue(firstScript.onBankRequested(false))
        assertTrue(secondScript.onBankRequested(false))
        assertSame(first, cached(firstScript))
        assertSame(second, cached(secondScript))
    }

    @Test fun ordinaryZonesUseInheritedBankLookupWithoutSearchingRoguesDen() = runBlocking<Unit> {
        val locator = world.locator
        clearInvocations(locator)
        val script = script(SubZone.AL_KHARID_BANK)
        assertTrue(script.onBankRequested(false))
        assertNull(cached(script))
        verify(locator, never()).findViewableNpcs(eq(SubZone.ROGUES_DEN.inside), any<Predicate<Npc>>())
    }
}
