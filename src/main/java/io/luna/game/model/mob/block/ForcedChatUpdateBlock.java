package io.luna.game.model.mob.block;

import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;

/**
 * An {@link UpdateBlock} implementation for the {@code FORCED_CHAT} update block.
 *
 * @author lare96
 */
public final class ForcedChatUpdateBlock extends UpdateBlock {

    /**
     * Creates a new {@link ForcedChatUpdateBlock}.
     */
    public ForcedChatUpdateBlock() {
        super(UpdateFlag.SPEAK);
    }

    @Override
    public void encodeForPlayer(ByteMessage msg, UpdateBlockData data) {
        msg.putString(data.speak);
    }

    @Override
    public void encodeForNpc(ByteMessage msg, UpdateBlockData data) {
        msg.putString(data.speak);
    }

    @Override
    public int getPlayerMask() {
        return 0x10;
    }

    @Override
    public int getNpcMask() {
        return 0x20;
    }
}