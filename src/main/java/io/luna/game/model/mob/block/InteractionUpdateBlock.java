package io.luna.game.model.mob.block;

import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;

/**
 * An {@link UpdateBlock} implementation for the {@code INTERACTION} update block.
 *
 * @author lare96
 */
public final class InteractionUpdateBlock extends UpdateBlock {

    /**
     * Creates a new {@link InteractionUpdateBlock}.
     */
    public InteractionUpdateBlock() {
        super(UpdateFlag.INTERACTION);
    }

    @Override
    public void encodeForPlayer(Player player, ByteMessage msg) {
        int index = unwrap(player.getInteractionIndex());
        msg.putShort(index, ValueType.ADD);
    }

    @Override
    public void encodeForNpc(Npc npc, ByteMessage msg) {
        int index = unwrap(npc.getInteractionIndex());
        msg.putShort(index, ByteOrder.LITTLE);
    }

    @Override
    public int getPlayerMask() {
        return 0x1;
    }

    @Override
    public int getNpcMask() {
        return 0x40;
    }
}
