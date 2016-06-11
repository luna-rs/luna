package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.OutboundMessageWriter;

/**
 * An {@link OutboundMessageWriter} implementation that displays an interface on the chatbox area of the gameframe.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ChatboxInterfaceMessageWriter extends OutboundMessageWriter {

    /**
     * The interface to display on the chatbox.
     */
    private final int id;

    /**
     * Creates a new {@link ChatboxInterfaceMessageWriter}.
     *
     * @param id The interface to display on the chatbox.
     */
    public ChatboxInterfaceMessageWriter(int id) {
        this.id = id;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(164);
        msg.putShort(id, ByteOrder.LITTLE);
        return msg;
    }
}
