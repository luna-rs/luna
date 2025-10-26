package io.luna.net.msg.in;

import io.luna.game.event.impl.PrivateChatEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.util.logging.LoggingSettings.FileOutputType;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent on private chat.
 *
 * @author lare96
 */
public final class PrivateChatMessageReader extends GameMessageReader<PrivateChatEvent> {

    @Override
    public PrivateChatEvent decode(Player player, GameMessage msg) {
        long name = msg.getPayload().getLong();
        byte[] message = msg.getPayload().getBytes(msg.getPayload().getBuffer().readableBytes());
        return new PrivateChatEvent(player, name, message);
    }

    @Override
    public boolean validate(Player player, PrivateChatEvent event) {
        if (!player.getFriends().contains(event.getName())) {
            player.sendMessage("That player is not on your friends list.");
            return false;
        }
        return event.getMessage().length > 0;
    }
}