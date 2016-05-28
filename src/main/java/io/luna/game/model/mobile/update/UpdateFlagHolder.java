package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.MobileEntity;

import java.util.EnumSet;

/**
 * A container backed by an {@link EnumSet} that manages all of the {@link UpdateFlag}s for {@link MobileEntity}s.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class UpdateFlagHolder {

    /**
     * An enumerated type that holds all of the values representing update flags for {@link MobileEntity}s.
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
     * An {@link EnumSet} that will contain all active {@link UpdateFlag}s.
     */
    private final EnumSet<UpdateFlag> flags = EnumSet.noneOf(UpdateFlag.class);

    /**
     * Adds {@code flag} to the backing {@link EnumSet}.
     *
     * @param flag The {@link UpdateFlag} to add.
     */
    public void flag(UpdateFlag flag) {
        flags.add(flag);
    }

    /**
     * Removes {@code flag} from the backing {@link EnumSet}.
     *
     * @param flag The {@link UpdateFlag} to remove.
     */
    public void unflag(UpdateFlag flag) {
        flags.remove(flag);
    }

    /*
     * @return {@code true} if the backing {@link EnumSet} contains {@code flag}, false otherwise.
     */
    public boolean get(UpdateFlag flag) {
        return flags.contains(flag);
    }

    /**
     * @return {@code true} if the backing {@link EnumSet} is empty.
     */
    public boolean isEmpty() {
        return flags.isEmpty();
    }

    /**
     * Clears the backing {@link EnumSet} of all elements.
     */
    public void clear() {
        flags.clear();
    }
}
