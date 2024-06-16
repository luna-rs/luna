package io.luna.net.msg.out;

import io.luna.game.model.Direction;
import io.luna.game.model.EntityState;
import io.luna.game.model.chunk.ChunkManager;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.AbstractUpdateBlockSet;
import io.luna.game.model.mob.block.NpcUpdateBlockSet;
import io.luna.game.model.mob.block.UpdateState;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.GameMessageWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;

/**
 * A {@link GameMessageWriter} implementation that sends an NPC update message.
 *
 * @author lare96
 */
public final class NpcUpdateMessageWriter extends GameMessageWriter {

    /**
     * The logger.
     */
    private final Logger logger = LogManager.getLogger();

    /**
     * The NPC update block set.
     */
    private final AbstractUpdateBlockSet<Npc> blockSet = new NpcUpdateBlockSet();

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(71, MessageType.VAR_SHORT);
        ByteMessage blockMsg = ByteMessage.raw();

        try {
            msg.startBitAccess();
            msg.putBits(8, player.getLocalNpcs().size());

            Iterator<Npc> iterator = player.getLocalNpcs().iterator();
            while (iterator.hasNext()) {
                Npc other = iterator.next();
                if (other.isViewableFrom(player) &&
                        other.getState() == EntityState.ACTIVE) {
                    handleMovement(other, msg);
                    blockSet.encode(other, blockMsg, UpdateState.UPDATE_LOCAL);
                } else {
                    msg.putBit(true);
                    msg.putBits(2, 3);
                    iterator.remove();
                }
            }

            ChunkManager chunks = player.getWorld().getChunks();
            int npcsAdded = 0;

            for (Npc other : chunks.getUpdateNpcs(player)) {
                if (npcsAdded == 15 || player.getLocalNpcs().size() >= 255) {
                    break;
                }
                if (other.isViewableFrom(player) &&
                        other.getState() == EntityState.ACTIVE &&
                        player.getLocalNpcs().add(other)) {
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
            logger.catching(e);
        } finally {
            blockMsg.release();
        }
        return msg;
    }

    /**
     * Adds {@code addNpc} in the view of {@code player}.
     */
    private void addNpc(Player player, Npc addNpc, ByteMessage msg) {
        boolean updateRequired = !addNpc.getFlags().isEmpty();

        int deltaX = addNpc.getPosition().getX() - player.getPosition().getX();
        int deltaY = addNpc.getPosition().getY() - player.getPosition().getY();

        msg.putBits(14, addNpc.getIndex());
        msg.putBit(updateRequired);
        msg.putBits(5, deltaY);
        msg.putBits(5, deltaX);
        msg.putBit(true);
        msg.putBits(13, addNpc.getId());
    }

    /**
     * Handles walking movement for {@code npc}.
     */
    private void handleMovement(Npc npc, ByteMessage msg) {
        boolean updateRequired = !npc.getFlags().isEmpty();
        Direction walkingDirection = npc.getWalkingDirection();
        Direction runningDirection = npc.getRunningDirection();

        if (runningDirection != Direction.NONE) {
            msg.putBit(true);
            msg.putBits(2, 2);
            msg.putBits(3, walkingDirection.getId());
            msg.putBits(3, runningDirection.getId());
            msg.putBit(updateRequired);
        } else if (walkingDirection != Direction.NONE) {
            msg.putBit(true);
            msg.putBits(2, 1);
            msg.putBits(3, walkingDirection.getId());
            msg.putBit(updateRequired);
        } else {
            if (updateRequired) {
                msg.putBit(true);
                msg.putBits(2, 0);
            } else {
                msg.putBit(false);
            }
        }
    }
}
