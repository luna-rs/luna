package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that displays an item model on a widget.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class WidgetItemModelMessageWriter extends GameMessageWriter {

    /**
     * The widget identifier.
     */
    private final int id;

    /**
     * The item's scale.
     */
    private final int scale;

    /**
     * The item.
     */
    private final int item;

    /**
     * Creates a new {@link WidgetItemModelMessageWriter}.
     *
     * @param id The widget identifier.
     * @param scale The item's scale.
     * @param item The item.
     */
    public WidgetItemModelMessageWriter(int id, int scale, int item) {
        this.id = id;
        this.scale = scale;
        this.item = item;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(246);
        msg.putShort(id, ByteOrder.LITTLE);
        msg.putShort(scale);
        msg.putShort(item);
        return msg;
    }
}
