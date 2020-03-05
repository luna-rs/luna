package io.luna.net.msg.in;

import io.luna.LunaContext;
import io.luna.game.event.Event;
import io.luna.game.event.impl.DropItemEvent;
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

    // TODO Rename this class along with other item action/options, when you find out what they do
// TODO split up into 3 steps, decode, verify, handle, for all incoming packets
    /**
     * An asynchronous logger that will handle item drop logs.
     */
    private static final Logger logger = FileOutputType.ITEM_DROP.getLogger();

    /**
     * The logging level.
     */
    private static final Level ITEM_DROP = FileOutputType.ITEM_DROP.getLevel();

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int itemId = msg.getPayload().getShort(false, ValueType.ADD);
        int widgetId = msg.getPayload().getShort(false);
        int index = msg.getPayload().getShort(false, ValueType.ADD);
        if (!ItemDefinition.isIdValid(itemId)) {
            return null;
        }
        if (widgetId == 3214) {
            Item inventoryItem = player.getInventory().get(index);
            if (inventoryItem != null && inventoryItem.getId() == itemId) {
                dropItem(player.getContext(), player, inventoryItem, index);
            }
        }
        return null;
    }

    private void dropItem(LunaContext ctx, Player player, Item item, int index) {
        var def = item.getItemDef();
        int id = item.getId();
        int amount = item.getAmount();
        if (def.isTradeable() && !def.getInventoryActions().contains("Destroy")) {
            var dropItem = new GroundItem(ctx, id,amount, player.getPosition(), Optional.of(player));
            if (ctx.getWorld().getItems().register(dropItem)) {
                player.getInventory().set(index, null);
            } else {
                player.sendMessage("You cannot drop this here.");
            }
        } else {
            player.getInterfaces().open(new DestroyItemDialogueInterface(index, id));
        }
        logger.log(ITEM_DROP, "{}: {}(x{})", player.getUsername(), def.getName(), box(amount));
        ctx.getPlugins().post(new DropItemEvent(player, item.getId(), 3214, index));
    }
}