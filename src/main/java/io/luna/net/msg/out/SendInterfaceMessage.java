package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.OutboundGameMessage;

/**
 * An {@link OutboundGameMessage} implementation that opens an interface.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class SendInterfaceMessage extends OutboundGameMessage {

    /**
     * The interface to open.
     */
    private final int id;

    /**
     * Creates a new {@link SendInterfaceMessage}.
     *
     * @param id The interface to open.
     */
    public SendInterfaceMessage(int id) {
        this.id = id;
    }

    @Override
    public ByteMessage writeMessage(Player player) {
        ByteMessage msg = ByteMessage.message(97);
        msg.putShort(id);
        return msg;
    }
}
