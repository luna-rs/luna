package io.luna.net.msg.in;

import io.luna.game.event.impl.ChatEvent;
import io.luna.game.model.mob.block.Chat;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.util.StringUtils;
import io.luna.util.logging.LoggingSettings.FileOutputType;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent on manual chat.
 *
 * @author lare96
 */
public final class ChatMessageReader extends GameMessageReader<ChatEvent> {

    /**
     * An asynchronous logger that will handle chat logs.
     */
    private static final Logger logger = FileOutputType.CHAT.getLogger();

    /**
     * The {@code CHAT} logging level.
     */
    private static final Level CHAT = FileOutputType.CHAT.getLevel();

    @Override
    public ChatEvent decode(Player player, GameMessage msg) {
        int color = msg.getPayload().get(false, ValueType.NEGATE);
        int effect = msg.getPayload().get(false, ValueType.ADD);
        int size = msg.getSize() - 2;
        byte[] message = msg.getPayload().getBytes(size);
        String unpackedMessage = StringUtils.unpackText(message);
        return new ChatEvent(player, effect, color, size, message, unpackedMessage);
    }

    @Override
    public boolean validate(Player player, ChatEvent event) {
        return event.getEffect() >= 0 && event.getColor() >= 0 && size >= 1 && !player.isMuted();
    }

    @Override
    public void handle(Player player, ChatEvent event) {
        logger.log(CHAT, "{}: {}", player::getUsername, event::getUnpackedMessage);
        player.chat(new Chat(event.getMessage(), event.getColor(), event.getEffect()));
    }
}
