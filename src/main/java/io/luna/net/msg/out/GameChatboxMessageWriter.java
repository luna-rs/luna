package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} that sends a game message.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class GameChatboxMessageWriter extends GameMessageWriter {

    /**
     * The message.
     */
    private final String message;

    /**
     * Creates a new {@link GameChatboxMessageWriter}.
     *
     * @param message The message.
     */
    public GameChatboxMessageWriter(String message) {
        this.message = message;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(253, MessageType.VAR);
        msg.putString(message);
        return msg;
    }
}
