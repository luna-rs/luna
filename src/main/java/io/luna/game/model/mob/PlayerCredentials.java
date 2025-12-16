package io.luna.game.model.mob;

import io.luna.util.StringUtils;

/**
 * Holds the authentication credentials for a single player account.
 * <p>
 * The username is normalized to lower-case and a pre-computed base-37 hash is stored for efficient lookups and
 * protocol usage. The password field is mutable so that password changes can be applied in memory and later persisted
 * by the account persistence layer.
 *
 * @author lare96
 */
public final class PlayerCredentials {

    /**
     * The normalized, lower-case username for this account.
     */
    private final String username;

    /**
     * The credential string used to authenticate this account, as entered on the login screen.
     * <p>
     * <p>
     * Note: The password is stored as a plain {@link String}; avoid logging it and keep its lifetime as short as
     * possible.
     * </p>
     */
    private String password;

    /**
     * The pre-computed base-37 hash of {@link #username}, used for protocol encoding and fast lookups.
     */
    private final long usernameHash;

    /**
     * Creates a new {@link PlayerCredentials} instance.
     *
     * @param username The raw username supplied by the client. This will be normalized to lower-case internally.
     * @param password The credential string used to authenticate the account (plaintext).
     */
    public PlayerCredentials(String username, String password) {
        this.username = username.toLowerCase();
        this.password = password;
        usernameHash = StringUtils.encodeToBase37(username);
    }

    /**
     * Returns the normalized, lower-case username.
     *
     * @return The normalized username.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Replaces the current credential string with a new plaintext value.
     *
     * @param password The new credential string (for example, a hashed password).
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Returns the plaintext credential string used to authenticate this account.
     * <p>
     * Callers should treat this value as sensitive and avoid logging or exposing it.
     * </p>
     *
     * @return The current credential string.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Returns the base-37 hash of the username.
     *
     * @return The pre-computed username hash.
     */
    public long getUsernameHash() {
        return usernameHash;
    }
}
