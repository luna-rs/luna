package io.luna.net.msg.out;

import io.luna.game.model.item.Item;
import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.OutboundMessageWriter;

import java.util.Arrays;
import java.util.Collection;

/**
 * An {@link OutboundMessageWriter} implementation that displays a group of {@link Item}s on a widget.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class WidgetItemGroupMessageWriter extends OutboundMessageWriter {

    /**
     * The identifier of the widget that the items will de displayed on.
     */
    private final int id;

    /**
     * The {@link Item}s that will be displayed on the widget.
     */
    private final Collection<? extends Item> items;

    /**
     * Creates a new {@link WidgetItemGroupMessageWriter}.
     *
     * @param id The identifier of the widget that the items will de displayed on.
     * @param items The {@link Item}s that will be displayed on the widget.
     */
    public WidgetItemGroupMessageWriter(int id, Collection<? extends Item> items) {
        this.id = id;
        this.items = items;
    }

    /**
     * Creates a new {@link WidgetItemGroupMessageWriter}.
     *
     * @param id The identifier of the widget that the items will de displayed on.
     * @param items The {@link Item}s that will be displayed on the widget.
     */
    public WidgetItemGroupMessageWriter(int id, Item[] items) {
        this(id, Arrays.asList(items));
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(53, MessageType.VARIABLE_SHORT);
        msg.putShort(id);
        msg.putShort(items.size());

        for (Item item : items) {
            if (item == null) {
                msg.put(0);
                msg.putShort(0, ByteTransform.A, ByteOrder.LITTLE);
                continue;
            }

            if (item.getAmount() >= 255) {
                msg.put(255);
                msg.putInt(item.getAmount(), ByteOrder.INVERSE_MIDDLE);
            } else {
                msg.put(item.getAmount());
            }
            msg.putShort(item.getId() + 1, ByteTransform.A, ByteOrder.LITTLE);
        }
        return msg;
    }
}
