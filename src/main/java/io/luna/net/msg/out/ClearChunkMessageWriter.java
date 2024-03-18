package io.luna.net.msg.out;

import io.luna.game.model.Position;
import io.luna.game.model.chunk.Chunk;
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
     * The base position.
     */
    private final Position basePosition;

    /**
     * The placement position.
     */
    private final Position placementPosition;

    /**
     * Creates a new {@link ChunkPlacementMessageWriter}.
     *
     * @param basePosition The base position.
     * @param placementChunk The placement chunk.
     */
    public ClearChunkMessageWriter(Position basePosition, Chunk placementChunk) {
        this.basePosition = basePosition;
        placementPosition = placementChunk.getPosition().getAbsolutePosition();
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(64);
        msg.put(placementPosition.getLocalX(basePosition), ValueType.NEGATE);
        msg.put(placementPosition.getLocalY(basePosition), ValueType.SUBTRACT);
        return msg;
    }
}