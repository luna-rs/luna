package io.luna.game.model.mobile;

/**
 * A container for the data that represents a single line of {@code Chat} text.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Chat {

    /**
     * The message that will be displayed to other {@code Player}s.
     */
    private final byte[] message;

    /**
     * The color of the message.
     */
    private final int color;

    /**
     * The various effects that the message take on.
     */
    private final int effects;

    /**
     * Creates a new {@link Chat}.
     *
     * @param message The message that will be displayed to other {@code Player}s.
     * @param color The color of the message.
     * @param effects The various effects that the message take on.
     */
    public Chat(byte[] message, int color, int effects) {
        this.message = message;
        this.color = color;
        this.effects = effects;
    }

    /**
     * @return The message that will be displayed to other {@code Player}s.
     */
    public byte[] getMessage() {
        return message;
    }

    /**
     * @return The color of the message.
     */
    public int getColor() {
        return color;
    }

    /**
     * @return The various effects that the message take on.
     */
    public int getEffects() {
        return effects;
    }
}
