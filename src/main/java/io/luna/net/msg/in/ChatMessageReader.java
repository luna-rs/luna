package io.luna.net.msg.in;

import engine.player.punishment.PunishmentHandler;
import io.luna.game.event.impl.ChatEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.util.StringUtils;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent on manual chat.
 *
 * @author lare96
 */
public final class ChatMessageReader extends GameMessageReader<ChatEvent> {

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
        if (PunishmentHandler.INSTANCE.notifyIfMuted(player)) {
            return false;
        }
        return event.getEffect() >= 0 && event.getColor() >= 0 && !event.getUnpackedMessage().isEmpty();
    }
}
