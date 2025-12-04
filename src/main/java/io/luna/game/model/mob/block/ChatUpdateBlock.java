package io.luna.game.model.mob.block;

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
    public void encodeForPlayer(ByteMessage msg, UpdateBlockData data) {
        msg.putShort(data.chat.getColor() + data.chat.getEffect());
        msg.put(data.chat.getRights(), ValueType.NEGATE);
        msg.put(data.chat.getMessage().length, ValueType.ADD);
        msg.putBytes(data.chat.getMessage(), ValueType.ADD);
    }

    @Override
    public int getPlayerMask() {
        return 0x40;
    }
}
