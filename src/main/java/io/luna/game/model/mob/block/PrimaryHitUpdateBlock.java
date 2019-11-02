package io.luna.game.model.mob.block;

import io.luna.game.model.mob.Hit;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;

/**
 * An {@link UpdateBlock} implementation for the {@code PRIMARY_HIT} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PrimaryHitUpdateBlock extends UpdateBlock {

    /**
     * Creates a new {@link PrimaryHitUpdateBlock}.
     */
    public PrimaryHitUpdateBlock() {
        super(UpdateFlag.PRIMARY_HIT);
    }


    @Override
    public void encodeForPlayer(Player player, ByteMessage msg) {
        Hit hit = unwrap(player.getPrimaryHit());
        msg.put(hit.getDamage());
        msg.put(hit.getType().getOpcode(), ValueType.ADD);
        msg.put(player.getHealth(), ValueType.NEGATE);
        msg.put(player.getTotalHealth());
    }

    @Override
    public void encodeForNpc(Npc npc, ByteMessage msg) {
        Hit hit = unwrap(npc.getPrimaryHit());
        msg.put(hit.getDamage(), ValueType.NEGATE);
        msg.put(hit.getType().getOpcode(), ValueType.SUBTRACT);
        msg.put(npc.getHealth(), ValueType.SUBTRACT);
        msg.put(npc.getTotalHealth(), ValueType.NEGATE);
    }

    @Override
    public int getPlayerMask() {
        return 32;
    }

    @Override
    public int getNpcMask() {
        return 64;
    }
}