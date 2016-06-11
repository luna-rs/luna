package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.ForcedMovement;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteTransform;

/**
 * An {@link PlayerUpdateBlock} implementation that handles the {@link ForcedMovement} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerForceMovementUpdateBlock extends PlayerUpdateBlock {

    /**
     * Creates a new {@link PlayerForceMovementUpdateBlock}.
     */
    public PlayerForceMovementUpdateBlock() {
        super(0x100, UpdateFlag.FORCE_MOVEMENT);
    }

    @Override
    public void write(Player mob, ByteMessage msg) {
        ForcedMovement movement = mob.getForceMovement();

        msg.put(movement.getStartPosition().getX(), ByteTransform.A);
        msg.put(movement.getStartPosition().getY(), ByteTransform.C);

        msg.put(movement.getEndPosition().getX(), ByteTransform.S);
        msg.put(movement.getEndPosition().getY());

        msg.putShort(movement.getDurationX());
        msg.putShort(movement.getDurationY(), ByteTransform.A);

        msg.put(movement.getDirection().getId());
    }
}
