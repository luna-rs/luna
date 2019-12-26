package io.luna.net.msg.out;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that marks a chunk.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class ChunkPlacementMessageWriter extends GameMessageWriter {

    // TODO cache the placement chunk to avoid duplicate writes, do the same to ClearChunk
    /**
     * The position's chunk to mark.
     */
    private final Position position;

    /**
     * Creates a new {@link ChunkPlacementMessageWriter}.
     *
     * @param position The position's chunk to mark.
     */
    public ChunkPlacementMessageWriter(Position position) {
        this.position = position;
    }

    @Override
    public ByteMessage write(Player player) {
        // TODO Check if marking the right chunk?
        ByteMessage msg = ByteMessage.message(85);
        msg.put(position.getLocalY(/*player.getPosition()*/ player.getLastRegion()), ValueType.NEGATE);
        msg.put(position.getLocalX(/*player.getPosition()*/ player.getLastRegion()), ValueType.NEGATE);
        return msg;
    }
}