package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that sends the client a copy of the player's ignore list.
 *
 * @author lare96
 */
public final class UpdateIgnoreListMessageWriter extends GameMessageWriter {

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(226, MessageType.VAR_SHORT);
        for (long name : player.getIgnores()) {
            msg.putLong(name);
        }
        return msg;
    }
}
