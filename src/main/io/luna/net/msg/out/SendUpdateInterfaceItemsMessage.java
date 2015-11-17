package io.luna.net.msg.out;

import io.luna.game.model.item.Item;
import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.OutboundGameMessage;

public class SendUpdateInterfaceItemsMessage extends OutboundGameMessage {

    private final int interfaceId;
    private final Item[] items;

    public SendUpdateInterfaceItemsMessage(int interfaceId, Item[] items) {
        this.interfaceId = interfaceId;
        this.items = items;
    }

    @Override
    public ByteMessage writeMessage(Player player) {
        ByteMessage msg = ByteMessage.create();

        msg.varShortMessage(53);
        msg.putShort(interfaceId);
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
