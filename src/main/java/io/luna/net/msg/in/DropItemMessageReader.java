package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.DropItemEvent;
import io.luna.game.model.World;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.dialogue.DestroyItemDialogueInterface;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.util.LoggingSettings.FileOutputType;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

import static org.apache.logging.log4j.util.Unbox.box;

/**
 * A {@link GameMessageReader} implementation that intercepts data for when an item is dropped.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class DropItemMessageReader extends GameMessageReader {

    /**
     * An asynchronous logger that will handle item drop logs.
     */
    private static final Logger logger = FileOutputType.ITEM_DROP.getLogger();

    /**
     * The {@code ITEM_DROP} logging level.
     */
    private static final Level ITEM_DROP = FileOutputType.ITEM_DROP.getLevel();

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        World world = player.getWorld();
        int itemId = msg.getPayload().getShort(false, ValueType.ADD);
        int widgetId = msg.getPayload().getShort(false);
        int index = msg.getPayload().getShort(false, ValueType.ADD);
        boolean isTradeable = ItemDefinition.ALL.retrieve(itemId).isTradeable();

        // TODO Make sure item exists in inventory... but move this to where the widget id is checked
        Item inventoryItem = player.getInventory().get(index);
        if (inventoryItem == null || inventoryItem.getId() != itemId) {
            return null;
        }

        if (!isTradeable) {
            // Open destroy interface.
            player.getInterfaces().open(new DestroyItemDialogueInterface(index, itemId));
        } else {
            // TODO  Items may only be dropped from your inventory, make sure widget id is inv
            // TODO Items must be checked to ensure they have a 'drop' option
            // Drop item.
            Item item = player.getInventory().get(index);
            GroundItem dropItem = new GroundItem(player.getContext(), itemId, inventoryItem.getAmount(),
                    player.getPosition(), Optional.of(player));
            world.getItems().register(dropItem);
            player.getInventory().set(index, null);
            logger.log(ITEM_DROP, "{}: {}(x{})", player.getUsername(),item.getItemDef().getName(), box(item.getAmount()));
        }
        return new DropItemEvent(player, itemId, widgetId, index);
    }
}