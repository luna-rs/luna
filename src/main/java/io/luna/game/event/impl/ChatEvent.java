package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.mobile.Player;

/**
 * An {@link Event} implementation sent whenever an {@link Player} talks.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ChatEvent extends Event {

    /**
     * The chat effects.
     */
    private final int effects;

    /**
     * The chat color.
     */
    private final int color;

    /**
     * The length of the message sent.
     */
    private final int textLength;

    /**
     * The actual message sent.
     */
    private final byte[] text;

    /**
     * Creates a new {@link ChatEvent}.
     *
     * @param effects The chat effects.
     * @param color The chat color.
     * @param textLength The length of the message sent.
     * @param text The actual message sent.
     */
    public ChatEvent(int effects, int color, int textLength, byte[] text) {
        this.effects = effects;
        this.color = color;
        this.textLength = textLength;
        this.text = text;
    }

    /**
     * @return The chat effects.
     */
    public int getEffects() {
        return effects;
    }

    /**
     * @return The chat color.
     */
    public int getColor() {
        return color;
    }

    /**
     * @return The length of the message sent.
     */
    public int getTextLength() {
        return textLength;
    }

    /**
     * @return The actual message sent.
     */
    public byte[] getText() {
        return text;
    }
}
