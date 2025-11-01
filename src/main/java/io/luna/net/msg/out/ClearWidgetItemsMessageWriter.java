package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * A {@link GameMessageWriter} implementation that clears all items on a widget.
 *
 * @author lare96
 */
public final class ClearWidgetItemsMessageWriter extends GameMessageWriter {

    /**
     * The widget identifier.
     */
    private final int id;

    /**
     * Creates a new {@link ClearWidgetItemsMessageWriter}.
     *
     * @param id The widget identifier.
     */
    public ClearWidgetItemsMessageWriter(int id) {
        this.id = id;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage msg = ByteMessage.message(219, buffer);
        msg.putShort(id, ByteOrder.LITTLE);
        return msg;
    }
}
