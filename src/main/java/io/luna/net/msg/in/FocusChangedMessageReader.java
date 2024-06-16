package io.luna.net.msg.in;

import io.luna.Luna;
import io.luna.game.event.impl.NullEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data for when the client focus is changed.
 *
 * @author lare96
 */
public final class FocusChangedMessageReader extends GameMessageReader<NullEvent> {

    @Override
    public NullEvent decode(Player player, GameMessage msg) {
        boolean focused = msg.getPayload().get(false) == 1;
        if (Luna.settings().game().betaMode()) {
            player.sendMessage("[FocusChangedMessageReader] focus: " + focused);
        }
        return NullEvent.INSTANCE;
    }
}
