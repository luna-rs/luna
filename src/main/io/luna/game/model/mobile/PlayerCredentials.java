package io.luna.game.model.mobile;

import io.luna.util.StringUtils;

/**
 * An immutable class that holds the credentials for a {@link Player}.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerCredentials {

    /**
     * The username credential.
     */
    private final String username;

    /**
     * The password credential.
     */
    private final String password;

    /**
     * The username hash credential, generated from the {@code username}.
     */
    private final long usernameHash;

    /**
     * Creates a new {@link PlayerCredentials}.
     *
     * @param username The username credential.
     * @param password The password credential.
     */
    public PlayerCredentials(String username, String password) {
        this.username = username;
        this.password = password;
        usernameHash = StringUtils.encodeToBase37(username);
    }

    /**
     * @return The username credential.
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return The password credential.
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
