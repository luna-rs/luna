package io.luna.net.msg.in;

import io.luna.game.event.impl.ArrangeItemEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when rearranging items.
 *
 * @author lare96
 */
public final class ArrangeItemMessageReader extends GameMessageReader<ArrangeItemEvent> {

    @Override
    public ArrangeItemEvent decode(Player player, GameMessage msg) {
        int toIndex = msg.getPayload().getShort(ByteOrder.LITTLE, ValueType.ADD);
        int insertionMode = msg.getPayload().get(false, ValueType.ADD);
        int widgetId = msg.getPayload().getShort(ValueType.ADD);
        int fromIndex = msg.getPayload().getShort(ByteOrder.LITTLE);
        return new ArrangeItemEvent(player, fromIndex, toIndex, widgetId, insertionMode);
    }

    @Override
    public boolean validate(Player player, ArrangeItemEvent event) {
        return event.getFromIndex() > -1 && event.getToIndex() > -1 && event.getWidgetId() > 0 &&
                (event.getInsertionMode() == 0 || event.getInsertionMode() == 1);
    }
}