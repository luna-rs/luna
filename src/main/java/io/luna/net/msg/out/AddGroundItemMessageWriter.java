package io.luna.net.msg.out;

import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that displays a ground item.
 *
 * @author lare96
 */
public final class AddGroundItemMessageWriter extends GameMessageWriter implements ChunkUpdatableMessage {

    /**
     * The item identifier.
     */
    private final int id;

    /**
     * The amount.
     */
    private final int amount;

    /**
     * The offset.
     */
    private final int offset;

    /**
     * Creates a new {@link AddGroundItemMessageWriter}.
     *
     * @param id The item identifier.
     * @param amount The amount.
     * @param offset The position offset.
     */
    public AddGroundItemMessageWriter(int id, int amount, int offset) {
        this.id = id;
        this.amount = amount;
        this.offset = offset;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(107);
        msg.putShort(id);
        msg.put(offset, ValueType.NEGATE);
        msg.putShort(amount, ValueType.ADD);
        return msg;
    }
}
