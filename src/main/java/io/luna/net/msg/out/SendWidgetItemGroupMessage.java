package io.luna.net.msg.out;

import io.luna.game.model.item.Item;
import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.OutboundGameMessage;

/**
 * An {@link OutboundGameMessage} implementation that displays a group of {@link Item}s on a widget.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class SendWidgetItemGroupMessage extends OutboundGameMessage {

    /**
     * The identifier of the widget that the items will de displayed on.
     */
    private final int id;

    /**
     * The {@link Item}s that will be displayed on the widget.
     */
    private final Item[] items;

    /**
     * Creates a new {@link SendWidgetItemGroupMessage}.
     *
     * @param id The identifier of the widget that the items will de displayed on.
     * @param items The {@link Item}s that will be displayed on the widget.
     */
    public SendWidgetItemGroupMessage(int id, Item[] items) {
        this.id = id;
        this.items = items;
    }

    @Override
    public ByteMessage writeMessage(Player player) {
        ByteMessage msg = ByteMessage.message(53, MessageType.VARIABLE_SHORT);
        msg.putShort(id);
        msg.putShort(items.length);

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
