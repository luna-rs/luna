package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.MessageWriter;

/**
 * A {@link MessageWriter} implementation that disconnects a player.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class LogoutMessageWriter extends MessageWriter {

    @Override
    public ByteMessage write(Player player) {
        return ByteMessage.message(109);
    }
}
