package io.luna.net.msg.out;

import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that adds an object.
 *
 * @author lare96
 */
public final class AddObjectMessageWriter extends GameMessageWriter implements ChunkUpdatableMessage {

    /**
     * The identifier.
     */
    private final int id;

    /**
     * The type.
     */
    private final int type;

    /**
     * The orientation.
     */
    private final int direction;

    /**
     * The offset.
     */
    private final int offset;

    /**
     * Creates a new {@link AddObjectMessageWriter}.
     *
     * @param id The identifier.
     * @param type The type.
     * @param direction The orientation.
     * @param offset The offset.
     */
    public AddObjectMessageWriter(int id, int type, int direction, int offset) {
        this.id = id;
        this.type = type;
        this.direction = direction;
        this.offset = offset;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(152);
        msg.put((type << 2) + (direction & 3), ValueType.NEGATE);
        msg.putShort(id, ByteOrder.LITTLE, ValueType.ADD);
        msg.put(offset, ValueType.ADD);
        return msg;
    }
}