package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.DropItemEvent;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ValueType;
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
        int itemId = msg.getPayload().getShort(false, ValueType.ADD);
        int widgetId = msg.getPayload().getShort(false);
        int index = msg.getPayload().getShort(false, ValueType.ADD);
        if (!ItemDefinition.isIdValid(itemId)) {
            return null;
        }
        return new DropItemEvent(player, itemId, widgetId, index);
    }
}