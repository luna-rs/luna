package io.luna.net.msg.out;

import io.luna.game.model.item.Item;
import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.OutboundGameMessage;

/**
 * An {@link OutboundGameMessage} implementation that displays a ground item.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class SendAddGroundItemMessage extends OutboundGameMessage {

    /**
     * The item that will be displayed.
     */
    private final Item item;

    /**
     * The offset of the item from the player.
     */
    private final int offset;

    /**
     * Creates a new {@link SendAddGroundItemMessage}.
     *
     * @param item The item that will be displayed.
     * @param offset The offset of the item from the player.
     */
    public SendAddGroundItemMessage(Item item, int offset) {
        this.item = item;
        this.offset = offset;
    }

    @Override
    public ByteMessage writeMessage(Player player) {
        ByteMessage msg = ByteMessage.create();
        msg.message(44);
        msg.putShort(item.getId(), ByteTransform.A, ByteOrder.LITTLE);
        msg.putShort(item.getAmount());
        msg.put(offset);
        return msg;
    }
}
