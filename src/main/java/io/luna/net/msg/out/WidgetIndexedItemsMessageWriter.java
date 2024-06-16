package io.luna.net.msg.out;

import io.luna.game.model.item.IndexedItem;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.GameMessageWriter;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * A {@link GameMessageWriter} implementation that displays an item on a widget.
 *
 * @author lare96
 */
public final class WidgetIndexedItemsMessageWriter extends GameMessageWriter {

    /**
     * The widget identifier.
     */
    private final int id;

    /**
     * The items to display.
     */
    private final Collection<IndexedItem> items;

    /**
     * Creates a new {@link WidgetIndexedItemsMessageWriter}.
     *
     * @param id The widget identifier.
     * @param items The items to display.
     */
    public WidgetIndexedItemsMessageWriter(int id, Collection<IndexedItem> items) {
        this.id = id;
        this.items = items;
    }

    /**
     * Creates a new {@link WidgetIndexedItemsMessageWriter}.
     *
     * @param id The widget identifier.
     * @param items The items to display.
     */
    public WidgetIndexedItemsMessageWriter(int id, IndexedItem... items) {
        this.id = id;

        if (items.length == 0) {
            this.items = List.of();
        } else if (items.length == 1) {
            this.items = List.of(items[0]);
        } else {
            // Arrays.asList is faster here, doesn't make a copy of the array.
            this.items = Arrays.asList(items);
        }
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(134, MessageType.VAR_SHORT);
        msg.putShort(id);

        for (IndexedItem item : items) {
            int index = item.getIndex();
            if (index <= 127) {
                msg.put(index);
            } else {
                msg.putShort(index);
            }

            msg.putShort(item.getId() + 1);

            int amount = item.getAmount();
            if (amount >= 255) {
                msg.put(255);
                msg.putInt(amount);
            } else {
                msg.put(amount);
            }
        }
        return msg;
    }
}
