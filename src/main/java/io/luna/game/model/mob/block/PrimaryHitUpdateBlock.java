package io.luna.game.model.mob.block;

import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
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
    public void encodeForPlayer(Player player, ByteMessage msg, UpdateBlockData data) {
        msg.put(data.hit1.getDamage(), ValueType.SUBTRACT);
        msg.put(data.hit1.getType().getOpcode(), ValueType.NEGATE);
        msg.put(player.getHealth(), ValueType.SUBTRACT);
        msg.put(player.getTotalHealth());
    }

    @Override
    public void encodeForNpc(Npc npc, ByteMessage msg, UpdateBlockData data) {
        msg.put(data.hit1.getDamage(), ValueType.ADD);
        msg.put(data.hit1.getType().getOpcode(), ValueType.ADD);
        msg.put(npc.getHealth());
        msg.put(npc.getTotalHealth(), ValueType.SUBTRACT);
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