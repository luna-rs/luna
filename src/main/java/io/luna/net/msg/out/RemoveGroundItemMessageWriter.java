package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that removes a ground item.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class RemoveGroundItemMessageWriter extends GameMessageWriter {

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
        ByteMessage msg = ByteMessage.message(156);
        msg.put(offset, ValueType.SUBTRACT);
        msg.putShort(id);
        return msg;
    }
}