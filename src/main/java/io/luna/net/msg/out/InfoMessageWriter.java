package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.OutboundMessageWriter;

/**
 * An {@link OutboundMessageWriter} that sends the {@link Player} a game message located in the chatbox.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class InfoMessageWriter extends OutboundMessageWriter {

    /**
     * The message to write to the chatbox.
     */
    private final String message;

    /**
     * Creates a new {@link InfoMessageWriter}.
     *
     * @param message The message to write to the chatbox.
     */
    public InfoMessageWriter(String message) {
        this.message = message;
    }

    /**
     * Creates a new {@link InfoMessageWriter} with a formatted {@code message}.
     *
     * @param message The message to format, then write to the chatbox.
     * @param params The parameters to include in this formatted message.
     */
    public InfoMessageWriter(String message, Object... params) {
        this(String.format(message, params));
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(253, MessageType.VARIABLE);
        msg.putString(message);
        return msg;
    }
}
