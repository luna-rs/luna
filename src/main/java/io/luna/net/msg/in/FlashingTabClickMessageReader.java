package io.luna.net.msg.in;

import io.luna.game.event.impl.FlashingTabClickEvent;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.GameTabSet.TabIndex;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data for when a flashing tab is clicked.
 *
 * @author lare96
 */
public final class FlashingTabClickMessageReader extends GameMessageReader<FlashingTabClickEvent> {

    @Override
    public FlashingTabClickEvent decode(Player player, GameMessage msg) {
        int index = msg.getPayload().get(false);
        return new FlashingTabClickEvent(player, TabIndex.forIndex(index));
    }

    @Override
    public boolean validate(Player player, FlashingTabClickEvent event) {
        return event.getTab() != null;
    }
}
