package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

import java.awt.Color;

/**
 * A {@link GameMessageWriter} implementation that changes the color of the text on an interface.
 *
 * @author lare96
 */
public final class ColorChangeMessageWriter extends GameMessageWriter {

    /**
     * The identifier for the text to change the color of.
     */
    private final int id;

    /**
     * The new color to change it to.
     */
    private final Color color;

    /**
     * Creates a new {@link ColorChangeMessageWriter}.
     *
     * @param id The identifier for the text to change the color of.
     * @param color The new color to change it to.
     */
    public ColorChangeMessageWriter(int id, Color color) {
        this.id = id;
        this.color = color;
    }

    @Override
    public ByteMessage write(Player player) {
        int encodedColor = 0;
        encodedColor += (int) ((double) color.getRed() / 8 * Math.pow(2, 10));
        encodedColor += (int) ((double) color.getGreen() / 8 * Math.pow(2, 5));
        encodedColor += (int) ((double) color.getBlue() / 8 * Math.pow(2, 0));
        ByteMessage msg = ByteMessage.message(122);
        msg.putShort(id, ByteOrder.LITTLE, ValueType.ADD);
        msg.putShort(encodedColor, ByteOrder.LITTLE, ValueType.ADD);
        return msg;
    }
}
