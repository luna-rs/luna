package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.ItemOnItemEvent;
import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.Item;
import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.MessageReader;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link MessageReader} implementation that intercepts data sent when an item is used on another item.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ItemOnItemMessageReader extends MessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int targetIndex = msg.getPayload().getShort(false);
        int usedIndex = msg.getPayload().getShort(false, ByteTransform.A);

        int targetId = msg.getPayload().getShort(ByteTransform.A, ByteOrder.LITTLE);
        int targetInterfaceId = msg.getPayload().getShort(false);

        int usedId = msg.getPayload().getShort(ByteOrder.LITTLE);
        int usedInterfaceId = msg.getPayload().getShort(false);

        if (!validate(player, usedId, targetId, usedIndex, targetIndex, usedInterfaceId, targetInterfaceId)) {
            return null;
        }
        return new ItemOnItemEvent(player, usedId, targetId, usedIndex, targetIndex, usedInterfaceId,
            targetInterfaceId);
    }

    /**
     * Returns {@code true} if the decoded data is valid.
     */
    private boolean validate(Player player, int usedId, int targetId, int usedIndex, int targetIndex,
        int usedInterfaceId, int targetInterfaceId) {

        checkState(targetIndex >= 0, "targetIndex is out of range");
        checkState(usedIndex >= 0, "usedIndex is out of range");
        checkState(Item.isIdWithinRange(targetId), "targetId is out of range");
        checkState(targetInterfaceId > 0, "targetInterfaceId is invalid interface identifier");
        checkState(Item.isIdWithinRange(usedId), "usedId is out of range");
        checkState(usedInterfaceId > 0, "usedInterfaceId is invalid identifier");

        // TODO remove boilerplate
        switch (usedInterfaceId) {
        case 3214:
            Inventory inventory = player.getInventory();
            return inventory.computeIdForIndex(usedIndex).map(it -> it == usedId).orElse(false);
        }

        switch (targetInterfaceId) {
        case 3214:
            Inventory inventory = player.getInventory();
            return inventory.computeIdForIndex(targetIndex).map(it -> it == targetId).orElse(false);
        }
        return false;
    }
}
