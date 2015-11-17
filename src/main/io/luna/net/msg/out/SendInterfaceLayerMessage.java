package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.OutboundGameMessage;

/**
 * An {@link OutboundGameMessage} implementation that sets an interface to be hidden until hovered over. A state of {@code 1}
 * means hidden until hovered, and a state of {@code 0} disables that effect.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class SendInterfaceLayerMessage extends OutboundGameMessage {

    /**
     * The interface to make hidden or unhidden.
     */
    private final int id;

    /**
     * The state: will only be a value of {@code 1} or {@code 0}.
     */
    private final int state;

    /**
     * Creates a new {@link SendInterfaceLayerMessage}.
     *
     * @param id The id of the interface to make hidden or unhidden.
     * @param hide If the interface should be hidden.
     */
    public SendInterfaceLayerMessage(int id, boolean hide) {
        this.id = id;
        state = hide ? 1 : 0;
    }

    @Override
    public ByteMessage writeMessage(Player player) {
        ByteMessage msg = ByteMessage.create();
        msg.message(171);
        msg.put(state);
        msg.putShort(id);
        return msg;
    }
}
