package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.ItemOnItemEvent;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.item.Inventory;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when an item is used on another item.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ItemOnItemMessageReader extends GameMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int targetIndex = msg.getPayload().getShort(false);
        int usedIndex = msg.getPayload().getShort(false, ValueType.ADD);

        int targetId = msg.getPayload().getShort(ValueType.ADD, ByteOrder.LITTLE);
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
     * Validates the read data.
     *
     * @param player The player.
     * @param usedId The used item identifier.
     * @param targetId The target item identifier.
     * @param usedIndex The used item index.
     * @param targetIndex The target item index.
     * @param usedInterfaceId The used interface identifier.
     * @param targetInterfaceId The target interface identifier.
     * @return {@code true} if the decoded data is valid.
     */
    private boolean validate(Player player, int usedId, int targetId, int usedIndex, int targetIndex,
                             int usedInterfaceId, int targetInterfaceId) {

        checkState(targetIndex >= 0, "targetIndex is out of range");
        checkState(usedIndex >= 0, "usedIndex is out of range");
        checkState(ItemDefinition.isIdValid(targetId), "targetId is out of range");
        checkState(targetInterfaceId > 0, "targetInterfaceId is invalid interface identifier");
        checkState(ItemDefinition.isIdValid(usedId), "usedId is out of range");
        checkState(usedInterfaceId > 0, "usedInterfaceId is invalid identifier");

        switch (usedInterfaceId) {
            case 3214:
                Inventory inventory = player.getInventory();
                return inventory.computeIdForIndex(usedIndex).orElse(-1) == usedId;
        }

        switch (targetInterfaceId) {
            case 3214:
                Inventory inventory = player.getInventory();
                return inventory.computeIdForIndex(targetIndex).orElse(-1) == targetId;
        }
        return false;
    }
}
