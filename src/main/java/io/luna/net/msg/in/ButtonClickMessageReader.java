package io.luna.net.msg.in;

import io.luna.Luna;
import io.luna.game.event.impl.ButtonClickEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when a widget is clicked.
 *
 * @author lare96
 */
public final class ButtonClickMessageReader extends GameMessageReader<ButtonClickEvent> {

    @Override
    public ButtonClickEvent decode(Player player, GameMessage msg) {
        int buttonId = msg.getPayload().getShort(false);
        return new ButtonClickEvent(player, buttonId);
    }

    @Override
    public boolean validate(Player player, ButtonClickEvent event) {
        if (event.getId() < 0) {
            return false;
        }
        if (Luna.settings().game().betaMode()) {
            player.sendMessage(event);
        }
        return true;
    }
}
