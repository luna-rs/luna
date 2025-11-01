package io.luna.net.msg.out;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * A {@link GameMessageWriter} implementation that forces a hint icon above a {@link Position}.
 *
 * @author lare96
 */
public final class PositionHintMessageWriter extends GameMessageWriter{

    /**
     * The target of the hint icon.
     */
    private final Position target;

    /**
     * Creates a new {@link PositionHintMessageWriter}.
     *
     * @param target The target of the hint icon.
     */
    public PositionHintMessageWriter(Position target) {
        this.target = target;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage msg = ByteMessage.message(199, buffer);
        msg.put(2);
        msg.putShort(target.getX());
        msg.putShort(target.getY());
        msg.put(0);
        return msg;
    }
}
