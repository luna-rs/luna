package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.button.ButtonClickEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when a widget is clicked.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ButtonClickMessageReader extends GameMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) {
        int buttonId = msg.getPayload().getShort(false);
        checkState(buttonId >= 0, "buttonId < 0");
        return new ButtonClickEvent(player, buttonId);
    }
}
