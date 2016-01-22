package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.MobileEntity;
import io.luna.game.model.mobile.Player;
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
public abstract class UpdateBlockSet<E extends MobileEntity> {

    /**
     * An ordered {@link Set} that contains all of the {@link UpdateBlock}s that will be handled.
     */
    private final Set<UpdateBlock<E>> updateBlocks = new LinkedHashSet<>();

    /**
     * An ordered {@link Set} that contains all of the {@link UpdateBlock}s that will actually be written.
     */
    private final Set<UpdateBlock<E>> writeBlocks = new LinkedHashSet<>();

    /**
     * An integer that holds the status of the update flags. Bitwise operators are used to manipulate it.
     */
    private int mask;

    /**
     * Adds an {@link UpdateBlock} to this {@code UpdateBlockSet}. Throws an {@link IllegalStateException} if this {@code
     * UpdateBlockSet} already contains {@code block}.
     *
     * @param block The {@link UpdateBlock} to add.
     */
    public void add(UpdateBlock<E> block) {
        checkState(updateBlocks.add(block), "this UpdateBlock type already added to internal block set");
    }

    /**
     * Determines if {@link UpdateBlock}s as a whole need to be encoded for {@code forMob}.
     *
     * @param forMob The {@link MobileEntity} to encode for.
     * @param player The {@link Player} that is being updated.
     * @param msg The main update block, where the encoded {@code UpdateBlock}s should be added to.
     * @return {@code true} if the encoding process should continue, {@code false} otherwise.
     */
    protected abstract boolean needsEncodeBlocks(E forMob, Player player, ByteMessage msg);

    /**
     * Determines if {@code block} should be encoded for {@code forMob}.
     *
     * @param forMob The {@link MobileEntity} to encode for.
     * @param block The {@link UpdateBlock} to encode.
     * @return {@code true} if the {@code UpdateBlock} should be encoded, {@code false} otherwise.
     */
    protected boolean canEncodeBlock(E forMob, UpdateBlock<E> block) {
        // optional implementation
        return true;
    }

    /**
     * A function invoked when all of the {@link UpdateBlock}s have been encoded and added to the main block.
     *
     * @param forMob The {@link MobileEntity} the {@code UpdateBlock}s were encoded for.
     * @param player The {@link Player} that {@code forMob} is being updated for.
     * @param encodedBlock The actual block that was encoded. This value is safe to modify.
     */
    protected void onEncodeFinish(E forMob, Player player, ByteMessage encodedBlock) {
        // optional implementation
    }

    /**
     * Encodes the {@link UpdateBlock}s for {@code forMob}, and adds it to the main block for {@code player}. Implementations
     * of {@code UpdateBlockSet} ultimately determine how this code functions.
     *
     * @param forMob The {@link MobileEntity} to encode for.
     * @param player The {@link Player} being updated.
     * @param msg The main block, belonging to {@code player}.
     */
    public final void encodeUpdateBlocks(E forMob, Player player, ByteMessage msg) {
        if (!needsEncodeBlocks(forMob, player, msg)) {
            return;
        }

        ByteMessage encodedBlock = ByteMessage.message();
        try {
            for (UpdateBlock<E> updateBlock : updateBlocks) {
                if (!canEncodeBlock(forMob, updateBlock)) {
                    continue;
                }
                if (!forMob.getUpdateFlags().get(updateBlock.getFlag())) {
                    continue;
                }
                mask |= updateBlock.getMask();
                writeBlocks.add(updateBlock);
            }

            if (mask >= 0x100) {
                mask |= 0x40;
                encodedBlock.putShort(mask, ByteOrder.LITTLE);
            } else {
                encodedBlock.put(mask);
            }

            writeBlocks.forEach(it -> it.write(forMob, encodedBlock));

            msg.putBytes(encodedBlock);
            onEncodeFinish(forMob, player, encodedBlock);
        } finally {
            encodedBlock.release();
        }
    }

    /**
     * Adds a new {@link UpdateBlock} that will be encoded. Used to force certain blocks.
     *
     * @param block The {@code UpdateBlock} to add.
     */
    protected void addBlock(UpdateBlock<E> block) {
        mask |= block.getMask();
        writeBlocks.add(block);
    }
}
