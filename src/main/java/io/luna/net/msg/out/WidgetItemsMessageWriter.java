package io.luna.net.msg.out;

import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.MessageType;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

import java.util.Arrays;
import java.util.Collection;

/**
 * A {@link GameMessageWriter} implementation that displays a group of items on a widget.
 *
 * @author lare96
 */
public final class WidgetItemsMessageWriter extends GameMessageWriter {

    /**
     * The widget identifier.
     */
    private final int id;

    /**
     * The items.
     */
    private final Collection<Item> items;

    /**
     * Creates a new {@link WidgetItemsMessageWriter}.
     *
     * @param id The widget identifier.
     * @param items The items.
     */
    public WidgetItemsMessageWriter(int id, Item[] items) {
        this(id, Arrays.asList(items));
    }

    /**
     * Creates a new {@link WidgetItemsMessageWriter}.
     *
     * @param id The widget identifier.
     * @param items The items.
     */
    public WidgetItemsMessageWriter(int id, Collection<Item> items) {
        this.id = id;
        this.items = items;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(206, MessageType.VAR_SHORT);
        msg.putShort(id);
        msg.putShort(items.size());

        for (Item item : items) {
            if (item == null) {
                msg.putShort(0, ByteOrder.LITTLE, ValueType.ADD);
                msg.put(0, ValueType.NEGATE);
                continue;
            }

            msg.putShort(item.getId() + 1, ByteOrder.LITTLE, ValueType.ADD);
            if (item.getAmount() >= 255) {
                msg.put(255, ValueType.NEGATE);
                msg.putInt(item.getAmount(), ByteOrder.LITTLE);
            } else {
                msg.put(item.getAmount(), ValueType.NEGATE);
            }
        }
        return msg;
    }
}
