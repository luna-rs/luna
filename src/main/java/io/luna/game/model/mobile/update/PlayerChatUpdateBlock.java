package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Chat;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.update.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;

/**
 * A {@link PlayerUpdateBlock} implementation for the {@code CHAT} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerChatUpdateBlock extends PlayerUpdateBlock {

    /**
     * Creates a new {@link PlayerChatUpdateBlock}.
     */
    public PlayerChatUpdateBlock() {
        super(0x80, UpdateFlag.CHAT);
    }

    @Override
    public void write(Player mob, ByteMessage msg) {
        Chat chat = mob.getChat().get();
        msg.putShort(((chat.getColor() & 0xff) << 8) + (chat.getEffects() & 0xff), ByteOrder.LITTLE);
        msg.put(mob.getRights().getClientValue());
        msg.put(chat.getMessage().length, ByteTransform.C);
        msg.putBytesReverse(chat.getMessage());
    }
}
