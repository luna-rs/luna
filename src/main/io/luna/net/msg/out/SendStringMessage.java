package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.OutboundGameMessage;

public final class SendStringMessage extends OutboundGameMessage {

    private final String text;

    private final int id;

    public SendStringMessage(String text, int id) {
        this.text = text;
        this.id = id;
    }

    @Override
    public ByteMessage writeMessage(Player player) {
        ByteMessage msg = ByteMessage.create();
        msg.varShortMessage(126);
        msg.putString(text);
        msg.putShort(id, ByteTransform.A);
        msg.endVarShortMessage();
        return msg;
    }

}
