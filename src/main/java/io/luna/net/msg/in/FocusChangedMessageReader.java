package io.luna.net.msg.in;

import io.luna.Luna;
import io.luna.game.event.impl.FocusChangedEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data for when the client focus is changed.
 *
 * @author lare96
 */
public final class FocusChangedMessageReader extends GameMessageReader<FocusChangedEvent> {

    @Override
    public FocusChangedEvent decode(Player player, GameMessage msg) {
        boolean focused = msg.getPayload().get(false) == 1;
        return new FocusChangedEvent(player, focused);
    }

    @Override
    public void handle(Player player, FocusChangedEvent event) {
        if (Luna.settings().game().betaMode()) {
            player.sendMessage("[FocusChangedMessageReader] focus: " + event.isFocused());
        }
    }
}
