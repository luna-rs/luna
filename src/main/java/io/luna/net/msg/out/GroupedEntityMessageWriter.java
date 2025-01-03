package io.luna.net.msg.out;

import io.luna.game.model.Position;
import io.luna.game.model.chunk.ChunkRepository;
import io.luna.game.model.chunk.ChunkUpdatableMessage;
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
    // TODO Restrict to certain types of messages

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
    private final Collection<ChunkUpdatableMessage> messages;

    /**
     * Creates a new {@link GroupedEntityMessageWriter}.
     *
     * @param basePosition The base position.
     * @param placementChunkRepository The placement position.
     * @param messages The messages to write.
     */
    public GroupedEntityMessageWriter(Position basePosition, ChunkRepository placementChunkRepository, Collection<ChunkUpdatableMessage> messages) {
        this.basePosition = basePosition;
        this.placementPosition = placementChunkRepository.getChunk().getAbsPosition();
        this.messages = messages;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage mainMsg = ByteMessage.message(183, MessageType.VAR_SHORT);
        mainMsg.put(placementPosition.getLocalX(basePosition));
        mainMsg.put(placementPosition.getLocalY(basePosition), ValueType.ADD);
        for (ChunkUpdatableMessage updatableMessage : messages) {
            GameMessageWriter subMsg = (GameMessageWriter) updatableMessage;
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
