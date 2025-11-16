package io.luna.game.model.mob.block;

import com.google.common.base.MoreObjects;
import io.luna.net.codec.ByteMessage;
import io.luna.util.StringUtils;

/**
 * Represents a single line of player chat, as submitted during a game tick.
 * <p>
 * This model stores the raw packed chat message as well as the optional color and text effect values used by the
 * RuneScape chat protocol.
 * </p>
 *
 * @author lare96
 */
public final class Chat {

    /**
     * The packed message text encoded using {@link StringUtils#packText(String, ByteMessage)}.
     * <p>
     * The client decodes this compressed byte array to display the chat message. This format supports
     * lowercase-only text and special grammar rules used by the legacy 317 protocol.
     * </p>
     */
    private final byte[] message;

    /**
     * The chat color applied to the message (range: 0–11).
     *
     * <p>
     * Values correspond to RuneScape chat colors such as yellow, red, green, cyan, etc. Encoding requires shifting
     * the value by eight bits, which is why {@link #getColor()} performs additional bitwise operations.
     * </p>
     */
    private final int color;

    /**
     * The text effect applied to the message (range: 0–5).
     * <p>
     * Effects include wave, shake, scroll, slide, and flashing text styles.
     * </p>
     */
    private final int effect;

    /**
     * Creates a new {@link Chat} instance.
     *
     * @param message The packed chat message.
     * @param color   The message color (0–11).
     * @param effect  The text effect (0–5).
     */
    public Chat(byte[] message, int color, int effect) {
        this.message = message;
        this.color = color;
        this.effect = effect;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("color", color)
                .add("effects", effect)
                .add("message", StringUtils.unpackText(message))
                .toString();
    }

    /**
     * Returns the packed chat message for transmission.
     *
     * @return The packed byte array representing the message.
     */
    public byte[] getMessage() {
        return message;
    }

    /**
     * Returns the chat color encoded for the update block mask.
     * <p>
     * The 317 protocol expects the chat color to be shifted left by 8 bits,
     * so this method applies:
     * </p>
     *
     * <pre>
     * (color & 0xff) << 8
     * </pre>
     *
     * @return The encoded chat color value.
     */
    public int getColor() {
        return (color & 0xff) << 8;
    }

    /**
     * Returns the chat effect encoded for the protocol.
     * <p>
     * The value is masked with {@code 0xff} to preserve only the lower byte.
     * </p>
     *
     * @return The encoded text-effect value.
     */
    public int getEffect() {
        return effect & 0xff;
    }
}
