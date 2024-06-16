package io.luna.net.msg.login;

import io.luna.game.model.mob.PlayerRights;
import io.luna.net.client.Client;

/**
 * An immutable model representing login response data.
 *
 * @author lare96
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
     * If the server suspects the {@link Client} is a bot.
     */
    private final boolean suspectedBot;

    /**
     * Creates a new {@link LoginResponseMessage}.
     *
     * @param response The login response.
     * @param rights The authority level.
     * @param suspectedBot If the server suspects the {@link Client} is a bot.
     */
    public LoginResponseMessage(LoginResponse response, PlayerRights rights, boolean suspectedBot) {
        this.response = response;
        this.rights = rights;
        this.suspectedBot = suspectedBot;
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
     * @return {@code true} if the server suspects the {@link Client} is a bot.
     */
    public boolean isSuspectedBot() {
        return suspectedBot;
    }
}
