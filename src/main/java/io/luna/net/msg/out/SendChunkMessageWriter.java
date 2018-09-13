package io.luna.net.msg.out;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that sends the current chunk coordinates.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class SendChunkMessageWriter extends GameMessageWriter {

    /**
     * The current position.
     */
    private final Position position;

    /**
     * Creates a new {@link SendChunkMessageWriter}.
     *
     * @param position The current position.
     */
    public SendChunkMessageWriter(Position position) {
        this.position = position;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(85);
        msg.put(position.getY() - (position.getChunkY() * 8), ByteTransform.C);
        msg.put(position.getX() - (position.getChunkX() * 8), ByteTransform.C);
        return msg;
    }
}