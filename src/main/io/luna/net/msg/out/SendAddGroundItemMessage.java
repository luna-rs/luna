package io.luna.net.msg.out;

import io.luna.game.model.item.Item;
import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.OutboundGameMessage;

public final class SendAddGroundItemMessage extends OutboundGameMessage {

    private final Item item;
    private final int offset;

    public SendAddGroundItemMessage(Item item, int offset) {
        this.item = item;
        this.offset = offset;
    }

    @Override
    public ByteMessage writeMessage(Player player) {
        ByteMessage msg = ByteMessage.create();
        msg.message(44);
        msg.putShort(item.getId(), ByteTransform.A, ByteOrder.LITTLE);
        msg.putShort(item.getAmount());
        msg.put(offset);
        return msg;
    }
}
