package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * Displays a raw cache model in a widget using the revision 377 model-type-1 packet.
 *
 * @author lare96
 */
public final class WidgetModelMessageWriter extends GameMessageWriter {
    private final int widgetId;
    private final int modelId;

    public WidgetModelMessageWriter(int widgetId, int modelId) {
        this.widgetId = widgetId;
        this.modelId = modelId;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage message = ByteMessage.message(216, buffer);
        message.putShort(modelId, ByteOrder.LITTLE, ValueType.ADD);
        message.putShort(widgetId, ByteOrder.LITTLE, ValueType.ADD);
        return message;
    }
}
