package io.luna.game.model.mob.block;

import com.google.common.collect.ImmutableList;
import io.luna.game.model.EntityType;
import io.luna.game.model.mob.Mob;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;

import java.util.ArrayList;
import java.util.List;

import static io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag.APPEARANCE;
import static io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag.CHAT;
import static io.luna.game.model.mob.block.UpdateState.ADD_LOCAL;
import static io.luna.game.model.mob.block.UpdateState.UPDATE_SELF;

/**
 * Represents an ordered collection of {@link UpdateBlock}s that may need to be encoded for a {@link Mob}
 * (player or NPC) during the main update cycle.
 * <p>
 * This class is designed to be <strong>stateless and thread-safe</strong>, allowing a single instance to be safely
 * reused by multiple encoder threads.
 * <p>
 * Concrete subclasses (e.g., {@code PlayerUpdateBlockSet}, {@code NpcUpdateBlockSet}) must define how to assemble and
 * encode their specific block types.
 *
 * @param <E> The mob subtype (Player or Npc).
 * @author lare96
 */
public abstract class AbstractUpdateBlockSet<E extends Mob> {

    /**
     * The immutable, ordered list of all possible update blocks.
     * <p>
     * The order of this list determines the encoding order, which must match the client’s expected mask order.
     */
    private final ImmutableList<UpdateBlock> updateBlocks;

    /**
     * Constructs this {@link AbstractUpdateBlockSet} and computes its block list.
     */
    public AbstractUpdateBlockSet() {
        this.updateBlocks = computeBlocks();
    }

    /**
     * Adds the encoded block set to the main updating buffer. This method is responsible for writing the update mask
     * and then calling {@link #encodeBlock(Mob, UpdateBlock, ByteMessage)} for each active block.
     *
     * @param mob The mob whose update is being encoded.
     * @param msg The main updating buffer.
     * @param state The update state (e.g., {@code ADD_LOCAL}, {@code UPDATE_SELF}).
     */
    public abstract void addBlockSet(E mob, ByteMessage msg, UpdateState state);

    /**
     * Encodes an individual {@link UpdateBlock} for a mob.
     *
     * @param mob The mob whose block is being encoded.
     * @param block The update block definition.
     * @param blockMsg The temporary block buffer.
     */
    public abstract void encodeBlock(E mob, UpdateBlock block, ByteMessage blockMsg);

    /**
     * Computes the immutable ordered list of {@link UpdateBlock}s that can be used for this mob type. This is called
     * once per blockset instance.
     *
     * @return An immutable list defining all possible update blocks.
     */
    public abstract ImmutableList<UpdateBlock> computeBlocks();

    /**
     * Encodes all pending update blocks into the specified message buffer. This method determines which blocks are
     * active based on the mob's {@link UpdateFlagSet} and the update {@link UpdateState}.
     *
     * @param mob The mob being updated.
     * @param blockMsg The target buffer for the update mask and block data.
     * @param state The update state.
     */
    final void encodeBlockSet(E mob, ByteMessage blockMsg, UpdateState state) {
        List<UpdateBlock> encodeBlocks = new ArrayList<>(updateBlocks.size());
        int mask = 0;

        for (UpdateBlock block : updateBlocks) {
            var flag = block.getFlag();

            if (mob.getType() == EntityType.PLAYER) {
                // ADD_LOCAL forces the appearance block so the client can render the new player immediately.
                if (state == ADD_LOCAL && flag == APPEARANCE) {
                    mask |= block.getMask(mob);
                    encodeBlocks.add(block);
                    continue;
                }

                // Skip our own chat block during UPDATE_SELF (prevents local echo).
                if (state == UPDATE_SELF && flag == CHAT) {
                    continue;
                }
            }

            // Include block if flagged.
            if (mob.getFlags().flagged(flag)) {
                mask |= block.getMask(mob);
                encodeBlocks.add(block);
            }
        }

        if (!encodeBlocks.isEmpty()) {
            // Encode the mask. Masks > 8 bits require a short (extended mask) with the 0x20 bit set.
            if (mask >= 0x100 && mob.getType() == EntityType.PLAYER) {
                mask |= 0x20;
                blockMsg.putShort(mask, ByteOrder.LITTLE);
            } else {
                blockMsg.put(mask);
            }

            // Encode all active blocks in order.
            for (UpdateBlock block : encodeBlocks) {
                encodeBlock(mob, block, blockMsg);
            }
        }
    }

    /**
     * Determines whether a mob should be updated and, if so, appends its update blockset to the provided update
     * buffer.
     *
     * @param mob The mob whose updates are being processed.
     * @param msg The main updating buffer.
     * @param state The current update state.
     */
    public void encode(E mob, ByteMessage msg, UpdateState state) {
        if (!mob.getFlags().isEmpty() || state == ADD_LOCAL) {
            addBlockSet(mob, msg, state);
        }
    }
}
