package io.luna.net.msg.out;

import io.luna.game.model.Position;
import io.luna.game.model.chunk.ChunkRepository;
import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

import java.util.Collection;

/**
 * A {@link GameMessageWriter} implementation that adds collection of stationary entities to a chunk.
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
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage mainMsg = ByteMessage.message(183, MessageType.VAR_SHORT, buffer);
        mainMsg.put(placementPosition.getLocalX(basePosition));
        mainMsg.put(placementPosition.getLocalY(basePosition), ValueType.ADD);
        for (ChunkUpdatableMessage updatableMessage : messages) {
            GameMessageWriter subMsg = (GameMessageWriter) updatableMessage;
            ByteBuf pooledBuf = ByteMessage.pooledBuffer();
            try {
                ByteMessage buf = subMsg.write(player, pooledBuf);
                mainMsg.put(buf.getOpcode());
                mainMsg.putBytes(buf);
            } finally {
                pooledBuf.release(pooledBuf.refCnt());
            }
        }
        return mainMsg;
    }
}
