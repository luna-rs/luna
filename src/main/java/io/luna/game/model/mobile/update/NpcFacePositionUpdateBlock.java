package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;

/**
 * An {@link NpcUpdateBlock} implementation that handles the {@code FACE_POSITION} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcFacePositionUpdateBlock extends NpcUpdateBlock {

    /**
     * Creates a new {@link NpcFacePositionUpdateBlock}.
     */
    public NpcFacePositionUpdateBlock() {
        super(4, UpdateFlag.FACE_POSITION);
    }

    @Override
    public void write(Npc mob, ByteMessage msg) {
        msg.putShort(mob.getFacePosition().getX(), ByteOrder.LITTLE);
        msg.putShort(mob.getFacePosition().getY(), ByteOrder.LITTLE);
    }
}
