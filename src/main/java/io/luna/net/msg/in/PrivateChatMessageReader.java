package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.chat.PrivateChatEvent;
import io.luna.game.event.chat.PrivateChatListChangeEvent;
import io.luna.game.event.chat.PrivateChatListChangeEvent.ChangeType;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent on private chat or
 * friend/ignore list changes.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class PrivateChatMessageReader extends GameMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) {
        long name = msg.getPayload().getLong();

        checkState(name > 0, "Name value must be above 0.");
        
        switch (opcode) {
            case 188:
                return new PrivateChatListChangeEvent(player, name, ChangeType.ADD_FRIEND);
            case 215:
                return new PrivateChatListChangeEvent(player, name, ChangeType.REMOVE_FRIEND);
            case 133:
                return new PrivateChatListChangeEvent(player, name, ChangeType.ADD_IGNORE);
            case 74:
                return new PrivateChatListChangeEvent(player, name, ChangeType.REMOVE_IGNORE);
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
        }
    
        byte[] message = msg.getBytes(msg.getBuffer().readableBytes());
        checkState(message.length > 0, "Message length must be above 0.");
        return new PrivateChatEvent(player, name, message);
    }
}