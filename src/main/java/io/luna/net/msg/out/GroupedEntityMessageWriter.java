package io.luna.net.msg.out;

import io.luna.game.model.Position;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

import java.util.Collection;

/**
 * A {@link GameMessageWriter} that adds collection of stationary entities to a chunk.
 *
 * @author lare96
 */
public final class GroupedEntityMessageWriter extends GameMessageWriter {

    /**
     * The base position.
     */
    private final Position basePosition;

    /**
     * The placement position.
     */
    private final Position placementPosition;

    /**
     * The messages to write.
     */
    private final Collection<GameMessageWriter> messages;

    /**
     * Creates a new {@link GroupedEntityMessageWriter}.
     *
     * @param basePosition The base position.
     * @param placementChunk The placement position.
     * @param messages The messages to write.
     */
    public GroupedEntityMessageWriter(Position basePosition, Chunk placementChunk, Collection<GameMessageWriter> messages) {
        this.basePosition = basePosition;
        this.placementPosition = placementChunk.getPosition().getAbsolutePosition();
        this.messages = messages;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage mainMsg = ByteMessage.message(60, MessageType.VAR_SHORT);
        mainMsg.put(placementPosition.getLocalY(basePosition));
        mainMsg.put(placementPosition.getLocalX(basePosition), ValueType.NEGATE);
        for (GameMessageWriter subMsg : messages) {
            ByteMessage buf = subMsg.write(player);
            try {
                mainMsg.put(buf.getOpcode());
                mainMsg.putBytes(buf);
            } finally {
                buf.release();
            }
        }
        return mainMsg;
    }
}
