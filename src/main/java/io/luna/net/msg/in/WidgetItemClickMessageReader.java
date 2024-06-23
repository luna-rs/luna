package io.luna.net.msg.in;

import io.luna.game.event.impl.WidgetItemClickEvent;
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFifthClickEvent;
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent;
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFourthClickEvent;
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemSecondClickEvent;
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemThirdClickEvent;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent on interface item clicks.
 *
 * @author lare96
 */
public final class WidgetItemClickMessageReader extends GameMessageReader<WidgetItemClickEvent> {

    @Override
    public WidgetItemClickEvent decode(Player player, GameMessage msg) {
        int opcode = msg.getOpcode();
        ByteMessage payload = msg.getPayload();
        int index;
        int itemId;
        int interfaceId;
        switch (opcode) {
            case 3:
                itemId = payload.getShort(ValueType.ADD);
                interfaceId = payload.getShort();
                index = payload.getShort();
                return new WidgetItemFirstClickEvent(player, index, interfaceId, itemId);
            case 177:
                index = payload.getShort(true, ValueType.ADD);
                itemId = payload.getShort(true, ByteOrder.LITTLE);
                interfaceId = payload.getShort(true, ByteOrder.LITTLE);
                return new WidgetItemSecondClickEvent(player, index, interfaceId, itemId);
            case 91:
                itemId = payload.getShort(ByteOrder.LITTLE);
                index = payload.getShort(ByteOrder.LITTLE, ValueType.ADD);
                interfaceId = payload.getShort();
                return new WidgetItemThirdClickEvent(player, index, interfaceId, itemId);
            case 231:
                interfaceId = payload.getShort(ByteOrder.LITTLE, ValueType.ADD);
                index = payload.getShort(ByteOrder.LITTLE);
                itemId = payload.getShort();
                return new WidgetItemFourthClickEvent(player, index, interfaceId, itemId);
            case 158:
                index = payload.getShort(ByteOrder.LITTLE, ValueType.ADD);
                itemId = payload.getShort(ByteOrder.LITTLE, ValueType.ADD);
                interfaceId = payload.getShort(ByteOrder.LITTLE);
                return new WidgetItemFifthClickEvent(player, index, interfaceId, itemId);
        }
        throw new IllegalStateException("invalid opcode");
    }

    @Override
    public boolean validate(Player player, WidgetItemClickEvent event) {
        return event.getWidgetId() > 0 && event.getIndex() >= 0 && ItemDefinition.isIdValid(event.getItemId());
    }
}
