package io.luna.game.model.mobile.update;

import io.luna.game.model.EntityType;
import io.luna.game.model.mobile.MobileEntity;
import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

/**
 * A group of {@link UpdateBlock}s that will be encoded and written to the main update message buffer.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class UpdateBlockSet {

    /**
     * An ordered {@link Set} that contains all of the {@link UpdateBlock}s that will be handled.
     */
    private final Set<UpdateBlock> updateBlocks = new LinkedHashSet<>();

    /**
     * Adds an {@link UpdateBlock} to this {@code UpdateBlockSet}. Throws an {@link IllegalStateException} if this {@code
     * UpdateBlockSet} already contains {@code block}.
     *
     * @param block The {@link UpdateBlock} to add.
     */
    public void add(UpdateBlock block) {
        checkState(updateBlocks.add(block), "update block " + block + " already added");
    }

    /**
     * Makes a call to the function that encodes all of the required {@link UpdateBlock}s, and then caches the result if need
     * be and writes it to the main update message buffer.
     *
     * @param other The {@link MobileEntity} being updated for {@code player}.
     * @param player The {@link Player} these blocks are being handled for.
     * @param msg The main update message buffer.
     * @param forceAppearance If the {@code APPEARANCE} block should be forced, should always be {@code false} for the {@link
     * Npc} update procedure.
     * @param noChat If the {@code CHAT} block should be ignored, should always be {@code false} for the {@link Npc} update
     * procedure.
     */
    public void handleUpdateBlocks(MobileEntity other, Player player, ByteMessage msg, boolean forceAppearance, boolean noChat) {
        if (other.type() == EntityType.NPC) {
            // Caching the NPC update blocks won't bring us much performance increases, NPCs do not have an appearance
            // block nor a manual chat block so the process is simple: check if any update blocks are flagged and if they
            // are then encode them and add the data to the main buffer.

            checkState(!forceAppearance, "forceAppearance should be false for NPC updating");
            checkState(!noChat, "noChat should be false for NPC updating");

            if (player.getUpdateFlags().isEmpty()) {
                return;
            }
            ByteMessage encodeMsg = encodeBlocks(player, forceAppearance, noChat);
            msg.putBytes(encodeMsg);

            encodeMsg.release();
        } else if (other.type() == EntityType.PLAYER) {
            // The Player update block process is a bit more complicated, but still fairly simple. If any update blocks are
            // flagged or appearance needs to be forced then either the update blocks are encoded and added to the main
            // buffer or if the Player has a cached update block available then that is simply written to the main buffer
            // to avoid (potentially) expensive encoding operations (mainly for appearance).

            if (player.getUpdateFlags().isEmpty() && !forceAppearance) {
                return;
            }
            if (player.getCachedBlock() != null && !other.equals(player) && !forceAppearance && !noChat) {

                // Why waste time re-encoding the blocks when the data will be exactly the same? Use the exact same block
                // that we've cached here!
                msg.putBytes(player.getCachedBlock());
                return;
            }

            ByteMessage cachedBlock = encodeBlocks(player, forceAppearance, noChat);

            if (!other.equals(player) && !forceAppearance && !noChat) {

                // We've encoded the update blocks, cache them so we don't have to re-encode the exact same
                // one again later on.
                player.setCachedBlock(cachedBlock.getBuffer());
            }
            msg.putBytes(cachedBlock);
            cachedBlock.release();
        }
    }

    /**
     * Encodes all of the required {@link UpdateBlock}s and returns the buffer with the data.
     *
     * @param player The {@link Player} these blocks are being handled for.
     * @param forceAppearance If the {@code APPEARANCE} block should be forced, should always be {@code false} for the {@link
     * Npc} update procedure.
     * @param noChat If the {@code CHAT} block should be ignored, should always be {@code false} for the {@link Npc} update
     * procedure.
     */
    private ByteMessage encodeBlocks(Player player, boolean forceAppearance, boolean noChat) {
        ByteMessage blockMsg = ByteMessage.create();
        int mask = 0;

        Set<UpdateBlock> writeBlocks = new LinkedHashSet<>();

        for (UpdateBlock updateBlock : updateBlocks) {
            if (updateBlock.getFlag() == UpdateFlag.APPEARANCE && forceAppearance) {

                // We need to force appearance without meddling with the update flags, so do it here!
                mask |= updateBlock.getMask();
                writeBlocks.add(updateBlock);
                continue;
            }
            if (!player.getUpdateFlags().get(updateBlock.getFlag())) {
                continue;
            }
            if (updateBlock.getFlag() == UpdateFlag.CHAT && noChat) {

                // Chat must be blocked out, so on the chat update block we ignore it like it doesn't exist. This is
                // required so chat isn't displayed twice.
                continue;
            }
            mask |= updateBlock.getMask();
            writeBlocks.add(updateBlock);
        }

        if (mask >= 0x100) {
            mask |= 0x40;
            blockMsg.putShort(mask, ByteOrder.LITTLE);
        } else {
            blockMsg.put(mask);
        }

        writeBlocks.forEach(it -> it.write(player, blockMsg));
        return blockMsg;
    }
}
