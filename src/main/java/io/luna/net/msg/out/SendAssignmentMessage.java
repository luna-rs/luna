package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.OutboundGameMessage;

/**
 * An {@link OutboundGameMessage} implementation that assigns a {@link Player} a client-sided slot and members status for the
 * duration of their login session.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class SendAssignmentMessage extends OutboundGameMessage {

    /**
     * If the {@link Player} should have a member or free-to-play status.
     */
    private final boolean members;

    /**
     * Creates a new {@link SendAssignmentMessage}.
     *
     * @param members If the {@link Player} should have a member or free-to-play status.
     */
    public SendAssignmentMessage(boolean members) {
        this.members = members;
    }

    @Override
    public ByteMessage writeMessage(Player player) {
        ByteMessage msg = ByteMessage.message(249);
        msg.put(members ? 1 : 0, ByteTransform.A);
        msg.putShort(player.getIndex(), ByteTransform.A, ByteOrder.LITTLE);
        return msg;
    }
}
