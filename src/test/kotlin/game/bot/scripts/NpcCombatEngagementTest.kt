package game.bot.scripts

import api.bot.script.TargetingZonedBotScript
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.CombatAction
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.seconds

class NpcCombatEngagementTest {
    private fun script(bot: Bot, target: Npc): NpcCombatScript {
        val script = NpcCombatScript(bot, 60.seconds, mutableListOf())
        TargetingZonedBotScript::class.java.getDeclaredField("focus").apply {
            isAccessible = true
            set(script, target)
        }
        return script
    }

    @Test fun selectingAttackOptionDoesNotStartCombatBeforeNavigation() = runBlocking {
        val bot = mock(Bot::class.java, RETURNS_DEEP_STUBS)
        val target = mock(Npc::class.java)
        assertEquals(3, script(bot, target).interactionOption(target))
        verify(bot.combat, never()).attack(any())
    }

    @Test fun pendingFirstAttackKeepsFocusEvenBeforeCombatTimerStarts() = runBlocking {
        val bot = mock(Bot::class.java, RETURNS_DEEP_STUBS)
        val target = mock(Npc::class.java)
        `when`(target.isAlive).thenReturn(true)
        `when`(bot.combat.target).thenReturn(target)
        `when`(bot.actions.contains(CombatAction::class.java)).thenReturn(true)
        `when`(bot.combat.lastCombatWith).thenReturn(null)
        assertFalse(script(bot, target).refocus())
        verify(bot.actionHandler.interactions, never()).interact(anyInt(), any())
    }

    @Test fun failedReengagementRequestsAnotherTarget() = runBlocking {
        val bot = mock(Bot::class.java, RETURNS_DEEP_STUBS)
        val target = mock(Npc::class.java)
        `when`(target.isAlive).thenReturn(true)
        `when`(bot.combat.checkMultiCombat(target)).thenReturn(true)
        `when`(bot.actionHandler.interactions.interact(3, target)).thenReturn(false)
        assertTrue(script(bot, target).refocus())
        verify(bot.actionHandler.interactions).interact(3, target)
    }
}
