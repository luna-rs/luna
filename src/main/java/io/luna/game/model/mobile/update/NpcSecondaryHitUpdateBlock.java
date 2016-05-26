package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Hit;
import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteTransform;

/**
 * An {@link NpcUpdateBlock} implementation that handles the {@code SECONDARY_HIT} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcSecondaryHitUpdateBlock extends NpcUpdateBlock {

    /**
     * Creates a new {@link NpcSecondaryHitUpdateBlock}.
     */
    public NpcSecondaryHitUpdateBlock() {
        super(8, UpdateFlag.SECONDARY_HIT);
    }

    @Override
    public void write(Npc mob, ByteMessage msg) {
        Hit hit = mob.getSecondaryHit();

        msg.put(hit.getDamage(), ByteTransform.A);
        msg.put(hit.getType().getOpcode(), ByteTransform.C);
        msg.put(mob.getCurrentHp(), ByteTransform.A);
        msg.put(mob.getCombatDefinition().getHitpoints());
    }
}
