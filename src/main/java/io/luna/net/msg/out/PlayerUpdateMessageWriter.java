package io.luna.net.msg.out;

import io.luna.game.model.Direction;
import io.luna.game.model.EntityState;
import io.luna.game.model.Position;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.update.PlayerAnimationUpdateBlock;
import io.luna.game.model.mobile.update.PlayerAppearanceUpdateBlock;
import io.luna.game.model.mobile.update.PlayerChatUpdateBlock;
import io.luna.game.model.mobile.update.PlayerFacePositionUpdateBlock;
import io.luna.game.model.mobile.update.PlayerForceChatUpdateBlock;
import io.luna.game.model.mobile.update.PlayerForceMovementUpdateBlock;
import io.luna.game.model.mobile.update.PlayerGraphicUpdateBlock;
import io.luna.game.model.mobile.update.PlayerInteractionUpdateBlock;
import io.luna.game.model.mobile.update.PlayerPrimaryHitUpdateBlock;
import io.luna.game.model.mobile.update.PlayerSecondaryHitUpdateBlock;
import io.luna.game.model.mobile.update.UpdateBlock;
import io.luna.game.model.mobile.update.UpdateBlockSet;
import io.luna.game.model.mobile.update.UpdateState;
import io.luna.game.model.region.RegionManager;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.MessageWriter;

import java.util.Iterator;

/**
 * A {@link MessageWriter} implementation that sends an update message containing the underlying {@link Player} and other
 * {@code Player}s surrounding them.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerUpdateMessageWriter extends MessageWriter {

    /**
     * The {@link UpdateBlockSet} that will manage all of the {@link UpdateBlock}s.
     */
    private final UpdateBlockSet<Player> blockSet = new UpdateBlockSet<>();

    {
        blockSet.add(new PlayerGraphicUpdateBlock());
        blockSet.add(new PlayerAnimationUpdateBlock());
        blockSet.add(new PlayerForceChatUpdateBlock());
        blockSet.add(new PlayerChatUpdateBlock());
        blockSet.add(new PlayerForceMovementUpdateBlock());
        blockSet.add(new PlayerInteractionUpdateBlock());
        blockSet.add(new PlayerAppearanceUpdateBlock());
        blockSet.add(new PlayerFacePositionUpdateBlock());
        blockSet.add(new PlayerPrimaryHitUpdateBlock());
        blockSet.add(new PlayerSecondaryHitUpdateBlock());
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(81, MessageType.VARIABLE_SHORT);
        ByteMessage blockMsg = ByteMessage.message();

        try {
            msg.startBitAccess();

            handleMovement(player, msg);
            blockSet.encodeUpdateBlocks(player, blockMsg, UpdateState.UPDATE_SELF);

            msg.putBits(8, player.getLocalPlayers().size());
            Iterator<Player> $it = player.getLocalPlayers().iterator();
            while ($it.hasNext()) {
                Player other = $it.next();

                if (other.isViewable(player) && other.getState() == EntityState.ACTIVE && !other.isRegionChanged()) {
                    handleMovement(other, msg);
                    blockSet.encodeUpdateBlocks(other, blockMsg, UpdateState.UPDATE_LOCAL);
                } else {
                    msg.putBit(true);
                    msg.putBits(2, 3);
                    $it.remove();
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
     *
     * @param msg The main update message.
     * @param player The {@link Player} this update message is being sent for.
     * @param addPlayer The {@code Player} being added.
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
     *
     * @param player The {@link Player} to handle running and walking for.
     * @param msg The main update message.
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
