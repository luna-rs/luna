package io.luna.game.model.mob.block;

import io.luna.game.model.mob.ForcedMovement;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteTransform;

/**
 * An {@link UpdateBlock} implementation for the {@code FORCED_MOVEMENT} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ForcedMovementUpdateBlock extends UpdateBlock {

    /**
     * Creates a new {@link ForcedMovementUpdateBlock}.
     */
    public ForcedMovementUpdateBlock() {
        super(UpdateFlag.FORCED_MOVEMENT);
    }

    @Override
    public void encodeForPlayer(Player player, ByteMessage msg) {
        ForcedMovement movement = unwrap(player.getForcedMovement());
        msg.put(movement.getStartPosition().getX(), ByteTransform.A);
        msg.put(movement.getStartPosition().getY(), ByteTransform.C);
        msg.put(movement.getEndPosition().getX(), ByteTransform.S);
        msg.put(movement.getEndPosition().getY());
        msg.putShort(movement.getDurationX());
        msg.putShort(movement.getDurationY(), ByteTransform.A);
        msg.put(movement.getDirection().getId());
    }

    @Override
    public int getPlayerMask() {
        return 256;
    }
}
