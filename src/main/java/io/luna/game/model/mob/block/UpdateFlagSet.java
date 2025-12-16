package io.luna.game.model.mob.block;

import com.google.common.collect.ImmutableSet;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Iterator;

/**
 * A container used to track which {@link UpdateFlag} values a mob must apply during the next synchronization cycle.
 *
 * @author lare96
 */
public final class UpdateFlagSet implements Iterable<UpdateFlag> {

    /**
     * The list of all possible update flags that a mob may signal during synchronization. Each flag corresponds to
     * a specific {@link UpdateBlock} type.
     */
    public enum UpdateFlag {

        /**
         * Signals that a mob's appearance (equipment, models, colors) must be refreshed.
         */
        APPEARANCE,

        /**
         * Signals that the mob has spoken and its chat message must be sent to nearby players.
         */
        CHAT,

        /**
         * Signals that a graphic (spot animation) should be displayed.
         */
        GRAPHIC,

        /**
         * Signals that an animation should be played (emotes, combat animations, etc.)
         */
        ANIMATION,

        /**
         * Signals a forced chat message, overriding normal behavior.
         */
        SPEAK,

        /**
         * Signals that the mob's interaction target has changed (face entity).
         */
        INTERACTION,

        /**
         * Signals that the mob must face a specific world coordinate.
         */
        FACE_POSITION,

        /**
         * Signals that the primary hitmark (+ damage + soak) should be displayed.
         */
        PRIMARY_HIT,

        /**
         * Signals that the secondary hitmark should be displayed.
         */
        SECONDARY_HIT,

        /**
         * Signals that the mob transforms into a different entity/NPC id.
         */
        TRANSFORM,

        /**
         * Signals that the mob is performing forced movement.
         */
        EXACT_MOVEMENT
    }

    /**
     * Internal storage of active flags.
     */
    private final EnumSet<UpdateFlag> flags = EnumSet.noneOf(UpdateFlag.class);

    @NotNull
    @Override
    public Iterator<UpdateFlag> iterator() {
        return flags.iterator();
    }

    /**
     * Flags an update block, marking it so it will be encoded in the next update cycle.
     *
     * @param flag The update flag to add.
     */
    public void flag(UpdateFlag flag) {
        flags.add(flag);
    }

    /**
     * Determines if a specific flag is currently active.
     *
     * @param flag The update flag to check.
     * @return {@code true} if the flag is set, otherwise {@code false}.
     */
    public boolean flagged(UpdateFlag flag) {
        return flags.contains(flag);
    }

    /**
     * Returns whether any update flags are currently active.
     *
     * @return {@code true} if no flags are set.
     */
    public boolean isEmpty() {
        return flags.isEmpty();
    }

    /**
     * Clears all active update flags.
     * <p>
     * This is typically called at the end of a mob’s update processing.
     */
    public void clear() {
        flags.clear();
    }

    /**
     * Creates an immutable snapshot of the backing update flags. Primarily used by player updating threads.
     *
     * @return An immutable snapshot of the flags.
     */
    public ImmutableSet<UpdateFlag> snapshot() {
        return ImmutableSet.copyOf(flags);
    }

    /**
     * Returns the number of active update flags.
     *
     * @return The number of flags currently set.
     */
    public int size() {
        return flags.size();
    }
}
