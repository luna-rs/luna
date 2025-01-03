package io.luna.net.msg.out;

import io.luna.game.model.Position;
import io.luna.game.model.chunk.ChunkRepository;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that clears entities from a chunk.
 *
 * @author lare96
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
     * @param placementChunkRepository The placement chunk.
     */
    public ClearChunkMessageWriter(Position basePosition, ChunkRepository placementChunkRepository) {
        this.basePosition = basePosition;
        placementPosition = placementChunkRepository.getChunk().getAbsPosition();
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(40);
        msg.put(placementPosition.getLocalY(basePosition), ValueType.SUBTRACT);
        msg.put(placementPosition.getLocalX(basePosition), ValueType.NEGATE);
        return msg;
    }
}