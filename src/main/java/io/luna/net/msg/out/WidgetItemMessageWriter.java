package io.luna.net.msg.out;

import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.MessageWriter;

/**
 * A {@link MessageWriter} implementation that displays an item on a widget.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class WidgetItemMessageWriter extends MessageWriter {

    /**
     * The widget identifier.
     */
    private final int id;

    /**
     * The widget index.
     */
    private final int index;

    /**
     * The item.
     */
    private final Item item;

    /**
     * Creates a new {@link WidgetItemMessageWriter}.
     *
     * @param id The widget identifier.
     * @param index The widget index.
     * @param item The item.
     */
    public WidgetItemMessageWriter(int id, int index, Item item) {
        this.id = id;
        this.index = index;
        this.item = item;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(34, MessageType.VAR_SHORT);
        msg.putShort(id);
        msg.put(index);

        if (item == null) {
            msg.putShort(0);
            msg.put(0);
        } else {
            msg.putShort(item.getId() + 1);

            if (item.getAmount() > 254) {
                msg.put(255);
                msg.putShort(item.getAmount());
            } else {
                msg.put(item.getAmount());
            }
        }
        return msg;
    }
}
