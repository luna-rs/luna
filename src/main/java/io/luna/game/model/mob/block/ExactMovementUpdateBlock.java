package io.luna.game.model.mob.block;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;

/**
 * An {@link UpdateBlock} implementation for the {@code EXACT_MOVEMENT} update block.
 *
 * @author lare96
 */
public final class ExactMovementUpdateBlock extends UpdateBlock {

    /**
     * Creates a new {@link ExactMovementUpdateBlock}.
     */
    public ExactMovementUpdateBlock() {
        super(UpdateFlag.EXACT_MOVEMENT);
    }

    @Override
    public void encodeForPlayer(Player player, ByteMessage msg) {
        Position lastRegion = player.getLastRegion();
        ExactMovement movement = unwrap(player.getExactMovement());
        msg.put(movement.getStartPosition().getLocalX(lastRegion), ValueType.ADD);
        msg.put(movement.getStartPosition().getLocalY(lastRegion), ValueType.NEGATE);
        msg.put(movement.getEndPosition().getLocalX(lastRegion), ValueType.SUBTRACT);
        msg.put(movement.getEndPosition().getLocalY(lastRegion));
        msg.putShort(movement.getDurationStart());
        msg.putShort(movement.getDurationEnd(), ValueType.ADD);
        msg.put(movement.getDirection().getId());
    }

    @Override
    public int getPlayerMask() {
        return 0x100;
    }
}
