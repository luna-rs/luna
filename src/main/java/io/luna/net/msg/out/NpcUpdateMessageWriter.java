package io.luna.net.msg.out;

import io.luna.game.model.Direction;
import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.update.NpcAnimationUpdateBlock;
import io.luna.game.model.mobile.update.NpcFacePositionUpdateBlock;
import io.luna.game.model.mobile.update.NpcForceChatUpdateBlock;
import io.luna.game.model.mobile.update.NpcGraphicUpdateBlock;
import io.luna.game.model.mobile.update.NpcInteractionUpdateBlock;
import io.luna.game.model.mobile.update.NpcPrimaryHitUpdateBlock;
import io.luna.game.model.mobile.update.NpcSecondaryHitUpdateBlock;
import io.luna.game.model.mobile.update.NpcTransformUpdateBlock;
import io.luna.game.model.mobile.update.UpdateBlock;
import io.luna.game.model.mobile.update.UpdateBlockSet;
import io.luna.game.model.mobile.update.UpdateState;
import io.luna.game.model.region.RegionManager;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.OutboundMessageWriter;

import java.util.Iterator;

/**
 * An {@link OutboundMessageWriter} implementation that sends an update message containing the underlying {@link Player} and
 * {@link Npc}s surrounding them.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcUpdateMessageWriter extends OutboundMessageWriter {

    /**
     * The {@link UpdateBlockSet} that will manage all of the {@link UpdateBlock}s.
     */
    private final UpdateBlockSet<Npc> blockSet = new UpdateBlockSet<>();

    {
        blockSet.add(new NpcAnimationUpdateBlock());
        blockSet.add(new NpcSecondaryHitUpdateBlock());
        blockSet.add(new NpcGraphicUpdateBlock());
        blockSet.add(new NpcInteractionUpdateBlock());
        blockSet.add(new NpcForceChatUpdateBlock());
        blockSet.add(new NpcPrimaryHitUpdateBlock());
        blockSet.add(new NpcTransformUpdateBlock());
        blockSet.add(new NpcFacePositionUpdateBlock());
    }

    @Override
    public ByteMessage encode(Player player) {
        ByteMessage msg = ByteMessage.message(65, MessageType.VARIABLE_SHORT);
        ByteMessage blockMsg = ByteMessage.message();

        try {
            msg.startBitAccess();
            msg.putBits(8, player.getLocalNpcs().size());

            Iterator<Npc> $it = player.getLocalNpcs().iterator();
            while ($it.hasNext()) {
                Npc other = $it.next();

                if (other.isViewable(player)) {
                    handleMovement(other, msg);
                    blockSet.encodeUpdateBlocks(other, blockMsg, UpdateState.UPDATE_LOCAL);
                } else {
                    msg.putBit(true);
                    msg.putBits(2, 3);
                    $it.remove();
                }
            }

            RegionManager regions = player.getWorld().getRegions();
            int npcsAdded = 0;

            for (Npc other : regions.getSurroundingNpcs(player)) {
                if (npcsAdded == 15 || player.getLocalNpcs().size() >= 255) {
                    break;
                }
                if (other.isViewable(player) && player.getLocalNpcs().add(other)) {
                    addNpc(player, other, msg);
                    blockSet.encodeUpdateBlocks(other, blockMsg, UpdateState.ADD_LOCAL);
                }
                npcsAdded++;
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
            throw e;
        } finally {
            blockMsg.release();
        }
        return msg;
    }

    /**
     * Adds {@code addNpc} in the view of {@code player}.
     *
     * @param msg The main update message.
     * @param player The {@link Player} this update message is being sent for.
     * @param addNpc The {@link Npc} being added.
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
     *
     * @param npc The {@link Player} to handle running and walking for.
     * @param msg The main update message.
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
