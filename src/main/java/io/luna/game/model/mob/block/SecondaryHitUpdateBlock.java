package io.luna.game.model.mob.block;

import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;

/**
 * An {@link UpdateBlock} implementation for the {@code SECONDARY_HIT} update block.
 *
 * @author lare96
 */
public final class SecondaryHitUpdateBlock extends UpdateBlock {

    /**
     * Creates a new {@link SecondaryHitUpdateBlock}.
     */
    public SecondaryHitUpdateBlock() {
        super(UpdateFlag.SECONDARY_HIT);
    }

    @Override
    public void encodeForPlayer(ByteMessage msg, UpdateBlockData data) {
        msg.put(data.hit2.getDamage(), ValueType.ADD);
        msg.put(data.hit2.getType().getOpcode(), ValueType.SUBTRACT);
        msg.put(data.hit2.getCurrentHealth(), ValueType.NEGATE);
        msg.put(data.hit2.getTotalHealth());
    }

    @Override
    public void encodeForNpc(ByteMessage msg, UpdateBlockData data) {
        msg.put(data.hit2.getDamage(), ValueType.ADD);
        msg.put(data.hit2.getType().getOpcode(), ValueType.NEGATE);
        msg.put(data.hit2.getCurrentHealth(), ValueType.ADD);
        msg.put(data.hit2.getTotalHealth());
    }

    @Override
    public int getPlayerMask() {
        return 0x400;
    }

    @Override
    public int getNpcMask() {
        return 0x10;
    }
}
