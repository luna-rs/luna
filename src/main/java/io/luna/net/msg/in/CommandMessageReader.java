package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.CommandEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that decodes data sent when a {@link Player} tries to
 * activate a command.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class CommandMessageReader extends GameMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) {
        String string = msg.getPayload().getString();
        string = string.toLowerCase();
        int index = string.indexOf(' ');

        if (index == -1) {
            return new CommandEvent(player, string);
        }

        String name = string.substring(0, index);
        String[] args = string.substring(index + 1).split(" ");
        return new CommandEvent(player, name, args);
    }
}
