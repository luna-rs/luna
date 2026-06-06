package io.luna.game.model.mob.bot.speech;

import com.google.common.collect.ImmutableList;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.brain.BotEmotion;
import io.luna.game.model.mob.bot.speech.BotGeneralSpeechPool.GeneralSpeech;

import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A {@link BotSpeechPool} implementation that loads and manages generic phrase pools categorized by {@link BotEmotion}
 * context.
 * <p>
 * These pools contain generalized and culture-aware speech categories shared by all bots.
 *
 * @author lare96
 */
public final class BotGeneralSpeechPool extends BotSpeechPool<GeneralSpeech> {

    /**
     * Enumerates the general-purpose categories of bot speech.
     * <p>
     * Each pool is designed to sound era-accurate to 2005 RuneScape, and applies to specific personality, intelligence,
     * and experience brackets.
     */
    public enum GeneralSpeech {
        FILLER,
        NOOB,
        VETERAN;

        /**
         * The immutable list representing all general speech types.
         */
        private static final ImmutableList<GeneralSpeech> ALL = ImmutableList.copyOf(values());

        /**
         * Selects the most contextually appropriate {@link GeneralSpeech} type for a given bot.
         * <p>
         * This method evaluates bot attributes (intelligence, experience, and playtime) to select a realistic speech
         * category.
         *
         * @param bot The bot being evaluated.
         * @return The selected {@link GeneralSpeech} type.
         */
        public static GeneralSpeech selectContextFor(Bot bot) {
            double intelligence = bot.getPersonality().getIntelligence();
            int combatLevel = bot.getCombatLevel();
            long minutesPlayed = bot.getTimePlayed().toMinutes();

            if (minutesPlayed >= 20_000) {
                return GeneralSpeech.VETERAN;
            }
            if (combatLevel < 40 || minutesPlayed < 300) {
                double noobRoll = ThreadLocalRandom.current().nextDouble();
                if (noobRoll < 0.6) {
                    return GeneralSpeech.NOOB;
                } else {
                    return GeneralSpeech.FILLER;
                }
            }
            if (intelligence < 0.4) {
                return GeneralSpeech.NOOB;
            } else {
                return GeneralSpeech.VETERAN;
            }
        }
    }

    /**
     * Creates a new {@link BotGeneralSpeechPool}.
     * <p>
     * Loads general speech definitions from {@code data/game/bots/speech/general.jsonc}.
     */
    public BotGeneralSpeechPool() {
        super(Paths.get("general.jsonc"), GeneralSpeech.class);
    }
}
