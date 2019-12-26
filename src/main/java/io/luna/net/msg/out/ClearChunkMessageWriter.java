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
     * The chunk position.
     */
    private final Position chunkPosition;

    /**
     * Creates a new {@link ClearChunkMessageWriter}.
     *
     * @param chunkPosition The chunk position.
     */
    public ClearChunkMessageWriter(Position chunkPosition) {
        this.chunkPosition = chunkPosition;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(64);
        var plrRegion = player.getLastRegion();
        msg.put(chunkPosition.getLocalX(plrRegion), ValueType.NEGATE);
        msg.put(chunkPosition.getLocalY(plrRegion), ValueType.SUBTRACT);
        return msg;
    }
}