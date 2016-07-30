package io.luna.net.msg.in;

import io.luna.game.action.DistancedAction;
import io.luna.game.event.Event;
import io.luna.game.event.impl.PickupItemEvent;
import io.luna.game.model.Position;
import io.luna.game.model.item.Item;
import io.luna.game.model.mobile.Player;
import io.luna.game.plugin.PluginManager;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.MessageReader;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link MessageReader} implementation that decodes data sent when a {@link Player} tries to pick up an item.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PickupItemMessageReader extends MessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int y = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        int id = msg.getPayload().getShort(false);
        int x = msg.getPayload().getShort(false, ByteOrder.LITTLE);

        checkState(Item.isIdentifier(id), "invalid item id");

        PluginManager plugins = player.getPlugins();
        Position position = new Position(x, y, player.getPosition().getZ());

        player.submitAction(new DistancedAction<Player>(player, position, 0, true) {
            @Override
            protected void execute() {
                plugins.post(new PickupItemEvent(x, y, id), player);
            }
        });
        return null;
    }
}
