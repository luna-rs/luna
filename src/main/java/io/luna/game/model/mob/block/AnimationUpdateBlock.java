package io.luna.game.model.mob.block;

import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;

/**
 * An {@link UpdateBlock} implementation for the {@code ANIMATION} update block.
 *
 * @author lare96
 */
public final class AnimationUpdateBlock extends UpdateBlock {

    /**
     * Creates a new {@link AnimationUpdateBlock}.
     */
    public AnimationUpdateBlock() {
        super(UpdateFlag.ANIMATION);
    }

    @Override
    public void encodeForPlayer(ByteMessage msg, UpdateBlockData data) {
        msg.putShort(data.animation.getId());
        msg.put(data.animation.getDelay(), ValueType.ADD);
    }

    @Override
    public void encodeForNpc(ByteMessage msg, UpdateBlockData data) {
        msg.putShort(data.animation.getId());
        msg.put(data.animation.getDelay(), ValueType.SUBTRACT);
    }

    @Override
    public int getPlayerMask() {
        return 0x8;
    }

    @Override
    public int getNpcMask() {
        return 0x2;
    }
}
