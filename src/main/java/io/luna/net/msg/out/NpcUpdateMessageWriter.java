package io.luna.net.msg.out;

import io.luna.game.model.Direction;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.AbstractUpdateBlockSet;
import io.luna.game.model.mob.block.LocalMobRepository;
import io.luna.game.model.mob.block.NpcUpdateBlockSet;
import io.luna.game.model.mob.block.UpdateState;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;

/**
 * A {@link GameMessageWriter} implementation that encodes and sends NPC updates to a single {@link Player}.
 *
 * @author lare96
 */
public final class NpcUpdateMessageWriter extends GameMessageWriter {

    /**
     * The logger.
     */
    private final Logger logger = LogManager.getLogger();

    /**
     * The update block set used to encode all pending NPC update flags.
     */
    private final AbstractUpdateBlockSet<Npc> blockSet = new NpcUpdateBlockSet();

    /**
     * The collection of NPCs that are considered when populating the player's local view (typically all NPCs
     * in nearby regions).
     */
    private final Collection<Npc> localNpcs;

    /**
     * Creates a new {@link NpcUpdateMessageWriter}.
     *
     * @param localNpcs The collection of NPCs that are considered when populating the player's local view
     * (typically all NPCs in nearby regions).
     */
    public NpcUpdateMessageWriter(Collection<Npc> localNpcs) {
        this.localNpcs = localNpcs;
    }

    /**
     * Encodes the NPC update packet for {@code player}.
     * <p>
     * The encoding process is:
     * <ol>
     *     <li>Begin bit access on the main message.</li>
     *     <li>Write the current local NPC count.</li>
     *     <li>Iterate over the player's {@link LocalMobRepository} of NPCs:
     *         <ul>
     *             <li>If the NPC is still viewable, encode movement and update blocks.</li>
     *             <li>If the NPC is no longer viewable or pending placement, mark it for removal.</li>
     *         </ul>
     *     </li>
     *     <li>Iterate over {@link #localNpcs} to add new NPCs to the local list (up to 25 at a time, and not exceeding
     *     255 total local NPCs).</li>
     *     <li>If any update blocks were encoded, write the end-of-list sentinel and append the block buffer to the
     *     main message.</li>
     *     <li>Restore byte access and return the finished message.</li>
     * </ol>
     * If an exception occurs during encoding, the message buffer is released and the error is logged.
     *
     * @param player The player that will receive the NPC update packet.
     * @param buffer The underlying {@link ByteBuf} allocated for this encoding operation.
     * @return The fully encoded {@link ByteMessage} containing the NPC update packet.
     */
    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage msg = ByteMessage.message(71, MessageType.VAR_SHORT, buffer);
        ByteMessage blockMsg = ByteMessage.raw();

        try {
            LocalMobRepository localMobs = player.getLocalMobs();

            // Start writing the header in bit access mode.
            msg.startBitAccess();

            // Write the amount of NPCs that are currently in the player's update view.
            msg.putBits(8, localMobs.updatingNpcsCount());

            // Update existing local NPCs: movement, visibility and update blocks.
            localMobs.forUpdatingNpcs(other -> {
                if (other.isViewableFrom(player) && !other.isPendingPlacement()) {
                    handleMovement(other, msg);
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

            // Add new NPCs into the player's local view, with a hard limit per cycle and an absolute cap imposed
            // by the update protocol.
            int added = 0;
            for (Npc other : localNpcs) {
                if (added == 25 || localMobs.updatingNpcsCount() >= 255) {
                    break;
                }
                if (other.isViewableFrom(player) && localMobs.add(other)) {
                    addNpc(player, other, msg);
                    blockSet.encode(other, blockMsg, UpdateState.ADD_LOCAL);
                    added++;
                }
            }

            // If any update blocks were written, terminate the NPC list with a sentinel and append the block
            // buffer. Otherwise, just end bit access.
            if (blockMsg.getBuffer().writerIndex() > 0) {
                msg.putBits(14, 16383); // Sentinel value marking the end of NPC indices.
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
     * Encodes the addition of a new NPC into the player's local view.
     * <p>
     * This writes the NPC's index, relative position, and id, and sets the bit indicating whether an update
     * block immediately follows for this NPC.
     *
     * @param player The player that is being updated.
     * @param addNpc The NPC being added to the player's local view.
     * @param msg The message to write into, in bit access mode.
     */
    private void addNpc(Player player, Npc addNpc, ByteMessage msg) {
        boolean updateRequired = !addNpc.getFlagData().isEmpty();

        int deltaX = addNpc.getPosition().getX() - player.getPosition().getX();
        int deltaY = addNpc.getPosition().getY() - player.getPosition().getY();

        msg.putBits(14, addNpc.getIndex());
        msg.putBit(updateRequired);
        msg.putBits(5, deltaY);
        msg.putBits(5, deltaX);
        msg.putBit(true); // NPC is added and should be shown.
        msg.putBits(13, addNpc.getId());
    }

    /**
     * Encodes walking or running movement information for an existing local NPC.
     * <p>
     * The protocol supports:
     * <ul>
     *     <li>No movement (idle) with optional update block.</li>
     *     <li>Single-step walking in one direction.</li>
     *     <li>Double-step running with both a walking and running direction.</li>
     * </ul>
     * When movement is present, the bit indicating whether an update block follows is also written.
     *
     * @param npc The NPC whose movement is being encoded.
     * @param msg The message to write into, in bit access mode.
     */
    private void handleMovement(Npc npc, ByteMessage msg) {
        boolean updateRequired = !npc.getFlagData().isEmpty();
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
