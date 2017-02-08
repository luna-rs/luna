package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.MessageWriter;

/**
 * A {@link MessageWriter} implementation that displays an interface on the chatbox area of the gameframe.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class DialogueInterfaceMessageWriter extends MessageWriter {

    /**
     * The interface to display.
     */
    private final int id;

    /**
     * Creates a new {@link DialogueInterfaceMessageWriter}.
     *
     * @param id The interface to display.
     */
    public DialogueInterfaceMessageWriter(int id) {
        this.id = id;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(164);
        msg.putShort(id, ByteOrder.LITTLE);
        return msg;
    }
}
