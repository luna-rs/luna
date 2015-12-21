package io.luna.net.codec.login;

import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.PlayerRights;

/**
 * An immutable message that is written through a channel and forwarded to the {@link LoginEncoder} where it is encoded and
 * sent to the client.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class LoginResponseMessage {

    /**
     * The actual login response.
     */
    private final LoginResponse response;

    /**
     * The {@link Player}s authority level.
     */
    private final PlayerRights rights;

    /**
     * If the {@link Player} is flagged.
     */
    private final boolean flagged;

    /**
     * Creates a new {@link LoginResponseMessage}.
     *
     * @param response The actual login response.
     * @param rights The {@link Player}s authority level.
     * @param flagged If the {@codePlayer} is flagged.
     */
    public LoginResponseMessage(LoginResponse response, PlayerRights rights, boolean flagged) {
        this.response = response;
        this.rights = rights;
        this.flagged = flagged;
    }

    /**
     * Creates a new {@link LoginResponseMessage} with an authority level of {@code PLAYER} and a {@code flagged} value of
     * {@code false}.
     *
     * @param response The actual login response.
     */
    public LoginResponseMessage(LoginResponse response) {
        this(response, PlayerRights.PLAYER, false);
    }

    /**
     * @return The actual login response.
     */
    public LoginResponse getResponse() {
        return response;
    }

    /**
     * @return The {@link Player}s authority level.
     */
    public PlayerRights getRights() {
        return rights;
    }

    /**
     * @return {@code true} if flagged, {@code false} otherwise.
     */
    public boolean isFlagged() {
        return flagged;
    }
}
