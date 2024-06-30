package io.luna.game.model.mob.block;

import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;

/**
 * An {@link UpdateBlock} implementation for the {@code ANIMATION} update block.
 *
 * @author lare96
 */
public final class AnimationUpdateBlock extends UpdateBlock {

    /**
     * Creates a new {@link AnimationUpdateBlock}.
     */
    public AnimationUpdateBlock() {
        super(UpdateFlag.ANIMATION);
    }

    @Override
    public void encodeForPlayer(Player player, ByteMessage msg) {
        Animation animation = unwrap(player.getAnimation());
        msg.putShort(animation.getId());
        msg.put(animation.getDelay(), ValueType.ADD);
    }

    @Override
    public void encodeForNpc(Npc npc, ByteMessage msg) {
        Animation animation = unwrap(npc.getAnimation());
        msg.putShort(animation.getId());
        msg.put(animation.getDelay(), ValueType.SUBTRACT);
    }

    @Override
    public int getPlayerMask() {
        return 0x8;
    }

    @Override
    public int getNpcMask() {
        return 0x2;
    }
}
