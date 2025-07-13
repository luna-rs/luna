package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that animates a widget.
 *
 * @author lare96
 */
public final class WidgetAnimationMessageWriter extends GameMessageWriter {

    /**
     * The widget.
     */
    private final int widgetId;

    /**
     * The animation.
     */
    private final int animationId;

    /**
     * Creates a new {@link WidgetAnimationMessageWriter}.
     *
     * @param widgetId    The widget.
     * @param animationId The animation.
     */
    public WidgetAnimationMessageWriter(int widgetId, int animationId) {
        this.widgetId = widgetId;
        this.animationId = animationId;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(2);
        msg.putShort(widgetId, ByteOrder.LITTLE, ValueType.ADD);
        msg.putShort(animationId, ValueType.ADD);
        return msg;
    }
}