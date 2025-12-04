package io.luna.game.model.mob.block;

import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;

/**
 * An {@link UpdateBlock} implementation for the {@code TRANSFORM} update block.
 *
 * @author lare96
 */
public final class TransformUpdateBlock extends UpdateBlock {

    /**
     * Creates a new {@link TransformUpdateBlock}.
     */
    public TransformUpdateBlock() {
        super(UpdateFlag.TRANSFORM);
    }

    @Override
    public void encodeForNpc(ByteMessage msg, UpdateBlockData data) {
        msg.putShort(data.transform, ValueType.ADD);
    }

    @Override
    public int getNpcMask() {
        return 0x1;
    }
}