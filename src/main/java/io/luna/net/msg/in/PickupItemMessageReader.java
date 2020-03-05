package io.luna.net.msg.in;

import io.luna.game.action.InteractionAction;
import io.luna.game.event.Event;
import io.luna.game.event.impl.PickupItemEvent;
import io.luna.game.model.Position;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.util.LoggingSettings.FileOutputType;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import static com.google.common.base.Preconditions.checkState;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * A {@link GameMessageReader} implementation that intercepts data when a ground item is clicked on.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PickupItemMessageReader extends GameMessageReader {

    /**
     * An asynchronous logger that will handle item pickup logs.
     */
    private static final Logger logger = FileOutputType.ITEM_PICKUP.getLogger();

    /**
     * The {@code ITEM_PICKUP} logging level.
     */
    private static final Level ITEM_PICKUP = FileOutputType.ITEM_PICKUP.getLevel();

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int y = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        int id = msg.getPayload().getShort(false);
        int x = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        var position = new Position(x, y, player.getPosition().getZ());
        checkState(ItemDefinition.isIdValid(id), "invalid item id");

        // Invalid item id.
        if (!ItemDefinition.isIdValid(id)) {
            return null;
        }

        // Item is too far.
        if (!player.getPosition().isViewable(position)) {
            return null;
        }

        var foundItem = player.getWorld().getItems().findAll(position).
                filter(item -> item.getId() == id &&
                        item.isVisibleTo(player)).findFirst();
        if (foundItem.isEmpty()) { // Item doesn't exist.
            return null;
        }

        // Send the event once the player reaches the item.
        var groundItem = foundItem.get();
        player.submitAction(new InteractionAction(player, groundItem) {
            @Override
            public void execute() {
                var item = groundItem.toItem();
                if (!player.getInventory().hasSpaceFor(item)) {
                    player.sendMessage("You do not have enough space in your inventory.");
                    return;
                }
                if (world.getItems().unregister(groundItem)) {
                    player.getInventory().add(item);
                    player.getPlugins().post(new PickupItemEvent(player, groundItem));
                    logger.log(ITEM_PICKUP, "{}: {}(x{})", player.getUsername(), groundItem.def().getName(), box(item.getAmount()));
                }
            }
        });
        return null;
    }
}
