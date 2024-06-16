package io.luna.net.msg.in;

import io.luna.game.event.impl.PrivateChatEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.util.StringUtils;
import io.luna.util.logging.LoggingSettings.FileOutputType;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent on private chat.
 *
 * @author lare96
 */
public final class PrivateChatMessageReader extends GameMessageReader<PrivateChatEvent> {

    /**
     * An asynchronous logger that will handle private message logs.
     */
    private static final Logger logger = FileOutputType.PRIVATE_MESSAGE.getLogger();

    /**
     * The {@code PRIVATE_MESSAGE} logging level.
     */
    private static final Level PRIVATE_MESSAGE = FileOutputType.PRIVATE_MESSAGE.getLevel();


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

    @Override
    public void handle(Player player, PrivateChatEvent event) {
        logger.log(PRIVATE_MESSAGE, "{} --> {}: {}", player::getUsername,
                () -> StringUtils.decodeFromBase37(event.getName()),
                event::getUnpackedMessage);
    }
}