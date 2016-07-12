package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.MessageWriter;

/**
 * A {@link MessageWriter} implementation that changes the color of the text on an interface. Used for things like updating
 * the quest title on the quest tab with green when you have completed it.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class ColorChangeMessageWriter extends MessageWriter {

    /**
     * The identifier for the text to change the color of.
     */
    private final int id;

    /**
     * The new color to change it to.
     */
    private final int color; // TODO: Find color values, make enumeration of standard colors

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
