package io.luna.game.model.mob.update;

import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.update.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;

/**
 * An {@link NpcUpdateBlock} implementation for the {@code TRANSFORM} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcTransformUpdateBlock extends NpcUpdateBlock {

    /**
     * Creates a new {@link NpcTransformUpdateBlock}.
     */
    public NpcTransformUpdateBlock() {
        super(2, UpdateFlag.TRANSFORM);
    }

    @Override
    public void write(Npc mob, ByteMessage msg) {
        int transformId = mob.getTransformId().getAsInt();
        msg.putShort(transformId, ByteTransform.A, ByteOrder.LITTLE);
    }
}