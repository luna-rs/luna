package io.luna.game.model.mob.update;

import io.luna.game.model.mob.Animation;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.update.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;

/**
 * A {@link PlayerUpdateBlock} implementation for the {@code ANIMATION} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerAnimationUpdateBlock extends PlayerUpdateBlock {

    /**
     * Creates a new {@link PlayerAnimationUpdateBlock}.
     */
    public PlayerAnimationUpdateBlock() {
        super(8, UpdateFlag.ANIMATION);
    }

    @Override
    public void write(Player mob, ByteMessage msg) {
        Animation animation = mob.getAnimation().get();
        msg.putShort(animation.getId(), ByteOrder.LITTLE);
        msg.put(animation.getDelay(), ByteTransform.C);
    }
}
