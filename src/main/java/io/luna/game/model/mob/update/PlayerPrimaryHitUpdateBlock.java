package io.luna.game.model.mob.update;

import io.luna.game.model.mob.Hit;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.update.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteTransform;

/**
 * A {@link PlayerUpdateBlock} implementation for the {@code PRIMARY_HIT} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerPrimaryHitUpdateBlock extends PlayerUpdateBlock {

    /**
     * Creates a new {@link PlayerPrimaryHitUpdateBlock}.
     */
    public PlayerPrimaryHitUpdateBlock() {
        super(0x20, UpdateFlag.PRIMARY_HIT);
    }

    @Override
    public void write(Player mob, ByteMessage msg) {
        Hit hit = mob.getPrimaryHit().get();
        Skill hitpoints = mob.skill(Skill.HITPOINTS);

        msg.put(hit.getDamage());
        msg.put(hit.getType().getOpcode(), ByteTransform.A);
        msg.put(hitpoints.getLevel(), ByteTransform.C);
        msg.put(hitpoints.getStaticLevel());
    }
}
