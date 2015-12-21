package io.luna.net.codec.login;

/**
 * An enumerated type whose elements represent all of the possible login responses.
 *
 * @author lare96 <http://github.com/lare96>
 */
public enum LoginResponse {
    NORMAL(2),
    INVALID_CREDENTIALS(3),
    ACCOUNT_DISABLED(4),
    ACCOUNT_ONLINE(5),
    SERVER_JUST_UPDATED(6),
    WORLD_FULL(7),
    LOGIN_SERVER_OFFLINE(8),
    LOGIN_LIMIT_EXCEEDED(9),
    BAD_SESSION_ID(10),
    PLEASE_TRY_AGAIN(11),
    NEED_MEMBERS(12),
    COULD_NOT_COMPLETE_LOGIN(13),
    SERVER_BEING_UPDATED(14),
    LOGIN_ATTEMPTS_EXCEEDED(16),
    MEMBERS_ONLY_AREA(17);

    /**
     * The response code.
     */
    private final int opcode;

    /**
     * Creates a new {@link LoginResponse}.
     *
     * @param opcode The response code.
     */
    private LoginResponse(int opcode) {
        this.opcode = opcode;
    }

    /**
     * @return The response code.
     */
    public final int getOpcode() {
        return opcode;
    }
}
