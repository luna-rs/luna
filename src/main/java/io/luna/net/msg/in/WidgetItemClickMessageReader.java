package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFifthClickEvent;
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent;
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFourthClickEvent;
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemSecondClickEvent;
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemThirdClickEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent on interface item clicks.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class WidgetItemClickMessageReader extends GameMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int opcode = msg.getOpcode();
        switch (opcode) {
            case 145:
                return firstIndex(player, msg.getPayload());
            case 117:
                return secondIndex(player, msg.getPayload());
            case 43:
                return thirdIndex(player, msg.getPayload());
            case 129:
                return fourthIndex(player, msg.getPayload());
            case 135:
                return fifthIndex(player, msg.getPayload());
        }
        return null;
    }

    /**
     * The first index click.
     *
     * @param player The player.
     * @param msg The buffer to read from.
     */
    private Event firstIndex(Player player, ByteMessage msg) {
        int interfaceId = msg.getShort(ValueType.ADD);
        int index = msg.getShort(ValueType.ADD);
        int itemId = msg.getShort(ValueType.ADD);

        checkState(interfaceId > 0, "interfaceId <= 0");
        checkState(index >= 0, "index < 0");
        checkState(itemId > 0, "itemId <= 0");
        return new WidgetItemFirstClickEvent(player, index, interfaceId, itemId);
    }

    /**
     * The second index click.
     *
     * @param player The player.
     * @param msg The buffer to read from.
     */
    private Event secondIndex(Player player, ByteMessage msg) {
        int interfaceId = msg.getShort(true, ValueType.ADD, ByteOrder.LITTLE);
        int itemId = msg.getShort(true, ValueType.ADD, ByteOrder.LITTLE);
        int index = msg.getShort(true, ByteOrder.LITTLE);

        checkState(interfaceId > 0, "interfaceId <= 0");
        checkState(index >= 0, "index < 0");
        checkState(itemId > 0, "itemId <= 0");
        return new WidgetItemSecondClickEvent(player, index, interfaceId, itemId);
    }

    /**
     * The third index click.
     *
     * @param player The player.
     * @param msg The buffer to read from.
     */
    private Event thirdIndex(Player player, ByteMessage msg) {
        int interfaceId = msg.getShort(ByteOrder.LITTLE);
        int itemId = msg.getShort(ValueType.ADD);
        int index = msg.getShort(ValueType.ADD);

        checkState(interfaceId > 0, "interfaceId <= 0");
        checkState(index >= 0, "index < 0");
        checkState(itemId > 0, "itemId <= 0");
        return new WidgetItemThirdClickEvent(player, index, interfaceId, itemId);
    }

    /**
     * The fourth index click.
     *
     * @param player The player.
     * @param msg The buffer to read from.
     */
    private Event fourthIndex(Player player, ByteMessage msg) {
        int index = msg.getShort(ValueType.ADD);
        int interfaceId = msg.getShort();
        int itemId = msg.getShort(ValueType.ADD);

        checkState(interfaceId > 0, "interfaceId <= 0");
        checkState(index >= 0, "index < 0");
        checkState(itemId > 0, "itemId <= 0");
        return new WidgetItemFourthClickEvent(player, index, interfaceId, itemId);
    }

    /**
     * The fifth index click.
     *
     * @param player The player.
     * @param msg The buffer to read from.
     */
    private Event fifthIndex(Player player, ByteMessage msg) {
        int index = msg.getShort(ByteOrder.LITTLE);
        int interfaceId = msg.getShort(false, ValueType.ADD);
        int itemId = msg.getShort(ByteOrder.LITTLE);

        checkState(interfaceId > 0, "interfaceId <= 0");
        checkState(index >= 0, "index < 0");
        return new WidgetItemFifthClickEvent(player, index, interfaceId, itemId);
    }
}
