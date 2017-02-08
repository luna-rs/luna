package io.luna.net.msg.out;

import io.luna.game.model.Direction;
import io.luna.game.model.EntityState;
import io.luna.game.model.Position;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.update.UpdateBlockSet;
import io.luna.game.model.mobile.update.UpdateState;
import io.luna.game.model.region.RegionManager;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.MessageWriter;

import java.util.Iterator;

/**
 * A {@link MessageWriter} implementation that sends a player update message.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerUpdateMessageWriter extends MessageWriter {

    /**
     * The player update block set.
     */
    private final UpdateBlockSet<Player> blockSet = UpdateBlockSet.PLAYER_BLOCK_SET;

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(81, MessageType.VAR_SHORT);
        ByteMessage blockMsg = ByteMessage.message();

        try {
            msg.startBitAccess();

            handleMovement(player, msg);
            blockSet.encodeUpdateBlocks(player, blockMsg, UpdateState.UPDATE_SELF);

            msg.putBits(8, player.getLocalPlayers().size());
            Iterator<Player> iterator = player.getLocalPlayers().iterator();
            while (iterator.hasNext()) {
                Player other = iterator.next();

                if (other.isViewable(player) && other.getState() == EntityState.ACTIVE && !other.isRegionChanged()) {
                    handleMovement(other, msg);
                    blockSet.encodeUpdateBlocks(other, blockMsg, UpdateState.UPDATE_LOCAL);
                } else {
                    msg.putBit(true);
                    msg.putBits(2, 3);
                    iterator.remove();
                }
            }

            RegionManager regions = player.getWorld().getRegions();
            int playersAdded = 0;

            for (Player other : regions.getSurroundingPlayers(player)) {
                if (playersAdded == 15 || player.getLocalPlayers().size() >= 255) {
                    break;
                }
                if (player.equals(other) || other.getState() != EntityState.ACTIVE) {
                    continue;
                }
                if (other.getPosition().isViewable(player.getPosition()) && player.getLocalPlayers().add(other)) {
                    playersAdded++;
                    addPlayer(msg, player, other);
                    blockSet.encodeUpdateBlocks(other, blockMsg, UpdateState.ADD_LOCAL);
                }
            }

            if (blockMsg.getBuffer().writerIndex() > 0) {
                msg.putBits(11, 2047);
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
     * Adds {@code addPlayer} in the view of {@code player}.
     */
    private void addPlayer(ByteMessage msg, Player player, Player addPlayer) {
        msg.putBits(11, addPlayer.getIndex());
        msg.putBit(true);
        msg.putBit(true);

        int deltaX = addPlayer.getPosition().getX() - player.getPosition().getX();
        int deltaY = addPlayer.getPosition().getY() - player.getPosition().getY();
        msg.putBits(5, deltaY);
        msg.putBits(5, deltaX);
    }

    /**
     * Handles running, walking, and teleportation movement for {@code player}.
     */
    private void handleMovement(Player player, ByteMessage msg) {
        boolean needsUpdate = !player.getUpdateFlags().isEmpty();

        if (player.isTeleporting()) {
            Position position = player.getPosition();

            msg.putBit(true);
            msg.putBits(2, 3);
            msg.putBits(2, position.getZ());
            msg.putBit(!player.isRegionChanged());
            msg.putBit(needsUpdate);

            msg.putBits(7, position.getLocalY(player.getLastRegion()));
            msg.putBits(7, position.getLocalX(player.getLastRegion()));
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
