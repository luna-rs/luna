package io.luna.game.model.mob;

import io.luna.util.StringUtils;

/**
 * A model representing a player's credentials.
 *
 * @author lare96
 */
public final class PlayerCredentials {

    /**
     * The username.
     */
    private final String username;

    /**
     * The password.
     */
    private String password;

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
     * Creates a new {@link PlayerCredentials}.
     *
     * @param username The username.
     */
    public PlayerCredentials(String username) {
        this(username, null);
    }

    /**
     * @return The username.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the new password.
     */
    public void setPassword(String password) {
        this.password = password;
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
