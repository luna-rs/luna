package io.luna.net.msg.out;

import io.luna.game.model.item.IndexedItem;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.GameMessageWriter;

import java.util.Arrays;

/**
 * A {@link GameMessageWriter} implementation that displays an item on a widget.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class WidgetIndexedItemGroupMessageWriter extends GameMessageWriter {

    /**
     * The widget identifier.
     */
    private final int id;

    public final Iterable<? extends IndexedItem> items;

    /**
     * Creates a new {@link WidgetIndexedItemGroupMessageWriter}.
     *
     * @param id The widget identifier.
     * @param items The items.
     */
    public WidgetIndexedItemGroupMessageWriter(int id, IndexedItem... items) {
        this(id, Arrays.asList(items));
    }
    /**
     * Creates a new {@link WidgetIndexedItemGroupMessageWriter}.
     *
     * @param id The widget identifier.
     * @param items The items.
     */
    public WidgetIndexedItemGroupMessageWriter(int id, Iterable<? extends IndexedItem> items) {
        this.id = id;
        this.items = items;
    }
    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(34, MessageType.VAR_SHORT);
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
