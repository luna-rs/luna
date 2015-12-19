package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;

/**
 * An {@link PlayerUpdateBlock} implementation that handles the {@code FACE_POSITION} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerFacePositionUpdateBlock extends PlayerUpdateBlock {

    /**
     * Creates a new {@link PlayerFacePositionUpdateBlock}.
     */
    public PlayerFacePositionUpdateBlock() {
        super(2, UpdateFlag.FACE_POSITION);
    }

    @Override
    public void write(Player mob, ByteMessage msg) {
        msg.putShort(2 * mob.getFacePosition().getX() + 1, ByteTransform.A, ByteOrder.LITTLE);
        msg.putShort(2 * mob.getFacePosition().getY() + 1, ByteOrder.LITTLE);
    }
}
