package io.luna.game.model.mob.update;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.update.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;

/**
 * An {@link NpcUpdateBlock} implementation for the {@code FACE_POSITION} update block.
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
        Position position = mob.getFacePosition().get();
        msg.putShort(position.getX(), ByteOrder.LITTLE);
        msg.putShort(position.getY(), ByteOrder.LITTLE);
    }
}
