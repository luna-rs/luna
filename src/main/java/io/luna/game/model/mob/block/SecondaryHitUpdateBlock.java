package io.luna.game.model.mob.block;

import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
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
    public void encodeForPlayer(Player player, ByteMessage msg) {
        Hit hit = unwrap(player.getSecondaryHit());
        msg.put(hit.getDamage(), ValueType.ADD);
        msg.put(hit.getType().getOpcode(), ValueType.SUBTRACT);
        msg.put(player.getHealth(), ValueType.NEGATE);
        msg.put(player.getTotalHealth());
    }

    @Override
    public void encodeForNpc(Npc npc, ByteMessage msg) {
        Hit hit = unwrap(npc.getSecondaryHit());
        msg.put(hit.getDamage(), ValueType.ADD);
        msg.put(hit.getType().getOpcode(), ValueType.NEGATE);
        msg.put(npc.getHealth(), ValueType.ADD);
        msg.put(npc.getTotalHealth());
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
