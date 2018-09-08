package io.luna.game.model.mob;

/**
 * A model representing a line of player chat.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Chat {

    /**
     * The message.
     */
    private final byte[] message;

    /**
     * The message color.
     */
    private final int color;

    /**
     * The message effects.
     */
    private final int effects;

    /**
     * Creates a new {@link Chat}.
     *
     * @param message The message.
     * @param color The message color.
     * @param effects The message effects.
     */
    public Chat(byte[] message, int color, int effects) {
        this.message = message;
        this.color = color;
        this.effects = effects;
    }

    /**
     * @return The message.
     */
    public byte[] getMessage() {
        return message;
    }

    /**
     * @return The message color.
     */
    public int getColor() {
        return (color & 0xff) << 8;
    }

    /**
     * @return The message effects.
     */
    public int getEffects() {
        return effects & 0xff;
    }
}
