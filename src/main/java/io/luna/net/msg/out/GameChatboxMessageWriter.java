package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.MessageWriter;
import org.apache.logging.log4j.message.ParameterizedMessage;

/**
 * A {@link MessageWriter} that sends the {@link Player} a game message located in the chatbox.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class GameChatboxMessageWriter extends MessageWriter {

    /**
     * The message to write to the chatbox.
     */
    private final String message;

    /**
     * Creates a new {@link GameChatboxMessageWriter}.
     *
     * @param message The message to write to the chatbox.
     */
    public GameChatboxMessageWriter(String message) {
        this.message = message;
    }

    /**
     * Creates a new {@link GameChatboxMessageWriter} with {@code message} formatted using {@link ParameterizedMessage}. It
     * works like so
     * <p>
     * {@code player.queue(new GameChatboxMessageWriter("Hi! My name is {}.", username));}
     *
     * @param message The message to format, then write to the chatbox.
     * @param params The parameters to include in this formatted message.
     */
    public GameChatboxMessageWriter(String message, Object... params) {
        this(ParameterizedMessage.format(message, params));
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(253, MessageType.VARIABLE);
        msg.putString(message);
        return msg;
    }
}
