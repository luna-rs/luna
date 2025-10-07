package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

import java.awt.*;

/**
 * A {@link GameMessageWriter} implementation that changes the color of a widget.
 *
 * @author lare96
 */
public final class WidgetColorMessageWriter extends GameMessageWriter {

    /**
     * The identifier for the text to change the color of.
     */
    private final int id;

    /**
     * The new color to change it to.
     */
    private final Color color;

    /**
     * Creates a new {@link WidgetColorMessageWriter}.
     *
     * @param id The identifier for the text to change the color of.
     * @param color The new color to change it to.
     */
    public WidgetColorMessageWriter(int id, Color color) {
        this.id = id;
        this.color = color;
    }

    @Override
    public ByteMessage write(Player player) {
        // TODO This doesn't work correctly.
        int encodedColor = 0;
        encodedColor += (int) ((double) color.getRed() / 8 * Math.pow(2, 10));
        encodedColor += (int) ((double) color.getGreen() / 8 * Math.pow(2, 5));
        encodedColor += (int) ((double) color.getBlue() / 8 * Math.pow(2, 0));
        ByteMessage msg = ByteMessage.message(218);
        msg.putShort(id);
        msg.putShort(encodedColor, ValueType.ADD);
        return msg;
    }
}
