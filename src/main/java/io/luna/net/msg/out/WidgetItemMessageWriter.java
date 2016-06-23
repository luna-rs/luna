package io.luna.net.msg.out;

import io.luna.game.model.item.Item;
import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.MessageWriter;

/**
 * An {@link MessageWriter} implementation that displays a single {@link Item} on a widget.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class WidgetItemMessageWriter extends MessageWriter {

    /**
     * The identifier for the widget that the {@link Item} will be displayed on.
     */
    private final int id;

    /**
     * The index on the widget that the {@link Item} will be displayed on.
     */
    private final int index;

    /**
     * The {@link Item} that will be displayed on the widget.
     */
    private final Item item;

    /**
     * Creates a new {@link WidgetItemMessageWriter}.
     *
     * @param id The identifier for the widget that the {@link Item} will be displayed on.
     * @param index The index on the widget that the {@link Item} will be displayed on.
     * @param item The {@link Item} that will be displayed on the widget.
     */
    public WidgetItemMessageWriter(int id, int index, Item item) {
        this.id = id;
        this.index = index;
        this.item = item;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(34, MessageType.VARIABLE_SHORT);
        msg.putShort(id);
        msg.put(index);
        msg.putShort(item.getId() + 1);

        if (item.getAmount() > 254) {
            msg.put(255);
            msg.putShort(item.getAmount());
        } else {
            msg.put(item.getAmount());
        }
        return msg;
    }
}
