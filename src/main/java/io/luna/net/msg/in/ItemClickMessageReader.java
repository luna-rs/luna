package io.luna.net.msg.in;

import io.luna.game.event.impl.ItemClickEvent;
import io.luna.game.event.impl.ItemClickEvent.ItemFifthClickEvent;
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent;
import io.luna.game.event.impl.ItemClickEvent.ItemFourthClickEvent;
import io.luna.game.event.impl.ItemClickEvent.ItemSecondClickEvent;
import io.luna.game.event.impl.ItemClickEvent.ItemThirdClickEvent;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent on item clicks.
 *
 * @author lare96
 */
public final class ItemClickMessageReader extends GameMessageReader<ItemClickEvent> {

    @Override
    public ItemClickEvent decode(Player player, GameMessage msg) {
        int opcode = msg.getOpcode();
        ByteMessage payload = msg.getPayload();

        int id;
        int index;
        int interfaceId;
        switch (opcode) {
            case 203:
                interfaceId = payload.getShort(true, ValueType.ADD);
                index = payload.getShort(false, ByteOrder.LITTLE);
                id = payload.getShort(true, ByteOrder.LITTLE);
                return new ItemFirstClickEvent(player, id, index, interfaceId);
            case 24:
                id = payload.getShort(false);
                index = payload.getShort(false, ValueType.ADD);
                interfaceId = payload.getShort(false, ValueType.ADD);
                return new ItemSecondClickEvent(player, id, index, interfaceId);
            case 161:
                id = payload.getShort(false, ValueType.ADD);
                index = payload.getShort(true, ByteOrder.LITTLE, ValueType.ADD);
                interfaceId = payload.getShort(true, ByteOrder.LITTLE, ValueType.ADD);
                return new ItemThirdClickEvent(player, id, index, interfaceId);
            case 228:
                interfaceId = payload.getShort(true, ByteOrder.LITTLE, ValueType.ADD);
                index = payload.getShort(true, ByteOrder.LITTLE, ValueType.ADD);
                id = payload.getShort(false, ValueType.ADD);
                return new ItemFourthClickEvent(player, id, index, interfaceId);
            case 4:
                id = payload.getShort(false, ValueType.ADD);
                interfaceId = payload.getShort(false);
                index = payload.getShort(false, ValueType.ADD);
                return new ItemFifthClickEvent(player, id, index, interfaceId);
        }
        throw new IllegalStateException("invalid opcode");
    }

    @Override
    public boolean validate(Player player, ItemClickEvent event) {
        if (!ItemDefinition.isIdValid(event.getId()) ||
                event.getIndex() < 0 ||
                event.getInterfaceId() <= 0) {
            return false;
        }

        if (event.getInterfaceId() == 3214) {
            return player.getInventory().contains(event.getIndex(), event.getId());
        }
        return false;
    }

    @Override
    public void handle(Player player, ItemClickEvent event){
        player.interruptAction();
    }
}
