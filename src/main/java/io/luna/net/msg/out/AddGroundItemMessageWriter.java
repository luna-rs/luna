package io.luna.net.msg.out;

import io.luna.game.model.item.Item;
import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.MessageWriter;

/**
 * An {@link MessageWriter} implementation that displays a ground item.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class AddGroundItemMessageWriter extends MessageWriter {

    /**
     * The item that will be displayed.
     */
    private final Item item;

    /**
     * The offset of the item from the player.
     */
    private final int offset;

    /**
     * Creates a new {@link AddGroundItemMessageWriter}.
     *
     * @param item The item that will be displayed.
     * @param offset The offset of the item from the player.
     */
    public AddGroundItemMessageWriter(Item item, int offset) {
        this.item = item;
        this.offset = offset;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(44);
        msg.putShort(item.getId(), ByteTransform.A, ByteOrder.LITTLE);
        msg.putShort(item.getAmount());
        msg.put(offset);
        return msg;
    }
}
