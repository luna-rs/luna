package io.luna.game.model.mob.block;

import java.util.EnumSet;

/**
 * A model that manages update flags for mobs.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class UpdateFlagSet {

    /**
     * An enum representing update flags.
     */
    public enum UpdateFlag {
        APPEARANCE,
        CHAT,
        GRAPHIC,
        ANIMATION,
        FORCED_CHAT,
        INTERACTION,
        FACE_POSITION,
        PRIMARY_HIT,
        SECONDARY_HIT,
        TRANSFORM,
        FORCED_MOVEMENT
    }

    /**
     * A set that tracks flagged update blocks.
     */
    private final EnumSet<UpdateFlag> flags = EnumSet.noneOf(UpdateFlag.class);

    /**
     * Flag an update block.
     *
     * @param flag The block to flag.
     */
    public void flag(UpdateFlag flag) {
        flags.add(flag);
    }

    /**
     * Unflag an update block.
     *
     * @param flag The block to unflag.
     */
    public void unflag(UpdateFlag flag) {
        flags.remove(flag);
    }

    /**
     * Retrieves the flag status of an update block.
     *
     * @param flag The flag to lookup.
     * @return {@code true} if {@code flag} is flagged.
     */
    public boolean get(UpdateFlag flag) {
        return flags.contains(flag);
    }

    /**
     * Returns if the backing set is empty or not.
     *
     * @return {@code true} if no blocks are flagged.
     */
    public boolean isEmpty() {
        return flags.isEmpty();
    }

    /**
     * Clears all flagged blocks. When this method returns, all blocks will be unflagged.
     */
    public void clear() {
        flags.clear();
    }
}
