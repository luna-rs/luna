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
public final class RegionChangeMessageWriter extends GameMessageWriter {

    /**
     * The new region.
     */
    private final Position position;

    /**
     * Creates a new {@link RegionChangeMessageWriter}.
     *
     * @param position The new region.
     */
    public RegionChangeMessageWriter(Position position) {
        this.position = position;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(222);
        msg.putShort(position.getCentralChunkY());
        msg.putShort(position.getCentralChunkX(), ValueType.ADD, ByteOrder.LITTLE);
        return msg;
    }
}
