package io.luna.net.msg.in;

import io.luna.game.event.impl.TextInputEvent;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.overlay.TextInput;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.util.StringUtils;

/**
 * A {@link GameMessageReader} implementation that intercepts data for when a string is entered on an {@link TextInput}.
 *
 * @author lare96
 */
public final class TextInputMessageReader extends GameMessageReader<TextInputEvent> {

    @Override
    public TextInputEvent decode(Player player, GameMessage msg) {
        String text = StringUtils.decodeFromBase37(msg.getPayload().getLong());
        return new TextInputEvent(player, text);
    }

    @Override
    public boolean validate(Player player, TextInputEvent event) {
        return player.getOverlays().contains(TextInput.class);
    }
}