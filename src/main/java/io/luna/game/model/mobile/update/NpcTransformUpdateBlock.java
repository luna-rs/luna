package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;

/**
 * An {@link NpcUpdateBlock} implementation that handles the {@code TRANSFORM} update block.
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
        msg.putShort(mob.getTransformId(), ByteTransform.A, ByteOrder.LITTLE);
    }
}