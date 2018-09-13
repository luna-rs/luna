package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that changes the color of the text on an interface.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class ColorChangeMessageWriter extends GameMessageWriter {

    // TODO Find all color values and make enumeration of standard colors.

    /**
     * The identifier for the text to change the color of.
     */
    private final int id;

    /**
     * The new color to change it to.
     */
    private final int color;

    /**
     * Creates a new {@link ColorChangeMessageWriter}.
     *
     * @param id The identifier for the text to change the color of.
     * @param color The new color to change it to.
     */
    public ColorChangeMessageWriter(int id, int color) {
        this.id = id;
        this.color = color;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(122);
        msg.putShort(id, ByteTransform.A, ByteOrder.LITTLE);
        msg.putShort(color, ByteTransform.A, ByteOrder.LITTLE);
        return msg;
    }
}
