package io.luna.game.model.mob.bot.speech;

import io.luna.game.model.mob.bot.speech.BotToneSpeechPool.BotSpeechTone;

import java.nio.file.Paths;

/**
 * A {@link BotSpeechPool} implementation that loads pools with the {@link BotSpeechTone} context.
 *
 * @author lare96
 */
public final class BotToneSpeechPool extends BotSpeechPool<BotSpeechTone> {

    /**
     * An enum representing the different types of bot tones.
     *
     * @author lare96
     */
    public enum BotSpeechTone {

        /**
         * Upbeat and friendly. Not afraid to use exclamation marks, generally uses more words.
         */
        NICE,

        /**
         * Indifferent for the most part, but can still care enough to show minor appreciation or concern.
         */
        NEUTRAL,

        /**
         * Trolling, bitter, and/or straight up rude.
         */
        MEAN
    }

    /**
     * Creates a new {@link BotToneSpeechPool}.
     *
     * @param fileName The file to load.
     */
    public BotToneSpeechPool(String fileName) {
        super(Paths.get(fileName), BotSpeechTone.class);
    }
}
