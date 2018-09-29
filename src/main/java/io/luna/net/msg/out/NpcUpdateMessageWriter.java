package io.luna.net.msg.out;

import io.luna.game.model.Direction;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.NpcUpdateBlockSet;
import io.luna.game.model.mob.block.UpdateBlockSet;
import io.luna.game.model.mob.block.UpdateState;
import io.luna.game.model.region.RegionManager;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.GameMessageWriter;

import java.util.Iterator;

/**
 * A {@link GameMessageWriter} implementation that sends an NPC update message.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcUpdateMessageWriter extends GameMessageWriter {

    /**
     * The NPC update block set.
     */
    private final UpdateBlockSet<Npc> blockSet = new NpcUpdateBlockSet();

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(65, MessageType.VAR_SHORT);
        ByteMessage blockMsg = ByteMessage.message();

        try {
            msg.startBitAccess();
            msg.putBits(8, player.getLocalNpcs().size());

            Iterator<Npc> iterator = player.getLocalNpcs().iterator();
            while (iterator.hasNext()) {
                Npc other = iterator.next();

                if (other.isViewable(player)) {
                    handleMovement(other, msg);
                    blockSet.encode(other, blockMsg, UpdateState.UPDATE_LOCAL);
                } else {
                    msg.putBit(true);
                    msg.putBits(2, 3);
                    iterator.remove();
                }
            }

            RegionManager regions = player.getWorld().getRegions();
            int npcsAdded = 0;

            for (Npc other : regions.getViewableNpcs(player)) {
                if (npcsAdded == 15 || player.getLocalNpcs().size() >= 255) {
                    break;
                }
                if (other.isViewable(player) && player.getLocalNpcs().add(other)) {
                    addNpc(player, other, msg);
                    blockSet.encode(other, blockMsg, UpdateState.ADD_LOCAL);
                    npcsAdded++;
                }
            }

            if (blockMsg.getBuffer().writerIndex() > 0) {
                msg.putBits(14, 16383);
                msg.endBitAccess();
                msg.putBytes(blockMsg);
            } else {
                msg.endBitAccess();
            }
        } catch (Exception e) {
            msg.release();
            throw new RuntimeException(e);
        } finally {
            blockMsg.release();
        }
        return msg;
    }

    /**
     * Adds {@code addNpc} in the view of {@code player}.
     */
    private void addNpc(Player player, Npc addNpc, ByteMessage msg) {
        boolean updateRequired = !addNpc.getUpdateFlags().isEmpty();

        int deltaX = addNpc.getPosition().getX() - player.getPosition().getX();
        int deltaY = addNpc.getPosition().getY() - player.getPosition().getY();

        msg.putBits(14, addNpc.getIndex());
        msg.putBits(5, deltaY);
        msg.putBits(5, deltaX);
        msg.putBit(updateRequired);
        msg.putBits(12, addNpc.getId());
        msg.putBit(true);
    }

    /**
     * Handles walking movement for {@code npc}.
     */
    private void handleMovement(Npc npc, ByteMessage msg) {
        boolean updateRequired = !npc.getUpdateFlags().isEmpty();

        if (npc.getWalkingDirection() == Direction.NONE) {
            if (updateRequired) {
                msg.putBit(true);
                msg.putBits(2, 0);
            } else {
                msg.putBit(false);
            }
        } else {
            msg.putBit(true);
            msg.putBits(2, 1);
            msg.putBits(3, npc.getWalkingDirection().getId());
            msg.putBit(updateRequired);
        }
    }
}
