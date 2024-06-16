package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.DialogueInterface;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that displays an interface on the chatbox area of the
 * gameframe. Use {@link DialogueInterface} instead of using this packet directly.
 *
 * @author lare96
 */
public final class DialogueInterfaceMessageWriter extends GameMessageWriter {

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
        ByteMessage msg = ByteMessage.message(109);
        msg.putShort(id);
        return msg;
    }
}
