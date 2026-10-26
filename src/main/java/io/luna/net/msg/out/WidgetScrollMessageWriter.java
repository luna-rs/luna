package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * A {@link GameMessageWriter} that sets the scroll position of a scrollable widget.
 * <p>
 * The client remembers how far each scrollable widget was scrolled, so interfaces whose content is replaced while they
 * are open (such as the skill guide) use this to start back at the top. The client clamps the position to the widget's
 * scroll range.
 *
 * @author Hydrozoa
 */
public final class WidgetScrollMessageWriter extends GameMessageWriter {

    /**
     * The scrollable widget id.
     */
    private final int widgetId;

    /**
     * The scroll position, in pixels from the top.
     */
    private final int position;

    /**
     * Creates a new {@link WidgetScrollMessageWriter}.
     *
     * @param widgetId The scrollable widget id.
     * @param position The scroll position, in pixels from the top.
     */
    public WidgetScrollMessageWriter(int widgetId, int position) {
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
