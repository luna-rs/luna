package io.luna.game.model.mob.block;

import io.luna.game.model.mob.Chat;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;

/**
 * An {@link UpdateBlock} implementation for the {@code CHAT} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ChatUpdateBlock extends UpdateBlock {

    /**
     * Creates a new {@link ChatUpdateBlock}.
     */
    public ChatUpdateBlock() {
        super(UpdateFlag.CHAT);
    }

    @Override
    public void encodeForPlayer(Player player, ByteMessage msg) {
        Chat chat = unwrap(player.getChat());
        msg.putShort(chat.getColor() + chat.getEffects(), ByteOrder.LITTLE);
        msg.put(player.getRights().getClientValue());
        msg.put(chat.getMessage().length, ValueType.NEGATE);
        msg.putBytesReverse(chat.getMessage());
    }

    @Override
    public int getPlayerMask() {
        return 128;
    }
}
