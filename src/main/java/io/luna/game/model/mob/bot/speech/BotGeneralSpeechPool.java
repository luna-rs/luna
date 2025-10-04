package io.luna.game.model.mob.bot.speech;

import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.speech.BotGeneralSpeechPool.GeneralSpeech;

import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A {@link BotSpeechPool} implementation that loads and manages generic phrase pools
 * categorized by {@link GeneralSpeech} context.
 * <p>
 * These pools contain generalized and culture-aware speech categories shared by all bots.
 *
 * @author lare96
 */
public final class BotGeneralSpeechPool extends BotSpeechPool<GeneralSpeech> {

    /**
     * Enumerates the general-purpose categories of bot speech.
     * <p>
     * Each pool is designed to sound era-accurate to 2005 RuneScape, and applies to specific personality,
     * intelligence, and experience brackets.
     */
    public enum GeneralSpeech {

        /**
         * Random trolling, banter, and spam typical of 2005 Falador Square or Varrock Bank.
         * <p>
         * Bots with an intelligence ratio above {@code 0.8} will generally avoid this category.
         */
        CHAT_FILLER,

        /**
         * Comments related to progression, XP rates, and general grind chatter.
         */
        GRIND_CHATTER,

        /**
         * Lines that reference RuneScape culture, in-jokes, and early community memes.
         */
        RUNESCAPE_CULTURE,

        /**
         * Naive or misinformed questions reflecting early-game confusion.
         * <p>
         * Typically used by low-level, low-intelligence bots to simulate “noob” behavior.
         */
        NOOB_QUESTIONS,

        /**
         * Lines tied to scams, trickery, or dishonest behavior (e.g. “trimming armor”).
         */
        EVIL,

        /**
         * Veteran player speech. More literate, often nostalgic or late-game oriented.
         */
        VETERAN;

        /**
         * Selects the most contextually appropriate {@link GeneralSpeech} type for a given bot.
         * <p>
         * This method evaluates bot attributes (intelligence, experience, kindness, and playtime)
         * to select a realistic speech category.
         *
         * @param bot The bot being evaluated.
         * @return The selected {@link GeneralSpeech} type.
         */
        public static GeneralSpeech selectContextFor(Bot bot) {
            double intelligence = bot.getPersonality().getIntelligence();
            int combatLevel = bot.getCombatLevel();
            long minutesPlayed = bot.getTimePlayed().toMinutes();
            int kindness = (int) (bot.getPersonality().getKindness() * 100);
            int roll = ThreadLocalRandom.current().nextInt(100);

            int evilChance;
            if (kindness >= 80) {
                evilChance = 1;
            } else if (kindness >= 60) {
                evilChance = 3;
            } else if (kindness >= 40) {
                evilChance = 6;
            } else if (kindness >= 20) {
                evilChance = 12;
            } else {
                evilChance = 20;
            }

            if (roll < evilChance) {
                return GeneralSpeech.EVIL;
            }
            if (minutesPlayed >= 20_000) {
                double altRoll = ThreadLocalRandom.current().nextDouble();
                return altRoll < 0.7 ? GeneralSpeech.VETERAN : GeneralSpeech.RUNESCAPE_CULTURE;
            }
            if (combatLevel < 40 && minutesPlayed < 300) {
                double noobRoll = ThreadLocalRandom.current().nextDouble();
                if (noobRoll < 0.6) {
                    return GeneralSpeech.NOOB_QUESTIONS;
                } else if (noobRoll < 0.9) {
                    return GeneralSpeech.CHAT_FILLER;
                } else {
                    return GeneralSpeech.GRIND_CHATTER;
                }
            }

            if (intelligence < 0.4) {
                return GeneralSpeech.CHAT_FILLER;
            } else if (intelligence < 0.8) {
                return Math.random() < 0.3 ? GeneralSpeech.CHAT_FILLER : GeneralSpeech.GRIND_CHATTER;
            } else {
                return Math.random() < 0.2 ? GeneralSpeech.RUNESCAPE_CULTURE : GeneralSpeech.GRIND_CHATTER;
            }
        }
    }

    /**
     * Creates a new {@link BotGeneralSpeechPool}.
     * <p>
     * Loads general speech definitions from {@code data/game/bots/speech/general.json}.
     */
    public BotGeneralSpeechPool() {
        super(Paths.get("general.json"), GeneralSpeech.class);
    }
}
