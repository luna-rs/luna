package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that assigns a client-sided index and members status.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class AssignmentMessageWriter extends GameMessageWriter {

    /**
     * If assigning members or free-to-play status.
     */
    private final boolean members;

    /**
     * Creates a new {@link AssignmentMessageWriter}.
     *
     * @param members If assigning members or free-to-play status.
     */
    public AssignmentMessageWriter(boolean members) {
        this.members = members;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(249);
        msg.put(members ? 1 : 0, ValueType.ADD);
        msg.putShort(player.getIndex(), ValueType.ADD, ByteOrder.LITTLE);
        return msg;
    }
}
