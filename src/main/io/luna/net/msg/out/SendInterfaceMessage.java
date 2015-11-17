package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.OutboundGameMessage;

public final class SendInterfaceMessage extends OutboundGameMessage {

    private final int id;

    public SendInterfaceMessage(int id) {
        this.id = id;
    }

    @Override
    public ByteMessage writeMessage(Player player) {
        ByteMessage msg = ByteMessage.create();
        msg.message(97);
        msg.putShort(id);
        return msg;
    }
}
