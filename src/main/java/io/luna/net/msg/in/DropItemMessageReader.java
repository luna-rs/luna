package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.DropItemEvent;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.dialogue.DestroyItemDialogueInterface;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data for when an item is dropped.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class DropItemMessageReader extends GameMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int itemId = msg.getPayload().getShort(false, ByteTransform.A);
        int widgetId = msg.getPayload().getShort(false);
        int index = msg.getPayload().getShort(false, ByteTransform.A);
        boolean isTradeable = ItemDefinition.ALL.retrieve(itemId).isTradeable();

        // Make sure item exists in inventory.
        if (!player.getInventory().nonNullGet(index).
                map(item -> item.getId() == itemId).isPresent()) {
            return null;
        }

        if (!isTradeable) {
            // Open destroy interface.
            player.getInterfaces().open(new DestroyItemDialogueInterface(index, itemId));
        } else {
            // Drop item.
        }
        return new DropItemEvent(player, itemId, widgetId, index);
    }
}