package io.luna.net.msg.out;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that sends a message containing the current region.
 *
 * @author lare96
 */
public final class RegionMessageWriter extends GameMessageWriter {

    /**
     * The new region.
     */
    private final Position position;

    /**
     * Creates a new {@link RegionMessageWriter}.
     *
     * @param position The new region.
     */
    public RegionMessageWriter(Position position) {
        this.position = position;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(222);
        msg.putShort(position.getChunkY());
        msg.putShort(position.getChunkX(), ByteOrder.LITTLE, ValueType.ADD);
        return msg;
    }
}
