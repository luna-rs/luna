package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that disconnects a player. Use {@link Player#logout()} instead of using
 * this directly.
 *
 * @author lare96
 */
public final class LogoutMessageWriter extends GameMessageWriter {

    @Override
    public ByteMessage write(Player player) {
        return ByteMessage.message(5);
    }
}
