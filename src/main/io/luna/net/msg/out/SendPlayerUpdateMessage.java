package io.luna.net.msg.out;

import com.google.common.base.Throwables;
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
import io.luna.game.model.region.RegionManager;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.OutboundGameMessage;
import io.luna.net.session.SessionState;

import java.util.Iterator;

/**
 * An {@link OutboundGameMessage} implementation that sends an update message containing the underlying {@link Player} and
 * other {@code Player}s surrounding them.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class SendPlayerUpdateMessage extends OutboundGameMessage {

    /**
     * The {@link UpdateBlockSet} that will manage all of the {@link UpdateBlock}s.
     */
    private final UpdateBlockSet blockSet = new UpdateBlockSet();

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
    public ByteMessage writeMessage(Player player) {
        ByteMessage msg = ByteMessage.create(16384);
        ByteMessage blockMsg = ByteMessage.create(8192);

        try {
            msg.varShortMessage(81);
            msg.startBitAccess();

            handleMovement(player, msg);

            blockSet.handleUpdateBlocks(player, player, blockMsg, false, true);

            msg.putBits(8, player.getLocalPlayers().size());
            Iterator<Player> $it = player.getLocalPlayers().iterator();
            while ($it.hasNext()) {
                Player other = $it.next();

                if (other.getPosition().isViewable(player.getPosition()) && other.getState() == EntityState.ACTIVE && !other
                    .isRegionChanged()) {

                    handleMovement(other, msg);
                    blockSet.handleUpdateBlocks(other, player, blockMsg, false, false);
                } else {
                    msg.putBit(true);
                    msg.putBits(2, 3);
                    $it.remove();
                }
            }

            RegionManager regions = player.getWorld().getRegions();
            int playersAdded = 0;

            for (Player other : regions.getPriorityPlayers(player)) {
                if (playersAdded == 15 || player.getLocalPlayers().size() >= 255) {
                    break;
                }
                if (other.equals(player) || other.getSession().getState() != SessionState.LOGGED_IN) {
                    continue;
                }
                if (other.getPosition().isViewable(player.getPosition()) && player.getLocalPlayers().add(other)) {
                    playersAdded++;
                    addPlayer(msg, player, other);
                    blockSet.handleUpdateBlocks(other, player, blockMsg, true, false);
                }
            }

            if (blockMsg.getBuffer().writerIndex() > 0) {
                msg.putBits(11, 2047);
                msg.endBitAccess();
                msg.putBytes(blockMsg.getBuffer());
            } else {
                msg.endBitAccess();
            }
            msg.endVarShortMessage();
        } catch (Exception e) {
            msg.release();
            throw Throwables.propagate(e);
        } finally {
            blockMsg.release();
        }
        return msg;
    }

    /**
     * Adds {@code addPlayer} in the view of {@code player}.
     *
     * @param out The main update message.
     * @param player The {@link Player} this update message is being sent for.
     * @param addPlayer The {@code Player} being added.
     */
    private void addPlayer(ByteMessage out, Player player, Player addPlayer) {
        out.putBits(11, addPlayer.getIndex());
        out.putBit(!addPlayer.getUpdateFlags().isEmpty());
        out.putBit(true);

        int deltaX = addPlayer.getPosition().getX() - player.getPosition().getX();
        int deltaY = addPlayer.getPosition().getY() - player.getPosition().getY();
        out.putBits(5, deltaY);
        out.putBits(5, deltaX);
    }

    /**
     * Handles running, walking, and teleportation movement for {@code player}.
     *
     * @param player The {@link Player} to handle running and walking for.
     * @param out The main update message.
     */
    private void handleMovement(Player player, ByteMessage out) {
        boolean needsUpdate = !player.getUpdateFlags().isEmpty();

        if (player.isTeleporting()) {
            Position position = player.getPosition();

            out.putBit(true);
            out.putBits(2, 3);
            out.putBits(2, position.getZ());
            out.putBit(!player.isRegionChanged());
            out.putBit(needsUpdate);

            out.putBits(7, position.getLocalY(player.getLastRegion()));
            out.putBits(7, position.getLocalX(player.getLastRegion()));
            return;
        }

        Direction walkingDirection = player.getWalkingDirection();
        Direction runningDirection = player.getRunningDirection();

        if (walkingDirection != Direction.NONE) {
            out.putBit(true);
            if (runningDirection != Direction.NONE) {
                out.putBits(2, 2);
                out.putBits(3, walkingDirection.getId());
                out.putBits(3, runningDirection.getId());
                out.putBit(needsUpdate);
            } else {
                out.putBits(2, 1);
                out.putBits(3, walkingDirection.getId());
                out.putBit(needsUpdate);
            }
        } else {
            if (needsUpdate) {
                out.putBit(true);
                out.putBits(2, 0);
            } else {
                out.putBit(false);
            }
        }
    }
}
