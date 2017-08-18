package io.luna.game.model.mob.update;

import io.luna.game.model.mob.Hit;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.update.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteTransform;

/**
 * An {@link NpcUpdateBlock} implementation for the {@code PRIMARY_HIT} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcPrimaryHitUpdateBlock extends NpcUpdateBlock {

    /**
     * Creates a new {@link NpcPrimaryHitUpdateBlock}.
     */
    public NpcPrimaryHitUpdateBlock() {
        super(0x40, UpdateFlag.PRIMARY_HIT);
    }

    @Override
    public void write(Npc mob, ByteMessage msg) {
        Hit hit = mob.getPrimaryHit().get();
        msg.put(hit.getDamage(), ByteTransform.C);
        msg.put(hit.getType().getOpcode(), ByteTransform.S);
        msg.put(mob.getCurrentHp(), ByteTransform.S);
        msg.put(mob.getCombatDefinition().getHitpoints(), ByteTransform.C);
    }
}