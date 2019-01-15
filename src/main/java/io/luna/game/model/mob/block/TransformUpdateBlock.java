package io.luna.game.model.mob.block;

import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;

/**
 * An {@link UpdateBlock} implementation for the {@code TRANSFORM} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class TransformUpdateBlock extends UpdateBlock {

    /**
     * Creates a new {@link TransformUpdateBlock}.
     */
    public TransformUpdateBlock() {
        super(UpdateFlag.TRANSFORM);
    }

    @Override
    public void encodeForNpc(Npc npc, ByteMessage msg) {
        int transformId = unwrap(npc.getTransformId());
        msg.putShort(transformId, ValueType.ADD, ByteOrder.LITTLE);
    }

    @Override
    public int getNpcMask() {
        return 2;
    }
}