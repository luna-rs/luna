package io.luna.game.model.mob;

import io.luna.util.StringUtils;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * An enum representing authority levels.
 *
 * @author lare96 <http://github.org/lare96>
 */
public enum PlayerRights {
    
    // Order matters, because >=/<= use compareTo in Kotlin.
    PLAYER(0, 0),
    MODERATOR(1, 1),
    ADMINISTRATOR(2, 2),
    DEVELOPER(2, 3);

    /**
     * The protocol value. May only be between 0-2 (player, moderator, administrator).
     */
    private final int clientValue;

    /**
     * The server value.
     */
    private final int serverValue;

    /**
     * The formatted name. Lowercase and capitalized.
     */
    private final String formattedName;

    /**
     * Creates a new {@link PlayerRights}.
     *
     * @param clientValue The protocol value.
     * @param serverValue The server value.
     */
    PlayerRights(int clientValue, int serverValue) {
        checkArgument(clientValue >= 0 && clientValue <= 2, "clientValue must be >= 0 && <= 2");

        this.clientValue = clientValue;
        this.serverValue = serverValue;
        formattedName = StringUtils.capitalize(name().toLowerCase());
    }

    /**
     * Determines if this rights level is greater than {@code other}.
     *
     * @param other The other rights level.
     * @return {@code true} if this rights level is greater than {@code other}.
     */
    public boolean greater(PlayerRights other) {
        return serverValue > other.serverValue;
    }

    /**
     * Determines if this rights level is equal to or greater than {@code other}.
     *
     * @param other The other rights level.
     * @return {@code true} if this rights level is equal to or greater than {@code other}.
     */
    public boolean equalOrGreater(PlayerRights other) {
        return serverValue >= other.serverValue;
    }

    /**
     * Determines if this rights level is less than {@code other}.
     *
     * @param other The other rights level.
     * @return {@code true} if this rights level is less than {@code other}.
     */
    public boolean less(PlayerRights other) {
        return serverValue < other.serverValue;
    }

    /**
     * Determines if this rights level is equal to or less than {@code other}.
     *
     * @param other The other rights level.
     * @return {@code true} if this rights level is equal to or less than {@code other}.
     */
    public boolean equalOrLess(PlayerRights other) {
        return serverValue <= other.serverValue;
    }

    /**
     * Determines if this rights level is equal to {@code other}.
     *
     * @param other The other rights level.
     * @return {@code true} if this rights level is equal to {@code other}.
     */
    public boolean equal(PlayerRights other) {
        return serverValue == other.serverValue;
    }

    /**
     * @return The protocol value.
     */
    public final int getClientValue() {
        return clientValue;
    }

    /**
     * @return The server value.
     */
    public final int getServerValue() {
        return serverValue;
    }

    /**
     * @return The formatted name. Lowercase and capitalized.
     */
    public String getFormattedName() {
        return formattedName;
    }
}
