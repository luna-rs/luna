package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Hit;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.Skill;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteTransform;

/**
 * An {@link PlayerUpdateBlock} implementation that handles the secondary {@link Hit} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerSecondaryHitUpdateBlock extends PlayerUpdateBlock {

    /**
     * Creates a new {@link PlayerSecondaryHitUpdateBlock}.
     */
    public PlayerSecondaryHitUpdateBlock() {
        super(0x200, UpdateFlag.SECONDARY_HIT);
    }

    @Override
    public void write(Player mob, ByteMessage msg) {
        Hit hit = mob.getSecondaryHit();
        Skill hitpoints = mob.skill(Skill.HITPOINTS);

        msg.put(hit.getDamage());
        msg.put(hit.getType().getOpcode(), ByteTransform.S);
        msg.put(hitpoints.getLevel());
        msg.put(hitpoints.getStaticLevel(), ByteTransform.C);
    }
}
