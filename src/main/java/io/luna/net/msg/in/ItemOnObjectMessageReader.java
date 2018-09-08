package io.luna.net.msg.in;

import io.luna.game.action.InteractionAction;
import io.luna.game.event.Event;
import io.luna.game.event.impl.ItemOnObjectEvent;
import io.luna.game.model.Position;
import io.luna.game.model.def.ObjectDefinition;
import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.game.model.object.GameObject;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.MessageReader;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link MessageReader} implementation that intercepts data sent when an item is used on an object.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ItemOnObjectMessageReader extends MessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int itemInterfaceId = msg.getPayload().getShort(false);
        int objectId = msg.getPayload().getShort(true, ByteOrder.LITTLE);
        int objectY = msg.getPayload().getShort(true, ByteTransform.A, ByteOrder.LITTLE);
        int itemIndexId = msg.getPayload().getShort(true, ByteOrder.LITTLE);
        int objectX = msg.getPayload().getShort(true, ByteTransform.A, ByteOrder.LITTLE);
        int itemId = msg.getPayload().getShort(false);
        int size = ObjectDefinition.ALL.retrieve(objectId).getSize();

        if (!validate(player, itemId, itemIndexId, itemInterfaceId, objectId, objectX, objectY)) {
            return null;
        }
        Position position = new Position(objectX, objectY, player.getPosition().getZ());
        GameObject object = new GameObject(player.getContext(), objectId, position); // TODO temporary
        Event event = new ItemOnObjectEvent(player, itemId, itemIndexId,
                itemInterfaceId, objectId, objectX, objectY);
        player.submitAction(new InteractionAction(player, object, event));
        return null;
    }

    /**
     * Returns {@code true} if the data decoded is valid.
     */
    private boolean validate(Player player, int itemId, int itemIndex, int itemInterfaceId, int objectId,
                             int objectX, int objectY) {
        checkState(itemInterfaceId > 0, "itemInterfaceId out of range");
        checkState(objectId > 0, "objectId out of range");
        checkState(objectY > 0, "objectY out of range");
        checkState(itemIndex >= 0, "itemIndex out of range");
        checkState(objectX > 0, "objectX out of range");
        checkState(Item.VALID_IDS.contains(itemId), "itemId out of range");

        // TODO make sure object exists
        switch (itemInterfaceId) {
            case 3214:
                Inventory inventory = player.getInventory();
                return inventory.get(itemIndex).getId() == itemId;
        }
        return false;
    }
}
