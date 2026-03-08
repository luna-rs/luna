package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * A {@link GameMessageWriter} that updates a widget's client-side position offset.
 * <p>
 * This is used for interfaces whose visual state is represented by shifting a child widget within its parent, such as
 * progress indicators, fill bars, or the special attack bar.
 *
 * @author lare96
 */
public final class WidgetPositionMessageWriter extends GameMessageWriter {

    /**
     * The target widget id whose position offset will be updated.
     */
    private final int widgetId;

    /**
     * The horizontal offset to apply.
     */
    private final int x;

    /**
     * The vertical offset to apply.
     */
    private final int y;

    /**
     * Creates a new {@link WidgetPositionMessageWriter}.
     *
     * @param widgetId The widget id whose position offset will be changed.
     * @param x The horizontal offset to apply.
     * @param y The vertical offset to apply.
     */
    public WidgetPositionMessageWriter(int widgetId, int x, int y) {
        this.widgetId = widgetId;
        this.x = x;
        this.y = y;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage msg = ByteMessage.message(166, buffer);
        msg.putShort(y, ByteOrder.LITTLE);
        msg.putShort(x, ByteOrder.LITTLE);
        msg.putShort(widgetId);
        return msg;
    }
}