package io.luna.game.model.mobile;

/**
 * An enumerated type whose elements represent the possible authority levels
 * that a {@link Player} can be assigned.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public enum PlayerRights {

    PLAYER(0),
    MODERATOR(1),
    ADMINISTRATOR(2);

    /**
     * The client-sided protocol value.
     */
    private final int opcode;

    /**
     * Creates a new {@link PlayerRights}.
     *
     * @param opcode The client-sided protocol value.
     */
    private PlayerRights(int opcode) {
        this.opcode = opcode;
    }

    /**
     * @return The client-sided protocol value.
     */
    public final int getOpcode() {
        return opcode;
    }
}
