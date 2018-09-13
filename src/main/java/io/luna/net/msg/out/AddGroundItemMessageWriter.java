package io.luna.net.msg.out;

import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that displays a ground item.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class AddGroundItemMessageWriter extends GameMessageWriter {

    /**
     * The item.
     */
    private final Item item;

    /**
     * The position offset.
     */
    private final int offset;

    /**
     * Creates a new {@link AddGroundItemMessageWriter}.
     *
     * @param item The item.
     * @param offset The position offset.
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
