package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that sends a full screen interface.
 *
 * @author lare96
 */
public final class FullScreenInterfaceMessageWriter extends GameMessageWriter {

    /**
     * The primary identifier.
     */
    private final int primaryId;

    /**
     * The secondary identifier.
     */
    private final int secondaryId;

    /**
     * Creates a new {@link FullScreenInterfaceMessageWriter}.
     *
     * @param primaryId The primary identifier.
     * @param secondaryId The secondary identifier.
     */
    public FullScreenInterfaceMessageWriter(int primaryId, int secondaryId) {
        this.primaryId = primaryId;
        this.secondaryId = secondaryId;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(253);
        msg.putShort(primaryId, ByteOrder.LITTLE);
        msg.put(secondaryId, ValueType.ADD);
        return msg;
    }
}
