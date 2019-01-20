package io.luna.net.msg.out;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that clears entities from a chunk.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ClearChunkMessageWriter extends GameMessageWriter {

    /**
     * The chunk.
     */
    private final Position pos;

    /**
     * Creates a new {@link ClearChunkMessageWriter}.
     *
     * @param pos The chunk.
     */
    public ClearChunkMessageWriter(Position pos) {
        this.pos = pos;
    }

    @Override
    public ByteMessage write(Player player) {
        var msg = ByteMessage.message(64);
        var position = player.getPosition();
        msg.put(pos.getLocalX(position), ValueType.NEGATE);
        msg.put(pos.getLocalY(position), ValueType.NEGATE);
        return msg;
    }
}