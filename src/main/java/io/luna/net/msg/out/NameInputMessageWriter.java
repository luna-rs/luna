package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.NameInputInterface;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that opens an "Enter name" input dialogue. Use
 * {@link NameInputInterface} instead of using this packet directly.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NameInputMessageWriter extends GameMessageWriter {

    @Override
    public ByteMessage write(Player player) {
        return ByteMessage.message(187);
    }
}