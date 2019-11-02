package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.ChatEvent;
import io.luna.game.model.mob.Chat;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.util.LoggingSettings.FileOutputType;
import io.luna.util.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent on manual chat.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ChatMessageReader extends GameMessageReader {

    /**
     * An asynchronous logger that will handle chat logs.
     */
    private static final Logger logger = FileOutputType.CHAT.getLogger();

    /**
     * The {@code CHAT} logging level.
     */
    private static final Level CHAT = FileOutputType.CHAT.getLevel();

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int effects = msg.getPayload().get(false, ValueType.SUBTRACT);
        int color = msg.getPayload().get(false, ValueType.SUBTRACT);
        int size = (msg.getSize() - 2);
        byte[] message = msg.getPayload().getBytesReverse(size, ValueType.ADD);

        checkState(effects >= 0, "invalid effects value");
        checkState(color >= 0, "invalid color value");
        checkState(size > 0, "invalid size, not large enough");

        if (player.isMuted()) {
            return null;
        }
        logger.log(CHAT, "{}: {}", player::getUsername, () -> StringUtils.unpackText(message));
        player.chat(new Chat(message, color, effects));
        return new ChatEvent(player, effects, color, size, message);
    }
}
