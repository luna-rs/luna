package io.luna.net.msg.out;

import io.luna.game.model.Direction;
import io.luna.game.model.EntityState;
import io.luna.game.model.Position;
import io.luna.game.model.chunk.ChunkManager;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.AbstractUpdateBlockSet;
import io.luna.game.model.mob.block.PlayerUpdateBlockSet;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.game.model.mob.block.UpdateState;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.GameMessageWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;

/**
 * A {@link GameMessageWriter} implementation that sends a player update message.
 *
 * @author lare96
 */
public final class PlayerUpdateMessageWriter extends GameMessageWriter {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The player update block set.
     */
    private final AbstractUpdateBlockSet<Player> blockSet = new PlayerUpdateBlockSet();


    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(90, MessageType.VAR_SHORT);
        ByteMessage blockMsg = ByteMessage.raw();

        try {
            msg.startBitAccess();
            handleMovement(player, msg, true);
            blockSet.encode(player, blockMsg, UpdateState.UPDATE_SELF);

            msg.putBits(8, player.getLocalPlayers().size());
            Iterator<Player> iterator = player.getLocalPlayers().iterator();
            while (iterator.hasNext()) {
                Player other = iterator.next();
                if (other.isViewableFrom(player) && other.getState() == EntityState.ACTIVE && !other.isTeleporting()) {
                    handleMovement(other, msg, false);
                    blockSet.encode(other, blockMsg, UpdateState.UPDATE_LOCAL);
                } else {
                    msg.putBit(true);
                    msg.putBits(2, 3);
                    iterator.remove();
                }
            }

            ChunkManager chunks = player.getWorld().getChunks();
            int playersAdded = 0;
            for (Player other : chunks.getUpdatePlayers(player)) {
                if (playersAdded == 15 || player.getLocalPlayers().size() >= 255) {
                    break;
                }
                if (player.equals(other) || other.getState() != EntityState.ACTIVE) {
                    continue;
                }

                if (other.getPosition().isViewable(player.getPosition()) && player.getLocalPlayers().add(other)) {
                    playersAdded++;
                    addPlayer(msg, player, other);
                    blockSet.encode(other, blockMsg, UpdateState.ADD_LOCAL);
                }
            }

            if (blockMsg.getBuffer().readableBytes() > 0) {
                msg.putBits(11, 2047);
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
     * Adds {@code addPlayer} in the view of {@code player}.
     */
    private void addPlayer(ByteMessage msg, Player player, Player addPlayer) {
        int deltaX = addPlayer.getPosition().getX() - player.getPosition().getX();
        int deltaY = addPlayer.getPosition().getY() - player.getPosition().getY();

        msg.putBits(11, addPlayer.getIndex());
        msg.putBits(5, deltaX);
        msg.putBit(true);
        msg.putBit(true);
        msg.putBits(5, deltaY);
    }

    /**
     * Handles running, walking, and teleportation movement for {@code player}.
     */
    private void handleMovement(Player player, ByteMessage msg, boolean updateTeleport) {
        boolean needsUpdate = !player.getFlags().isEmpty();
        if(updateTeleport && player.getFlags().size() == 1 && player.getFlags().get(UpdateFlag.CHAT)) {
            // Needs update needs to be false if were only forcing chat
            needsUpdate = false;
        }
        if (player.isTeleporting() && updateTeleport) {
            Position position = player.getPosition();

            msg.putBit(true);
            msg.putBits(2, 3);
            msg.putBit(true);
            msg.putBits(2, position.getZ());
            msg.putBits(7, position.getLocalY(player.getLastRegion()));
            msg.putBits(7, position.getLocalX(player.getLastRegion()));
            msg.putBit(needsUpdate);
            return;
        }

        Direction walkingDirection = player.getWalkingDirection();
        Direction runningDirection = player.getRunningDirection();

        if (walkingDirection != Direction.NONE) {
            msg.putBit(true);
            if (runningDirection != Direction.NONE) {
                msg.putBits(2, 2);
                msg.putBits(3, walkingDirection.getId());
                msg.putBits(3, runningDirection.getId());
                msg.putBit(needsUpdate);
            } else {
                msg.putBits(2, 1);
                msg.putBits(3, walkingDirection.getId());
                msg.putBit(needsUpdate);
            }
        } else {
            if (needsUpdate) {
                msg.putBit(true);
                msg.putBits(2, 0);
            } else {
                msg.putBit(false);
            }
        }
    }
}
