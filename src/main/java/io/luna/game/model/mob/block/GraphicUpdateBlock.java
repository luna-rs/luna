package io.luna.game.model.mob.block;

import io.luna.game.model.mob.Graphic;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;

/**
 * An {@link UpdateBlock} implementation for the {@code GRAPHIC} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class GraphicUpdateBlock extends UpdateBlock {

    /**
     * Creates a new {@link GraphicUpdateBlock}.
     */
    public GraphicUpdateBlock() {
        super(UpdateFlag.GRAPHIC);
    }

    @Override
    public void encodeForPlayer(Player player, ByteMessage msg) {
        Graphic graphic = unwrap(player.getGraphic());
        msg.putShort(graphic.getId(), ByteOrder.LITTLE);
        msg.putInt(graphic.getHeight() << 16 | graphic.getDelay() & 0xFFFF);
    }

    @Override
    public void encodeForNpc(Npc npc, ByteMessage msg) {
        Graphic graphic = unwrap(npc.getGraphic());
        msg.putShort(graphic.getId());
        msg.putInt(graphic.getHeight() << 16 | graphic.getDelay() & 0xFFFF);
    }

    @Override
    public int getPlayerMask() {
        return 256;
    }

    @Override
    public int getNpcMask() {
        return 128;
    }
}