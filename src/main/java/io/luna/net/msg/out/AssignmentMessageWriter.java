package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.MessageWriter;

/**
 * An {@link MessageWriter} implementation that assigns a {@link Player} a client-sided index and members status for the
 * duration of their login session.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class AssignmentMessageWriter extends MessageWriter {

    /**
     * If the {@link Player} should have a member or free-to-play status.
     */
    private final boolean members;

    /**
     * Creates a new {@link AssignmentMessageWriter}.
     *
     * @param members If the {@link Player} should have a member or free-to-play status.
     */
    public AssignmentMessageWriter(boolean members) {
        this.members = members;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(249);
        msg.put(members ? 1 : 0, ByteTransform.A);
        msg.putShort(player.getIndex(), ByteTransform.A, ByteOrder.LITTLE);
        return msg;
    }
}
