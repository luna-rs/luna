package io.luna.net.msg.out;

import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that removes a ground item.
 *
 * @author lare96 
 */
public final class RemoveGroundItemMessageWriter extends GameMessageWriter implements ChunkUpdatableMessage {

    /**
     * The item identifier.
     */
    private final int id;

    /**
     * The offset.
     */
    private final int offset;

    /**
     * Creates a new {@link RemoveGroundItemMessageWriter}.
     *
     * @param id The item identifier.
     * @param offset The offset.
     */
    public RemoveGroundItemMessageWriter(int id, int offset) {
        this.id = id;
        this.offset = offset;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(208);
        msg.putShort(id, ValueType.ADD);
        msg.put(offset, ValueType.ADD);
        return msg;
    }
}