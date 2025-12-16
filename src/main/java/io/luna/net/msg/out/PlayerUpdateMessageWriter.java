package io.luna.net.msg.out;

import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.AbstractUpdateBlockSet;
import io.luna.game.model.mob.block.LocalMobRepository;
import io.luna.game.model.mob.block.PlayerUpdateBlockSet;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.game.model.mob.block.UpdateState;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;

/**
 * A {@link GameMessageWriter} implementation that encodes and sends player updates to a single {@link Player}.
 *
 * @author lare96
 */
public final class PlayerUpdateMessageWriter extends GameMessageWriter {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The update block set used to encode all pending player update flags.
     */
    private final AbstractUpdateBlockSet<Player> blockSet = new PlayerUpdateBlockSet();

    /**
     * The collection of players that are considered when populating the player's local view (typically all players
     * in nearby regions).
     */
    private final Collection<Player> localPlayers;

    /**
     * Creates a new {@link PlayerUpdateMessageWriter}.
     *
     * @param localPlayers The collection of players that are considered when populating the player's local view
     * (typically all players in nearby regions).
     */
    public PlayerUpdateMessageWriter(Collection<Player> localPlayers) {
        this.localPlayers = localPlayers;
    }

    /**
     * Encodes the player update packet for {@code player}.
     * <p>
     * The encoding process is:
     * <ol>
     *     <li>Begin bit access on the main message.</li>
     *     <li>Encode the movement and update blocks for {@code player}.</li>
     *     <li>Write the current local player count.</li>
     *     <li>Iterate over the player's {@link LocalMobRepository} of players:
     *         <ul>
     *             <li>If the other player is still viewable and not pending placement, encode movement and update blocks.</li>
     *             <li>If the other player is no longer viewable or pending placement, mark them for removal.</li>
     *         </ul>
     *     </li>
     *     <li>Iterate over {@link #localPlayers} to add new players to the local list (up to 25 at a time, and not
     *     exceeding 255 total local players).</li>
     *     <li>If any update blocks were encoded, write the end-of-list sentinel and append the block buffer to the
     *     main message.</li>
     *     <li>Restore byte access and return the finished message.</li>
     * </ol>
     * If an exception occurs during encoding, the message buffer is released and the error is logged.
     *
     * @param player The player that will receive the player update packet.
     * @param buffer The underlying {@link ByteBuf} allocated for this encoding operation.
     * @return The fully encoded {@link ByteMessage} containing the player update packet.
     */
    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage msg = ByteMessage.message(90, MessageType.VAR_SHORT, buffer);
        ByteMessage blockMsg = ByteMessage.raw();
        try {
            LocalMobRepository localMobs = player.getLocalMobs();

            // Start writing the header in bit access mode.
            msg.startBitAccess();

            // Encode movement and update blocks for the local player itself.
            handleMovement(player, msg, true);
            blockSet.encode(player, blockMsg, UpdateState.UPDATE_SELF);

            // Write the amount of players that are currently in the player's update view.
            msg.putBits(8, localMobs.updatingPlayersCount());

            // Update existing local players: movement, visibility and update blocks.
            localMobs.forUpdatingPlayers(other -> {
                if (other.isViewableFrom(player) && !other.isPendingPlacement()) {
                    handleMovement(other, msg, false);
                    blockSet.encode(other, blockMsg, UpdateState.UPDATE_LOCAL);
                    return false;
                } else {
                    // No longer viewable or being placed; remove from local view.
                    msg.putBit(true);
                    msg.putBits(2, 3);
                    localMobs.removeLocal(other);
                    return true;
                }
            });

            // Add new players into the player's local view, with a hard limit per cycle and an absolute cap imposed
            // by the update protocol.
            int added = 0;
            for (Player other : localPlayers) {
                if (added == 25 || localMobs.updatingPlayersCount() >= 255) {
                    break;
                }
                if (player.equals(other)) {
                    continue;
                }
                if (other.getPosition().isViewable(player.getPosition()) && localMobs.add(other)) {
                    added++;
                    addPlayer(msg, player, other);
                    blockSet.encode(other, blockMsg, UpdateState.ADD_LOCAL);
                }
            }

            // If any update blocks were written, terminate the player list with a sentinel and append the block
            // buffer. Otherwise, just end bit access.
            if (blockMsg.getBuffer().readableBytes() > 0) {
                msg.putBits(11, 2047); // Sentinel value marking the end of player indices.
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
     * Encodes the addition of a new player into {@code player's} local view.
     * <p>
     * This writes the added player's index, relative position, and the bits indicating that the player should be
     * drawn and that an update block follows.
     *
     * @param msg The message to write into, in bit access mode.
     * @param player The player that is being updated.
     * @param addPlayer The player being added to {@code player's} local view.
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
     * Encodes running, walking, or teleportation movement for {@code player}.
     * <p>
     * The protocol supports:
     * <ul>
     *     <li>Teleport / placement for {@code player} when {@code updateSelf} is {@code true}.</li>
     *     <li>No movement (idle) with optional update block.</li>
     *     <li>Single-step walking in one direction.</li>
     *     <li>Double-step running with both a walking and running direction.</li>
     * </ul>
     * When movement is present, the bit indicating whether an update block follows is also written. For {@code player},
     * a chat-only update is skipped because the client handles it directly.
     *
     * @param player The player whose movement is being encoded.
     * @param msg The message to write into, in bit access mode.
     * @param updateSelf {@code true} if this call is encoding {@code player}'s own movement, allowing teleport/placement
     * handling and chat-only optimisation.
     */
    private void handleMovement(Player player, ByteMessage msg, boolean updateSelf) {
        boolean updateRequired = !player.getFlagData().isEmpty();
        if (updateSelf && player.getFlagData().size() == 1 && player.getFlagData().contains(UpdateFlag.CHAT)) {
            // We don't need to update only chat for ourselves, the client handles that.
            updateRequired = false;
        }
        if (player.isPendingPlacement() && updateSelf) {
            Position position = player.getPosition();

            msg.putBit(true);
            msg.putBits(2, 3);
            msg.putBit(true);
            msg.putBits(2, position.getZ());
            msg.putBits(7, position.getLocalY(player.getLastRegion()));
            msg.putBits(7, position.getLocalX(player.getLastRegion()));
            msg.putBit(updateRequired);
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
                msg.putBit(updateRequired);
            } else {
                msg.putBits(2, 1);
                msg.putBits(3, walkingDirection.getId());
                msg.putBit(updateRequired);
            }
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
