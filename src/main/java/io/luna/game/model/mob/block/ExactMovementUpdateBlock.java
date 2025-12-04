package io.luna.game.model.mob.block;

import io.luna.game.model.Position;
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
    public void encodeForPlayer(ByteMessage msg, UpdateBlockData data) {
        Position lastRegion = data.move.getLastRegion();
        Position start = data.move.getStartPosition();
        Position end = data.move.getEndPosition();

        msg.put(start.getLocalX(lastRegion), ValueType.ADD);
        msg.put(start.getLocalY(lastRegion), ValueType.NEGATE);
        msg.put(end.getLocalX(lastRegion), ValueType.SUBTRACT);
        msg.put(end.getLocalY(lastRegion));
        msg.putShort(data.move.getDurationStart());
        msg.putShort(data.move.getDurationEnd(), ValueType.ADD);
        msg.put(data.move.getDirection().toForcedMovementId());
    }

    @Override
    public int getPlayerMask() {
        return 0x100;
    }
}
