package io.luna.game.model.mob.bot.speech;

import io.luna.game.model.mob.bot.io.BotOutputMessageHandler.ChatColor;
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler.ChatEffect;

/**
 * A model representing a queued bot speech request within a {@link BotSpeechStack}.
 * <p>
 * Each instance holds the finalized chat message text, color, effect, and its tick delay before execution.
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
     * How many ticks this speech will remain in the stack before execution.
     * <ul>
     *     <li>{@code -1}, delay determined automatically by {@link BotSpeechStack}.</li>
     *     <li>{@code 0}, executes on the next tick.</li>
     * </ul>
     */
    int delay;

    /**
     * Creates a new {@link BotSpeech} with a specified delay.
     *
     * @param text The text.
     * @param color The color.
     * @param effect The effect.
     * @param delay The number of ticks before this message is executed. {@code -1} = auto-delay,
     * {@code 0} = next tick.
     */
    public BotSpeech(String text, ChatColor color, ChatEffect effect, int delay) {
        this.text = text;
        this.color = color;
        this.effect = effect;
        this.delay = delay;
    }

    /**
     * Creates a new {@link BotSpeech} with regular text and {@code delay}.
     *
     * @param text The text.
     * @param delay The number of ticks before this message is executed. {@code -1} = auto-delay,
     * {@code 0} = next tick.
     */
    public BotSpeech(String text, int delay) {
        this(text, ChatColor.YELLOW, ChatEffect.NONE, delay);
    }

    /**
     * Creates a new {@link BotSpeech} with regular text and a {@code delay} of -1.
     *
     * @param text The text.
     */
    public BotSpeech(String text) {
        this(text, -1);
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
     * @return How many ticks this speech remains queued before execution.
     */
    public int getDelay() {
        return delay;
    }
}
