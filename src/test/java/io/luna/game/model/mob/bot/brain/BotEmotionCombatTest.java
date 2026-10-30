package io.luna.game.model.mob.bot.brain;

import game.bot.scripts.combat.CombatBotScript.InitialState;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.bot.Bot;
import io.luna.util.RandomUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Verifies that threat classification leaves intelligence and confidence in control of the initial combat response.
 *
 * @author lare96
 */
class BotEmotionCombatTest {

    @Test
    void dangerousOpponentsStillAllowRecklessBotsToStandTheirGround() {
        Bot bot = mock(Bot.class, RETURNS_DEEP_STUBS);
        Mob attacker = mock(Mob.class);
        when(bot.getActionHandler().getCombat().isThreat(attacker)).thenReturn(true);
        when(bot.getPersonality().getConfidence()).thenReturn(1.0);
        when(bot.getPersonality().getIntelligence()).thenReturn(0.0);
        try (MockedStatic<RandomUtils> random = mockStatic(RandomUtils.class)) {
            random.when(() -> RandomUtils.roll(0.0)).thenReturn(false);

            assertEquals(InitialState.ATTACK, new BotEmotion(bot).getCombatResponse(attacker));

            random.verify(() -> RandomUtils.roll(0.0));
        }
    }

    @Test
    void intelligentTimidBotsStillRunFromDangerousOpponents() {
        Bot bot = mock(Bot.class, RETURNS_DEEP_STUBS);
        Mob attacker = mock(Mob.class);
        when(bot.getActionHandler().getCombat().isThreat(attacker)).thenReturn(true);
        when(bot.getPersonality().getConfidence()).thenReturn(0.0);
        when(bot.getPersonality().getIntelligence()).thenReturn(1.0);
        try (MockedStatic<RandomUtils> random = mockStatic(RandomUtils.class)) {
            random.when(() -> RandomUtils.roll(1.0)).thenReturn(true);

            assertEquals(InitialState.RUN, new BotEmotion(bot).getCombatResponse(attacker));

            random.verify(() -> RandomUtils.roll(1.0));
        }
    }

    @Test
    void confidenceStillDrivesResponseToAnOrdinaryOpponent() {
        Bot bot = mock(Bot.class, RETURNS_DEEP_STUBS);
        Mob attacker = mock(Mob.class);
        when(bot.getActionHandler().getCombat().isThreat(attacker)).thenReturn(false);
        when(bot.getPersonality().getConfidence()).thenReturn(1.0);
        when(bot.getPersonality().getIntelligence()).thenReturn(0.0);
        try (MockedStatic<RandomUtils> random = mockStatic(RandomUtils.class)) {
            random.when(() -> RandomUtils.roll(0.70)).thenReturn(true);

            assertEquals(InitialState.ATTACK, new BotEmotion(bot).getCombatResponse(attacker));

            random.verify(() -> RandomUtils.roll(0.70));
        }
    }
}
