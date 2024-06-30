package io.luna.game.model.mob.block;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;

/**
 * An {@link UpdateBlock} implementation for the {@code FACE_POSITION} update block.
 *
 * @author lare96
 */
public final class FacePositionUpdateBlock extends UpdateBlock {

    /**
     * Creates a new {@link UpdateBlock}.
     */
    public FacePositionUpdateBlock() {
        super(UpdateFlag.FACE_POSITION);
    }

    @Override
    public void encodeForPlayer(Player player, ByteMessage msg) {
        Position position = unwrap(player.getFacePosition());
        msg.putShort(position.getX() * 2 + 1);
        msg.putShort(position.getY() * 2 + 1);
    }

    @Override
    public void encodeForNpc(Npc npc, ByteMessage msg) {
        Position position = unwrap(npc.getFacePosition());
        msg.putShort(position.getX() * 2 + 1, ByteOrder.LITTLE, ValueType.ADD);
        msg.putShort(position.getY() * 2 + 1, ByteOrder.LITTLE);
    }

    @Override
    public int getPlayerMask() {
        return 0x2;
    }

    @Override
    public int getNpcMask() {
        return 0x8;
    }
}
