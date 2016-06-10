package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.OutboundMessageWriter;

/**
 * An {@link OutboundMessageWriter} implementation that opens an interface.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class WidgetMessageWriter extends OutboundMessageWriter {

    /**
     * The interface to open.
     */
    private final int id;

    /**
     * Creates a new {@link WidgetMessageWriter}.
     *
     * @param id The interface to open.
     */
    public WidgetMessageWriter(int id) {
        this.id = id;
    }

    @Override
    public ByteMessage encode(Player player) {
        ByteMessage msg = ByteMessage.message(97);
        msg.putShort(id);
        return msg;
    }
}
