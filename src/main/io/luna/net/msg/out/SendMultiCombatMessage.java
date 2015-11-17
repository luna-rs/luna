package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.OutboundGameMessage;

public final class SendMultiCombatMessage extends OutboundGameMessage {

    private final boolean display;

    public SendMultiCombatMessage(boolean display) {
        this.display = display;
    }

    @Override
    public ByteMessage writeMessage(Player player) {
        ByteMessage msg = ByteMessage.create();
        msg.message(61);
        msg.put(display ? 1 : 0);
        return msg;
    }
}