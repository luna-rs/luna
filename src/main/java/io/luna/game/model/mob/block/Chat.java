package io.luna.game.model.mob.block;

import com.google.common.base.MoreObjects;
import io.luna.util.StringUtils;

/**
 * A model representing a line of player chat.
 *
 * @author lare96
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
    private final int effect;

    /**
     * Creates a new {@link Chat}.
     *
     * @param message The message.
     * @param color The message color.
     * @param effect The message effects.
     */
    public Chat(byte[] message, int color, int effect) {
        this.message = message;
        this.color = color;
        this.effect = effect;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("color", color).
                add("effects", effect).
                add("message", StringUtils.unpackText(message)).toString();
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
    public int getEffect() {
        return effect & 0xff;
    }
}
