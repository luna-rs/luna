package io.luna.game.model.mob.block;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;

/**
 * An {@link UpdateBlock} implementation for the {@code CHAT} update block.
 *
 * @author lare96
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
        msg.putShort(chat.getColor() + chat.getEffect());
        msg.put(player.getRights().getClientValue(), ValueType.NEGATE);
        msg.put(chat.getMessage().length, ValueType.ADD);
        msg.putBytes(chat.getMessage(), ValueType.ADD);
    }

    @Override
    public int getPlayerMask() {
        return 0x40;
    }
}
