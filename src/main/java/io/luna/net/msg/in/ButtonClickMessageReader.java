package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.ButtonClickEvent;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerRights;
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
    public Event read(Player player, GameMessage msg) throws Exception {
        int buttonId = msg.getPayload().getShort(false);
        checkState(buttonId >= 0, "buttonId < 0");
        if (player.getRights().equalOrGreater(PlayerRights.DEVELOPER)) {
            player.sendMessage("[Debug] Button: " + buttonId);
        }
        return new ButtonClickEvent(player, buttonId);
    }
}
