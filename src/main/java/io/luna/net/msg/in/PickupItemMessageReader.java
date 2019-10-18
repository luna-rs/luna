package io.luna.net.msg.in;

import io.luna.game.action.InteractionAction;
import io.luna.game.event.Event;
import io.luna.game.event.impl.PickupItemEvent;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.util.LoggingSettings.FileOutputType;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import java.util.stream.Stream;

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
        Position pos = new Position(x, y, player.getPosition().getZ());
        checkState(ItemDefinition.isIdValid(id), "invalid item id");

        if (!player.getPosition().isViewable(pos)) {
            return null;
        }

        // Try to pickup the item (verification).
        Stream<GroundItem> localItems = player.getChunks().getChunk(pos).stream(EntityType.ITEM);

        localItems.filter(item -> item.getPosition().equals(pos) && item.getId() == id).
                findFirst().
                ifPresent(item -> {
                    Event event = new PickupItemEvent(player, x, y, id);
                    player.submitAction(new InteractionAction(player, item) {
                        @Override
                        public void execute() {
                            player.getPlugins().post(event);
                            logger.log(ITEM_PICKUP, "{}: {}(x{})", player.getUsername(), item.def().getName(), box(item.getAmount()));
                        }
                    });
                });
        return null;
    }
}
