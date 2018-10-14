package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that displays an item model on a widget.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class WidgetItemModelMessageWriter extends GameMessageWriter {

    /**
     * The widget identifier.
     */
    private final int widgetId;

    /**
     * The item's scale.
     */
    private final int scale;

    /**
     * The item.
     */
    private final int itemId;

    /**
     * Creates a new {@link WidgetItemModelMessageWriter}.
     *
     * @param widgetId The widget identifier.
     * @param scale The item's scale.
     * @param itemId The item.
     */
    public WidgetItemModelMessageWriter(int widgetId, int scale, int itemId) {
        this.widgetId = widgetId;
        this.scale = scale;
        this.itemId = itemId;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(246);
        msg.putShort(widgetId, ByteOrder.LITTLE);
        msg.putShort(scale);
        msg.putShort(itemId);
        return msg;
    }
}
