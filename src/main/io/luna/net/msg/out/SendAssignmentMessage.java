package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.OutboundGameMessage;

public final class SendAssignmentMessage extends OutboundGameMessage {

    private final boolean members;

    public SendAssignmentMessage(boolean members) {
        this.members = members;
    }

    @Override
    public ByteMessage writeMessage(Player player) {
        ByteMessage msg = ByteMessage.create();
        msg.message(249);
        msg.put(members ? 1 : 0, ByteTransform.A);
        msg.putShort(player.getIndex(), ByteTransform.A, ByteOrder.LITTLE);
        return msg;
    }

}
