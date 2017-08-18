package io.luna.game.model.mob;

import io.luna.util.StringUtils;

/**
 * A model representing a player's credentials.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerCredentials {

    /**
     * The username.
     */
    private final String username;

    /**
     * The password.
     */
    private final String password;

    /**
     * The username hash.
     */
    private final long usernameHash;

    /**
     * Creates a new {@link PlayerCredentials}.
     *
     * @param username The username.
     * @param password The password.
     */
    public PlayerCredentials(String username, String password) {
        this.username = username;
        this.password = password;
        usernameHash = StringUtils.encodeToBase37(username);
    }

    /**
     * @return The username.
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return The password.
     */
    public String getPassword() {
        return password;
    }

    /**
     * @return The username hash.
     */
    public long getUsernameHash() {
        return usernameHash;
    }
}
