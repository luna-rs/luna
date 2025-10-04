package io.luna.game.model.mob.bot.speech;

import io.luna.game.model.mob.bot.speech.BotGeneralSpeechPool.GeneralSpeech;

import java.nio.file.Paths;

/**
 * A {@link BotSpeechPool} implementation that loads pools with the {@link GeneralSpeech} context.
 *
 * @author lare96
 */
public final class BotGeneralSpeechPool extends BotSpeechPool<GeneralSpeech> {

    /**
     * An enum representing the different types of general bot speech. All phrases within these types try their best to
     * be generic and applicable to most situations.
     *
     * @author lare96
     */
    public enum GeneralSpeech {

        /**
         * Random trolling, banter, or spam you may have came across in Falador square or Varrock bank circa-2005. Bots
         * within an intelligence ratio > 0.8 will not pull from this pool by default.
         */
        CHAT_FILLER,

        /**
         * Victories, triumphs, and random babble related to that classic Runescape grind.
         */
        GRIND_CHATTER,

        /**
         * Phrases related to Runescape culture and memes. Veteran bots should pull from this pool.
         */
        RUNESCAPE_CULTURE,

        /**
         * Inexperienced questions and thoughts from noobs (borderline trolling). Low level unintelligent bots should
         * pull from this pool.
         */
        NOOB_QUESTIONS
    }

    /**
     * Creates a new {@link BotGeneralSpeechPool}.
     */
    public BotGeneralSpeechPool() {
        super(Paths.get("general.json"), GeneralSpeech.class);
    }
}
