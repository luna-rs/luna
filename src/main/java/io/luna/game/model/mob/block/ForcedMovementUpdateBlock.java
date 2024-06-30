package io.luna.game.model.mob.block;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;

/**
 * An {@link UpdateBlock} implementation for the {@code FORCED_MOVEMENT} update block.
 *
 * @author lare96
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
        msg.put(movement.getStartPosition().getX(), ValueType.ADD);
        msg.put(movement.getStartPosition().getY(), ValueType.NEGATE);
        msg.put(movement.getEndPosition().getX(), ValueType.SUBTRACT);
        msg.put(movement.getEndPosition().getY());
        msg.putShort(movement.getDurationX());
        msg.putShort(movement.getDurationY(), ValueType.ADD);
        msg.put(movement.getDirection().getId());
    }

    @Override
    public int getPlayerMask() {
        return 0x100;
    }
}
