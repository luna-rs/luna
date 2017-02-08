package io.luna.game.model.mobile.update;

import io.luna.game.model.Position;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.update.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;

/**
 * A {@link PlayerUpdateBlock} implementation for the {@code FACE_POSITION} update block.
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
        Position position = mob.getFacePosition().get();
        msg.putShort((position.getX() + 1) << 1, ByteTransform.A, ByteOrder.LITTLE);
        msg.putShort((position.getY() + 1) << 1, ByteOrder.LITTLE);
    }
}
