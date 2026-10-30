package game.bot.scripts

import api.bot.action.BotCombatThreat
import api.bot.script.InventoryProductionFixtures
import api.combat.death.DeathHookHandler.deathItems
import engine.bot.speech.BotReactions
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.SkillSet
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionType
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionalTrigger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import java.time.Duration

/**
 * Exercises the context chosen by death recovery and completed-kill reactions while preserving their policy rules.
 *
 * @author lare96
 */
class BotCombatThreatContextTest {

    companion object {
        @JvmStatic @BeforeAll fun initialize() = InventoryProductionFixtures.initialize()
    }

    private lateinit var luna: MockedStatic<Luna>

    @BeforeEach fun settings() {
        val settings = mock(LunaSettings::class.java, RETURNS_DEEP_STUBS)
        luna = mockStatic(Luna::class.java)
        luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)
    }

    @AfterEach fun restoreSettings() { luna.close() }

    @Test fun deadNpcKillUsesAchievementDifficultyEvenWhenTheLiveFightWouldBeEasy() {
        val bot = InventoryProductionFixtures.bot()
        val victim = mock(Npc::class.java)
        `when`(victim.health).thenReturn(0)
        `when`(bot.actionHandler.combat.isThreat(victim, BotCombatThreat.Context.COMBAT)).thenReturn(false)
        `when`(bot.actionHandler.combat.isThreat(victim, BotCombatThreat.Context.ACHIEVEMENT)).thenReturn(true)

        BotReactions.reactToKill(bot, victim)

        verify(bot.actionHandler.combat).isThreat(victim, BotCombatThreat.Context.ACHIEVEMENT)
        verify(bot.actionHandler.combat, never()).isThreat(victim, BotCombatThreat.Context.COMBAT)
        verify(bot.emotions, times(2)).add(any(EmotionalTrigger::class.java))
    }

    @Test fun finishingAnOrdinaryNpcInDangerDoesNotTurnItIntoAnAchievement() {
        val bot = InventoryProductionFixtures.bot()
        val victim = mock(Npc::class.java)
        `when`(bot.health).thenReturn(1)
        `when`(victim.health).thenReturn(0)
        `when`(bot.actionHandler.combat.isThreat(victim, BotCombatThreat.Context.COMBAT)).thenReturn(true)
        `when`(bot.actionHandler.combat.isThreat(victim, BotCombatThreat.Context.ACHIEVEMENT)).thenReturn(false)

        BotReactions.reactToKill(bot, victim)

        verify(bot.actionHandler.combat).isThreat(victim, BotCombatThreat.Context.ACHIEVEMENT)
        verify(bot.emotions, never()).add(any(EmotionalTrigger::class.java))
    }

    @Test fun playerKillsStillRewardTheBotWithoutAThreatAssessment() {
        val bot = InventoryProductionFixtures.bot()
        val victim = mock(Player::class.java)

        BotReactions.reactToKill(bot, victim)

        verifyNoInteractions(bot.actionHandler.combat)
        verify(bot.emotions, times(2)).add(any(EmotionalTrigger::class.java))
    }

    private fun recoveryBot(): Bot = InventoryProductionFixtures.bot().also { bot ->
        val position = Position(3200, 3200)
        `when`(bot.position).thenReturn(position)
        `when`(bot.timePlayed).thenReturn(Duration.ZERO)
        `when`(bot.skills).thenReturn(SkillSet(bot))
        val dropped = mock(GroundItem::class.java)
        `when`(dropped.state).thenReturn(EntityState.ACTIVE)
        `when`(dropped.isWithinDistance(position, Position.VIEWING_DISTANCE)).thenReturn(true)
        `when`(dropped.toItem()).thenReturn(Item(995, 1000))
        `when`(bot.world.economy.getTotalPrice(Item(995, 1000))).thenReturn(1000L)
        bot.deathItems += dropped
    }

    @Suppress("UNCHECKED_CAST")
    private fun decide(script: DeathReflexScript, killer: Mob?): Pair<Boolean, String> {
        val method = DeathReflexScript::class.java.getDeclaredMethod("decide", Position::class.java, Mob::class.java)
        method.isAccessible = true
        return method.invoke(script, Position(3200, 3200), killer) as Pair<Boolean, String>
    }

    @Test fun recoveryUsesRecoveryDifficultyInsteadOfTheKillersRemainingHealth() {
        val bot = recoveryBot()
        val killer = mock(Npc::class.java)
        `when`(killer.health).thenReturn(1)
        `when`(bot.actionHandler.combat.isThreat(killer, BotCombatThreat.Context.COMBAT)).thenReturn(false)
        `when`(bot.actionHandler.combat.isThreat(killer, BotCombatThreat.Context.RECOVERY)).thenReturn(true)

        val decision = decide(DeathReflexScript(bot), killer)

        assertFalse(decision.first)
        assertTrue(decision.second.contains("too strong"))
        verify(bot.actionHandler.combat).isThreat(killer, BotCombatThreat.Context.RECOVERY)
        verify(bot.actionHandler.combat, never()).isThreat(killer, BotCombatThreat.Context.COMBAT)
    }

    @Test fun greedyRecoveryStillOverridesTheThreatForAValuableReachablePile() {
        val bot = recoveryBot()
        val killer = mock(Npc::class.java)
        `when`(bot.actionHandler.combat.isThreat(killer, BotCombatThreat.Context.RECOVERY)).thenReturn(true)
        `when`(bot.emotions.isFeeling(EmotionType.GREEDY)).thenReturn(true)

        val decision = decide(DeathReflexScript(bot), killer)

        assertTrue(decision.first)
        assertTrue(decision.second.startsWith("greedy"))
    }

    @Test fun fearStillTakesPrecedenceOverGreedAndRecklessnessForRecovery() {
        val bot = recoveryBot()
        val killer = mock(Npc::class.java)
        `when`(bot.actionHandler.combat.isThreat(killer, BotCombatThreat.Context.RECOVERY)).thenReturn(true)
        `when`(bot.emotions.isFeeling(EmotionType.SCARED)).thenReturn(true)
        `when`(bot.emotions.isFeeling(EmotionType.GREEDY)).thenReturn(true)
        `when`(bot.personality.isStupidlyConfident).thenReturn(true)

        val decision = decide(DeathReflexScript(bot), killer)

        assertFalse(decision.first)
        assertTrue(decision.second.startsWith("too scared"))
    }

    @Test fun missingDeathItemsStillPreventRecoveryBeforeAssessingTheKiller() {
        val bot = recoveryBot()
        bot.deathItems.clear()
        val killer = mock(Npc::class.java)
        `when`(bot.emotions.isFeeling(EmotionType.GREEDY)).thenReturn(true)
        `when`(bot.personality.isStupidlyConfident).thenReturn(true)

        val decision = decide(DeathReflexScript(bot), killer)

        assertFalse(decision.first)
        assertEquals("nothing I dropped is left there", decision.second)
        verifyNoInteractions(bot.actionHandler.combat)
    }
}
