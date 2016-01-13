package io.luna.game.model.mobile;

/**
 * An enumerated type whose elements represent the possible authority levels that a {@link Player} can be assigned.
 *
 * @author lare96 <http://github.org/lare96>
 */
public enum PlayerRights {
    PLAYER(0, 0),
    MODERATOR(1, 1),
    ADMINISTRATOR(2, 2),
    DEVELOPER(2, 3);

    /**
     * The client-sided protocol value. Used to determine how the client sees the player.
     */
    private final int opcode;

    /**
     * The server-sided comparison value. Used to determine how the server sees the player.
     */
    private final int value;

    /**
     * Creates a new {@link PlayerRights}.
     *
     * @param opcode The client-sided protocol value.
     * @param value The server-sided comparison value.
     */
    private PlayerRights(int opcode, int value) {
        this.opcode = opcode;
        this.value = value;
    }

    /**
     * If the underlying comparison value is greater than {@code other}.
     *
     * @param other The {@code PlayerRights} value to compare to this.
     * @return {@code true} if this value is greater, {@code false} otherwise.
     */
    public boolean greater(PlayerRights other) {
        return value > other.value;
    }

    /**
     * If the underlying comparison value is less than {@code other}.
     *
     * @param other The {@code PlayerRights} value to compare to this.
     * @return {@code true} if this value is less, {@code false} otherwise.
     */
    public boolean less(PlayerRights other) {
        return value < other.value;
    }

    /**
     * If the underlying comparison value is equal to {@code other}.
     *
     * @param other The {@code PlayerRights} value to compare to this.
     * @return {@code true} if the values are equal, {@code false} otherwise.
     */
    public boolean equal(PlayerRights other) {
        return value == other.value;
    }

    /**
     * @return The client-sided protocol value.
     */
    public final int getOpcode() {
        return opcode;
    }

    /**
     * @return The server-sided comparison value.
     */
    public final int getValue() {
        return value;
    }
}
