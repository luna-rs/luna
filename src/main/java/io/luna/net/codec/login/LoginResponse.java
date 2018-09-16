package io.luna.net.codec.login;

import io.luna.net.client.Client;

/**
 * An enumerated type whose values represent login responses. Any response other than {@link #NORMAL} will
 * cause the {@link Client} to disconnect.
 *
 * @author lare96 <http://github.com/lare96>
 */
public enum LoginResponse {
    NORMAL(2),
    INVALID_CREDENTIALS(3),
    ACCOUNT_BANNED(4),
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
     * The opcode.
     */
    private final int opcode;

    /**
     * Creates a new {@link LoginResponse}.
     *
     * @param opcode The opcode.
     */
    LoginResponse(int opcode) {
        this.opcode = opcode;
    }

    /**
     * @return The opcode.
     */
    public final int getOpcode() {
        return opcode;
    }
}
