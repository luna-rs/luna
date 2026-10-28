package io.luna.net.msg.in;

import io.luna.game.event.impl.DropItemEvent;
import io.luna.game.event.impl.ItemClickEvent.ItemFifthClickEvent;
import io.luna.game.event.impl.PlayerEvent;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * Routes the client's fifth item action, shared by inventory drops and sliding-puzzle moves.
 * Widget 3214 retains the normal drop event; widget 6980 uses the existing validated puzzle
 * click path. Other widgets cannot use this packet to drop inventory items or move puzzle tiles.
 *
 * @author lare96
 */
public final class DropItemMessageReader extends GameMessageReader<PlayerEvent> {

    /**
     * Reuses the item-grid validation policy, including puzzle ownership, displayed tiles and
     * the active puzzle interface. Decoding stays here because opcode 4 uses the drop layout.
     */
    private final ItemClickMessageReader itemClicks = new ItemClickMessageReader();

    @Override
    public PlayerEvent decode(Player player, GameMessage msg) {
        int index = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        int itemId = msg.getPayload().getShort(false, ByteOrder.LITTLE, ValueType.ADD);
        int widgetId = msg.getPayload().getShort(false, ByteOrder.LITTLE, ValueType.ADD);
        if (widgetId == 6980) {
            return new ItemFifthClickEvent(player, itemId, index, widgetId);
        }
        return new DropItemEvent(player, itemId, widgetId, index);
    }

    @Override
    public boolean validate(Player player, PlayerEvent event) {
        if (event instanceof ItemFifthClickEvent click) {
            return click.getInterfaceId() == 6980 && itemClicks.validate(player, click);
        }
        if (!(event instanceof DropItemEvent drop)) {
            return false;
        }
        int itemId = drop.getItemId();
        int index = drop.getIndex();

        if (drop.getWidgetId() != 3214 || // Click didn't come from inventory.
                !ItemDefinition.isIdValid(itemId) || // Item ID invalid.
                index < 0 || // Index < 0.
                index >= player.getInventory().capacity()) { // Index exceeds inventory capacity.
            return false;
        }

        // Check if inventory item ID is equal to event item ID.
        return player.getInventory().contains(index, itemId);
    }
}
