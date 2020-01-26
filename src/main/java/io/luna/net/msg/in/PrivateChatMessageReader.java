package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.CreateFriendedPlayerEvent;
import io.luna.game.event.impl.CreateIgnoredPlayerEvent;
import io.luna.game.event.impl.DeleteFriendedPlayerEvent;
import io.luna.game.event.impl.DeleteIgnoredPlayerEvent;
import io.luna.game.event.impl.PrivateChatEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.util.LoggingSettings.FileOutputType;
import io.luna.util.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent on private chat or
 * friend/ignore list changes.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class PrivateChatMessageReader extends GameMessageReader {

    /**
     * An asynchronous logger that will handle private message logs.
     */
    private static final Logger logger = FileOutputType.PRIVATE_MESSAGE.getLogger();

    /**
     * The {@code PRIVATE_MESSAGE} logging level.
     */
    private static final Level PRIVATE_MESSAGE = FileOutputType.PRIVATE_MESSAGE.getLevel();


    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        long name = msg.getPayload().getLong();

        checkState(name > 0, "Name value must be above 0.");
        switch (opcode) {
            case 188:
                return new CreateFriendedPlayerEvent(player, name);
            case 215:
                return new DeleteFriendedPlayerEvent(player, name);
            case 133:
                return new CreateIgnoredPlayerEvent(player, name);
            case 74:
                return new DeleteIgnoredPlayerEvent(player, name);
            case 126:
                return privateChat(player, name, msg.getPayload());
        }
        return null;
    }

    /**
     * Handles event posting for private chat.
     *
     * @param player The player.
     * @param name The receiver's name.
     * @param msg The payload.
     * @return The event.
     */
    private Event privateChat(Player player, long name, ByteMessage msg) {
        if (!player.getFriends().contains(name)) {
            player.sendMessage("That player is not on your friends list.");
            return null;
        } else {
            byte[] message = msg.getBytes(msg.getBuffer().readableBytes());

            checkState(message.length > 0, "Message length must be above 0.");
            logger.log(PRIVATE_MESSAGE, "{} @ {}: {}", player::getUsername, () -> StringUtils.decodeFromBase37(name), () -> StringUtils.unpackText(message));
            return new PrivateChatEvent(player, name, message);
        }
    }
}