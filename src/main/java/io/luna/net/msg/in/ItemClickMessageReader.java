package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.ItemClickEvent.ItemFifthClickEvent;
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent;
import io.luna.game.event.impl.ItemClickEvent.ItemFourthClickEvent;
import io.luna.game.event.impl.ItemClickEvent.ItemSecondClickEvent;
import io.luna.game.event.impl.ItemClickEvent.ItemThirdClickEvent;
import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.MessageReader;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link MessageReader} implementation that intercepts data sent on item clicks.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ItemClickMessageReader extends MessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int opcode = msg.getOpcode();
        switch (opcode) {
        case 122:
            return firstIndex(player, msg.getPayload());
        case 41:
            return secondIndex(player, msg.getPayload());
        case 16:
            return thirdIndex(player, msg.getPayload());
        case 75:
            return fourthIndex(player, msg.getPayload());
        case 87:
            return fifthIndex(player, msg.getPayload());
        }
        return null;
    }

    /**
     * Returns {@code true} if the item clicked is valid.
     */
    private boolean validate(Player player, int id, int index, int interfaceId) {
        checkState(Item.isIdWithinRange(id), "itemId out of range");
        checkState(index >= 0, "index out of range");
        checkState(interfaceId > 0, "interfaceId out of range");

        switch (interfaceId) {
        case 3214:
            Inventory inventory = player.getInventory();
            return inventory.computeIdForIndex(index).map(it -> it == id).orElse(false);
        }
        return true;
    }

    /**
     * Click the first index of an object.
     */
    private Event firstIndex(Player player, ByteMessage msg) {
        int interfaceId = msg.getShort(true, ByteTransform.A, ByteOrder.LITTLE);
        int index = msg.getShort(false, ByteTransform.A);
        int id = msg.getShort(true, ByteOrder.LITTLE);

        if (!validate(player, id, index, interfaceId)) {
            return null;
        }
        return new ItemFirstClickEvent(player, id, index, interfaceId);
    }

    /**
     * Click the second index of an object.
     */
    private Event secondIndex(Player player, ByteMessage msg) {
        int id = msg.getShort(false);
        int index = msg.getShort(false, ByteTransform.A);
        int interfaceId = msg.getShort(false, ByteTransform.A);

        if (!validate(player, id, index, interfaceId)) {
            return null;
        }
        return new ItemSecondClickEvent(player, id, index, interfaceId);
    }

    /**
     * Click the third index of an object.
     */
    private Event thirdIndex(Player player, ByteMessage msg) {
        int id = msg.getShort(false, ByteTransform.A);
        int index = msg.getShort(true, ByteTransform.A, ByteOrder.LITTLE);
        int interfaceId = msg.getShort(true, ByteTransform.A, ByteOrder.LITTLE);

        if (!validate(player, id, index, interfaceId)) {
            return null;
        }
        return new ItemThirdClickEvent(player, id, index, interfaceId);
    }

    /**
     * Click the fourth index of an object.
     */
    private Event fourthIndex(Player player, ByteMessage msg) {
        int interfaceId = msg.getShort(true, ByteTransform.A, ByteOrder.LITTLE);
        int index = msg.getShort(true, ByteTransform.A, ByteOrder.LITTLE);
        int id = msg.getShort(false, ByteTransform.A);

        if (!validate(player, id, index, interfaceId)) {
            return null;
        }
        return new ItemFourthClickEvent(player, id, index, interfaceId);
    }

    /**
     * Click the fifth index of an object.
     */
    private Event fifthIndex(Player player, ByteMessage msg) {
        int id = msg.getShort(false, ByteTransform.A);
        int interfaceId = msg.getShort(false);
        int index = msg.getShort(false, ByteTransform.A);

        if (!validate(player, id, index, interfaceId)) {
            return null;
        }
        return new ItemFifthClickEvent(player, id, index, interfaceId);
    }
}
