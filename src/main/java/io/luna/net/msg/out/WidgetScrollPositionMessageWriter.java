package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * A {@link GameMessageWriter} that sets the scroll position of a container widget.
 *
 * @author lare96
 */
public final class WidgetScrollPositionMessageWriter extends GameMessageWriter {

    /**
     * The container widget identifier.
     */
    private final int widgetId;

    /**
     * The vertical scroll position in pixels.
     */
    private final int position;

    /**
     * Creates a new {@link WidgetScrollPositionMessageWriter}.
     *
     * @param widgetId The container widget identifier.
     * @param position The vertical scroll position in pixels, clamped by the client to the widget's scroll range.
     */
    public WidgetScrollPositionMessageWriter(int widgetId, int position) {
        this.widgetId = widgetId;
        this.position = position;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage msg = ByteMessage.message(200, buffer);
        msg.putShort(widgetId);
        msg.putShort(position, ByteOrder.LITTLE, ValueType.ADD);
        return msg;
    }
}
