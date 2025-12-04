package io.luna.game.model.mob.block;

import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;

/**
 * An {@link UpdateBlock} implementation for the {@code PRIMARY_HIT} update block.
 *
 * @author lare96
 */
public final class PrimaryHitUpdateBlock extends UpdateBlock {

    /**
     * Creates a new {@link PrimaryHitUpdateBlock}.
     */
    public PrimaryHitUpdateBlock() {
        super(UpdateFlag.PRIMARY_HIT);
    }

    @Override
    public void encodeForPlayer(ByteMessage msg, UpdateBlockData data) {
        msg.put(data.hit1.getDamage(), ValueType.SUBTRACT);
        msg.put(data.hit1.getType().getOpcode(), ValueType.NEGATE);
        msg.put(data.hit1.getCurrentHealth(), ValueType.SUBTRACT);
        msg.put(data.hit1.getTotalHealth());
    }

    @Override
    public void encodeForNpc(ByteMessage msg, UpdateBlockData data) {
        msg.put(data.hit1.getDamage(), ValueType.ADD);
        msg.put(data.hit1.getType().getOpcode(), ValueType.ADD);
        msg.put(data.hit1.getCurrentHealth());
        msg.put(data.hit1.getTotalHealth(), ValueType.SUBTRACT);
    }

    @Override
    public int getPlayerMask() {
        return 0x80;
    }

    @Override
    public int getNpcMask() {
        return 0x80;
    }
}