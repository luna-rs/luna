package io.luna.net.msg.in;

import io.luna.game.action.InteractionAction;
import io.luna.game.event.Event;
import io.luna.game.event.impl.PickupItemEvent;
import io.luna.game.model.Position;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link GameMessageReader} implementation that intercepts data when a ground item is clicked on.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PickupItemMessageReader extends GameMessageReader {

    // TODO Ensure item really exists
    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int y = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        int id = msg.getPayload().getShort(false);
        int x = msg.getPayload().getShort(false, ByteOrder.LITTLE);

        checkState(ItemDefinition.isIdValid(id), "invalid item id");

        Position position = new Position(x, y, player.getPosition().getZ());
        Event event = new PickupItemEvent(player, x, y, id);
        GroundItem item = new GroundItem(player.getContext(), id, 1, position);

        player.submitAction(new InteractionAction(player, item, event));
        return null;
    }
}
