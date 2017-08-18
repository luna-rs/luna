package io.luna.game.model.mob.update;

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
        FORCE_CHAT,
        INTERACTION,
        FACE_POSITION,
        PRIMARY_HIT,
        SECONDARY_HIT,
        TRANSFORM,
        FORCE_MOVEMENT
    }

    /**
     * A set that tracks flagged update blocks.
     */
    private final EnumSet<UpdateFlag> flags = EnumSet.noneOf(UpdateFlag.class);

    /**
     * Flag an update block.
     */
    public void flag(UpdateFlag flag) {
        flags.add(flag);
    }

    /**
     * Unflag an update block.
     */
    public void unflag(UpdateFlag flag) {
        flags.remove(flag);
    }

    /**
     * Retrieves the flag status of an update block.
     */
    public boolean get(UpdateFlag flag) {
        return flags.contains(flag);
    }

    /**
     * Returns if the backing set is empty or not.
     */
    public boolean isEmpty() {
        return flags.isEmpty();
    }

    /**
     * Clears the backing set.
     */
    public void clear() {
        flags.clear();
    }
}
