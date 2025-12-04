package io.luna.game.model.mob.block;

import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;

/**
 * An {@link UpdateBlock} implementation for the {@code GRAPHIC} update block.
 *
 * @author lare96
 */
public final class GraphicUpdateBlock extends UpdateBlock {

    /**
     * Creates a new {@link GraphicUpdateBlock}.
     */
    public GraphicUpdateBlock() {
        super(UpdateFlag.GRAPHIC);
    }

    @Override
    public void encodeForPlayer(ByteMessage msg, UpdateBlockData data) {
        msg.putShort(data.graphic.getId(), ValueType.ADD);
        msg.putInt(data.graphic.getHeight() << 16 | data.graphic.getDelay() & 0xFFFF, ByteOrder.MIDDLE);
    }

    @Override
    public void encodeForNpc(ByteMessage msg, UpdateBlockData data) {
        msg.putShort(data.graphic.getId());
        msg.putInt(data.graphic.getHeight() << 16 | data.graphic.getDelay() & 0xFFFF);
    }

    @Override
    public int getPlayerMask() {
        return 0x200;
    }

    @Override
    public int getNpcMask() {
        return 0x4;
    }
}