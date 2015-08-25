package io.luna.game.model.mobile.player;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * An enumerated type representing all of the possible authority levels.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public enum PlayerRights {
    PLAYER(0, 0),
    DONATOR(1, 0),
    SUPER_DONATOR(2, 0),
    EXTREME_DONATOR(3, 0),
    MODERATOR(4, 1),
    ADMINISTRATOR(5, 2),
    DEVELOPER(6, 2);

    /**
     * All authority levels that constitute as being a staff member.
     */
    public static final ImmutableSet<PlayerRights> STAFF = Sets.immutableEnumSet(MODERATOR, ADMINISTRATOR, DEVELOPER);

    /**
     * All authority levels that constitute as being a donator.
     */
    public static final ImmutableSet<PlayerRights> DONATORS = Sets.immutableEnumSet(DONATOR, SUPER_DONATOR, EXTREME_DONATOR);

    /**
     * The amount of authority this right has over all other rights. The higher
     * the value == The higher the authority.
     */
    private final int code;

    /**
     * The protocol value for these rights, {@code 0} for normal player,
     * {@code 1} for moderator, and {@code 2} for administrator.
     */
    private final int protocolCode;

    /**
     * Creates a new {@link io.luna.game.model.mobile.player.PlayerRights}.
     *
     * @param code
     *            The amount of authority this right has.
     * @param protocolCode
     *            The protocol value of this right.
     */
    private PlayerRights(int code, int protocolCode) {
        this.code = code;
        this.protocolCode = protocolCode;
    }

    /**
     * Determines if this right is of a lesser authority than {@code other}.
     * 
     * @param other
     *            The right to compare with this.
     * @return {@code true} if the this right is lesser, {@code false}
     *         otherwise.
     */
    public final boolean less(PlayerRights other) {
        return code < other.code;
    }

    /**
     * Determines if this right is of a greater authority than {@code other}.
     * 
     * @param other
     *            The right to compare with this.
     * @return {@code true} if the this right is greater, {@code false}
     *         otherwise.
     */
    public final boolean greater(PlayerRights other) {
        return code > other.code;
    }

    /**
     * Determines if this right is of a lesser or equal authority than
     * {@code other}.
     * 
     * @param other
     *            The right to compare with this.
     * @return {@code true} if the this right is lesser or equal, {@code false}
     *         otherwise.
     */
    public final boolean lessEquals(PlayerRights other) {
        return code <= other.code;
    }

    /**
     * Determines if this right is of a greater or equal authority than
     * {@code other}.
     * 
     * @param other
     *            The right to compare with this.
     * @return {@code true} if the this right is greater or equal, {@code false}
     *         otherwise.
     */
    public final boolean greaterEquals(PlayerRights other) {
        return code >= other.code;
    }

    /**
     * Determines if this right is of an equal authority to {@code other}.
     * 
     * @param other
     *            The right to compare with this.
     * @return {@code true} if the this right is equal, {@code false} otherwise.
     */
    public final boolean equalTo(PlayerRights other) {
        return code == other.code;
    }

    /**
     * Determines if this is a staff authority level.
     * 
     * @return {@code true} if this is a staff authority level, {@code false}
     *         otherwise.
     */
    public final boolean isStaff() {
        return STAFF.contains(this);
    }

    /**
     * Determines if this is a donator authority level.
     * 
     * @return {@code true} if this is a donator authority level, {@code false}
     *         otherwise.
     */
    public final boolean isDonator() {
        return DONATORS.contains(this);
    }

    /**
     * Gets the amount of authority this right has.
     * 
     * @return The amount of authority.
     */
    public final int getCode() {
        return code;
    }

    /**
     * Gets the protocol value of this right.
     * 
     * @return The protocol value.
     */
    public final int getProtocolCode() {
        return protocolCode;
    }
}
