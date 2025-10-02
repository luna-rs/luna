package io.luna.game.model.mob.bot.speech;

import io.luna.game.model.mob.bot.io.BotOutputMessageHandler.ChatColor;
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler.ChatEffect;

/**
 * A model representing an entry of generated bot speech within a stack.
 *
 * @author lare96
 */
public final class BotSpeech {

    /**
     * The text.
     */
    private final String text;

    /**
     * The color.
     */
    private final ChatColor color;

    /**
     * The effect.
     */
    private final ChatEffect effect;

    /**
     * How many ticks this should remain in the stack before being popped. -1 = set by
     * {@link BotSpeechManager}, 0 = speaks on next tick.
     */
    int delay;

    /**
     * Creates a new {@link BotSpeech} with {@code delay} ticks before leaving the stack.
     *
     * @param text The text.
     * @param color The color.
     * @param effect The effect.
     * @param delay How many ticks this should remain in the stack before being popped. -1 = set by
     * {@link BotSpeechManager}, 0 = speaks on next tick.
     */
    public BotSpeech(String text, ChatColor color, ChatEffect effect, int delay) {
        this.text = text;
        this.color = color;
        this.effect = effect;
        this.delay = delay;
    }

    /**
     * Creates a new {@link BotSpeech} with a delay of {@code -1}.
     *
     * @param text The text.
     * @param color The color.
     * @param effect The effect.
     */
    public BotSpeech(String text, ChatColor color, ChatEffect effect) {
        this(text, color, effect, -1);
    }

    /**
     * @return The text.
     */
    public String getText() {
        return text;
    }

    /**
     * @return The color.
     */
    public ChatColor getColor() {
        return color;
    }

    /**
     * @return The effect.
     */
    public ChatEffect getEffect() {
        return effect;
    }

    /**
     * Decrements the delay by {@code 1}.
     */
    void decrementDelay() {
        delay--;
    }

    /**
     * How many ticks this should remain in the stack before being popped. -1 = set by
     * {@link BotSpeechManager}, 0 = speaks on next tick.
     */
    public int getDelay() {
        return delay;
    }
}
