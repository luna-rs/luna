package io.luna.net.msg.in;

import io.luna.LunaContext;
import io.luna.game.event.impl.DropItemEvent;
import io.luna.game.model.chunk.ChunkUpdatableView;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.dialogue.DestroyItemDialogueInterface;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.util.logging.LoggingSettings.FileOutputType;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import world.player.Sounds;

import static org.apache.logging.log4j.util.Unbox.box;

/**
 * A {@link GameMessageReader} implementation that intercepts data for when an item is dropped.
 *
 * @author lare96
 */
public final class DropItemMessageReader extends GameMessageReader<DropItemEvent> {

    /**
     * An asynchronous logger that will handle item drop logs.
     */
    private static final Logger logger = FileOutputType.ITEM_DROP.getLogger();

    /**
     * The logging level.
     */
    private static final Level ITEM_DROP = FileOutputType.ITEM_DROP.getLevel();

    @Override
    public DropItemEvent decode(Player player, GameMessage msg) {
        int index = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        int itemId = msg.getPayload().getShort(false, ByteOrder.LITTLE, ValueType.ADD);
        int widgetId = msg.getPayload().getShort(false, ByteOrder.LITTLE, ValueType.ADD);
        return new DropItemEvent(player, itemId, widgetId, index);
    }

    @Override
    public boolean validate(Player player, DropItemEvent event) {
        int itemId = event.getItemId();
        int index = event.getIndex();

        if (event.getWidgetId() != 3214 || // Click didn't come from inventory.
                !ItemDefinition.isIdValid(itemId) || // Item ID invalid.
                event.getIndex() < 0 || // Index < 0.
                event.getIndex() >= player.getInventory().capacity()) { // Index exceeds inventory capacity.
            return false;
        }

        // Check if inventory item ID is equal to event item ID.
        return player.getInventory().contains(index, itemId);
    }

    @Override
    public void handle(Player player, DropItemEvent event) {
        Item inventoryItem = player.getInventory().get(event.getIndex());

        ItemDefinition itemDef = inventoryItem.getItemDef();
        dropItem(player.getContext(), player, inventoryItem, itemDef, event);
        logger.log(ITEM_DROP, "{}: {}(x{})", player.getUsername(), itemDef.getName(), box(inventoryItem.getAmount()));
    }

    /**
     * Drops {@code inventoryItem} if it's tradeable, otherwise opens the {@link DestroyItemDialogueInterface}.
     *
     * @param ctx The context instance.
     * @param player The player.
     * @param inventoryItem The inventory item.
     * @param event The event instance.
     */
    private void dropItem(LunaContext ctx, Player player, Item inventoryItem, ItemDefinition itemDef, DropItemEvent event) {
        player.interruptAction();
        if (itemDef.isTradeable() && !itemDef.getInventoryActions().contains("Destroy")) {
            GroundItem groundItem = new GroundItem(ctx, inventoryItem.getId(), inventoryItem.getAmount(),
                    player.getPosition(), ChunkUpdatableView.localView(player));
            if (ctx.getWorld().getItems().register(groundItem)) {
                player.getInventory().set(event.getIndex(), null);
                player.playSound(Sounds.DROP_ITEM);
            } else {
                player.sendMessage("You cannot drop this here.");
            }
        } else {
            DestroyItemDialogueInterface destroyItemInterface =
                    new DestroyItemDialogueInterface(event.getIndex(), inventoryItem.getId());
            player.getInterfaces().open(destroyItemInterface);
        }
    }
}