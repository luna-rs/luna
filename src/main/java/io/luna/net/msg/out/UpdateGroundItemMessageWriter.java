package io.luna.net.msg.out;

import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that updates an item's amount.
 *
 * @author lare96
 */
public final class UpdateGroundItemMessageWriter extends GameMessageWriter implements ChunkUpdatableMessage {

    /**
     * The offset.
     */
    private final int offset;

    /**
     * The id.
     */
    private final int id;

    /**
     * The previous amount.
     */
    private final int previousAmount;

    /**
     * The new amount.
     */
    private final int newAmount;

    /**
     * Creates a new {@link UpdateGroundItemMessageWriter}.
     *
     * @param offset The offset.
     * @param id The id.
     * @param previousAmount The previous amount.
     * @param newAmount The new amount.
     */
    public UpdateGroundItemMessageWriter(int offset, int id, int previousAmount, int newAmount) {
        this.offset = offset;
        this.id = id;
        this.previousAmount = previousAmount;
        this.newAmount = newAmount;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(121);
        msg.put(offset);
        msg.putShort(id);
        msg.putShort(previousAmount);
        msg.putShort(newAmount);
        return msg;
    }
}
