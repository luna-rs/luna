package io.luna.game.model.mob.block;

import com.google.common.collect.ImmutableList;
import io.luna.game.model.EntityType;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;

import java.util.ArrayList;
import java.util.List;

import static io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag.APPEARANCE;
import static io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag.CHAT;
import static io.luna.game.model.mob.block.UpdateState.ADD_LOCAL;
import static io.luna.game.model.mob.block.UpdateState.UPDATE_SELF;

/**
 * A model representing a group of update blocks that need to be encoded. Implementations <strong>must be
 * stateless</strong> so instances can be shared concurrently.
 *
 * @author lare96
 */
public abstract class AbstractUpdateBlockSet<E extends Mob> {

    /**
     * The immutable list of update blocks.
     */
    private final ImmutableList<UpdateBlock> updateBlocks;

    /**
     * Creates a new {@link AbstractUpdateBlockSet}.
     */
    public AbstractUpdateBlockSet() {
        updateBlocks = computeBlocks();
    }

    /**
     * Adds the encoded block set to the main updating buffer.
     *
     * @param mob The mob.
     * @param msg The main updating buffer.
     * @param state The updating state.
     */
    public abstract void addBlockSet(E mob, ByteMessage msg, UpdateState state);

    /**
     * Encodes a single update block.
     *
     * @param mob The mob.
     * @param block The update block to encode.
     * @param blockMsg The update block set buffer.
     */
    public abstract void encodeBlock(E mob, UpdateBlock block, ByteMessage blockMsg);

    /**
     * Computes the immutable list of update blocks. Update blocks are handled in the order they are provided here.
     */
    public abstract ImmutableList<UpdateBlock> computeBlocks();

    /**
     * Encodes the backing group of update blocks.
     *
     * @param mob The mob.
     * @param blockMsg The update block set buffer.
     * @param state The update state.
     */
    final void encodeBlockSet(E mob, ByteMessage blockMsg, UpdateState state) {
        List<UpdateBlock> encodeBlocks = new ArrayList<>(updateBlocks.size());
        int mask = 0;
        for (UpdateBlock block : updateBlocks) {
            UpdateFlag updateFlag = block.getFlag();
            if (mob.getType() == EntityType.PLAYER) {
                // We are adding local players, so we need to force the appearance block.
                if (state == ADD_LOCAL && updateFlag == APPEARANCE) {
                    mask |= block.getMask(mob);
                    encodeBlocks.add(block);
                    continue;
                }

                // We are updating ourselves, ignore our own chat block.
                if (state == UPDATE_SELF && updateFlag == CHAT) {
                    continue;
                }
            }

            // Add the update block to the cache, if its flagged.
            if (mob.getFlags().get(updateFlag)) {
                mask |= block.getMask(mob);
                encodeBlocks.add(block);
            }
        }

        if (!encodeBlocks.isEmpty()) {
            // Encode the update mask.
            if (mask >= 0x100 && mob.getType() == EntityType.PLAYER) {
                mask |= 0x20;
                blockMsg.putShort(mask, ByteOrder.LITTLE);
            } else {
                blockMsg.put(mask);
            }

            // And finally, encode the update blocks!
            for (UpdateBlock block : encodeBlocks) {
                encodeBlock(mob, block, blockMsg);
            }
        }
    }

    /**
     * Encodes this enitre block set.
     *
     * @param mob The mob.
     * @param msg The main updating buffer.
     * @param state The update state.
     */
    public void encode(E mob, ByteMessage msg, UpdateState state) {
        if (!mob.getFlags().isEmpty() || state == ADD_LOCAL) {
            addBlockSet(mob, msg, state);
        }
    }
}
