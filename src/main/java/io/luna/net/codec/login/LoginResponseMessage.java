package io.luna.net.codec.login;

import io.luna.game.model.mobile.PlayerRights;

/**
 * A model representing login response data.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class LoginResponseMessage {

    /**
     * The login response.
     */
    private final LoginResponse response;

    /**
     * The authority level.
     */
    private final PlayerRights rights;

    /**
     * If flagged.
     */
    private final boolean flagged;

    /**
     * Creates a new {@link LoginResponseMessage}.
     *
     * @param response The login response.
     * @param rights The authority level.
     * @param flagged If flagged.
     */
    public LoginResponseMessage(LoginResponse response, PlayerRights rights, boolean flagged) {
        this.response = response;
        this.rights = rights;
        this.flagged = flagged;
    }

    /**
     * Creates a {@link LoginResponseMessage} with an authority level of {@code PLAYER} and a {@code flagged}
     * value of {@code false}.
     *
     * @param response The actual login response.
     */
    public LoginResponseMessage(LoginResponse response) {
        this(response, PlayerRights.PLAYER, false);
    }

    /**
     * @return The login response.
     */
    public LoginResponse getResponse() {
        return response;
    }

    /**
     * @return The authority level.
     */
    public PlayerRights getRights() {
        return rights;
    }

    /**
     * @return {@code true} if flagged.
     */
    public boolean isFlagged() {
        return flagged;
    }
}
