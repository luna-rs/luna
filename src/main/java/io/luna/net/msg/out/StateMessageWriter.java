package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.OutboundMessageWriter;

/**
 * @author lare96 <http://github.org/lare96>
 */
public final class StateMessageWriter extends OutboundMessageWriter {

    private final int id;
    private final int state;

    public StateMessageWriter(int id, int state) {
        this.id = id;
        this.state = state;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg;
        boolean overflow = state > Byte.MAX_VALUE;

        if (overflow) {
            msg = ByteMessage.message(87);
            msg.putShort(id, ByteOrder.LITTLE);
            msg.putInt(state, ByteOrder.MIDDLE);
        } else {
            msg = ByteMessage.message(36);
            msg.putShort(id, ByteOrder.LITTLE);
            msg.put(state);
            return msg;
        }
        return msg;
    }
}