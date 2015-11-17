package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.OutboundGameMessage;

/**
 * An {@link OutboundGameMessage} implementation that updates a special bar with {@code amount} energy.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class SendSpecialBarMessage extends OutboundGameMessage {

    /**
     * The identifier for which special bar is being updated.
     */
    private final int id;

    /**
     * The amount of energy to update the special bar with.
     */
    private final int amount;

    /**
     * Creates a new {@link SendSpecialBarMessage}.
     *
     * @param id The identifier for which special bar is being updated.
     * @param amount The amount of energy to update the special bar with.
     */
    public SendSpecialBarMessage(int id, int amount) {
        this.id = id;
        this.amount = amount;
    }

    @Override
    public ByteMessage writeMessage(Player player) {
        ByteMessage msg = ByteMessage.create();
        msg.message(70);
        msg.putShort(amount);
        msg.putShort(0, ByteOrder.LITTLE);
        msg.putShort(id, ByteOrder.LITTLE);
        return msg;
    }
}
