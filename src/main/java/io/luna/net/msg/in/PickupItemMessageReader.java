package io.luna.net.msg.in;

import io.luna.game.event.impl.PickupItemEvent;
import io.luna.game.model.Position;
import io.luna.game.model.World;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.util.logging.LoggingSettings.FileOutputType;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import static org.apache.logging.log4j.util.Unbox.box;

/**
 * A {@link GameMessageReader} implementation that intercepts data when a ground item is clicked on.
 *
 * @author lare96
 */
public final class PickupItemMessageReader extends GameMessageReader<PickupItemEvent> {

    /**
     * An asynchronous logger that will handle item pickup logs.
     */
    private static final Logger logger = FileOutputType.ITEM_PICKUP.getLogger();

    /**
     * The {@code ITEM_PICKUP} logging level.
     */
    private static final Level ITEM_PICKUP = FileOutputType.ITEM_PICKUP.getLevel();

    @Override
    public PickupItemEvent decode(Player player, GameMessage msg) {
        int itemId = msg.getPayload().getShort(false, ByteOrder.LITTLE, ValueType.ADD);
        int itemX = msg.getPayload().getShort(false, ByteOrder.LITTLE, ValueType.ADD);
        int itemY = msg.getPayload().getShort(false, ValueType.ADD);
        Position itemPosition = new Position(itemX, itemY, player.getPosition().getZ());
        GroundItem groundItem = player.getWorld().getItems().findAll(itemPosition).
                filter(item -> item.getId() == itemId &&
                        item.isVisibleTo(player)).findFirst().orElse(null);
        return new PickupItemEvent(player, groundItem);
    }

    @Override
    public boolean validate(Player player, PickupItemEvent event) {
        if (event.getTargetItem() == null) {
            return false;
        }
        World world = player.getWorld();
        Item pickupItem = event.getTargetItem().toItem();
        if (!player.getInventory().hasSpaceFor(pickupItem)) {
            player.sendMessage("You do not have enough space in your inventory.");
            return false;
        }
        if (world.getItems().unregister(event.getTargetItem())) {
            player.getInventory().add(pickupItem);
            return true;
        }
        return false;
    }

    @Override
    public void handle(Player player, PickupItemEvent event) {
        logger.log(ITEM_PICKUP, "{}: {}(x{})", player.getUsername(), event.getTargetItem().def().getName(), box(event.getTargetItem().getAmount()));
    }
}
