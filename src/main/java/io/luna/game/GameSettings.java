package io.luna.game;

import io.luna.LunaRuntime;
import io.luna.game.model.Position;
import io.luna.game.model.chunk.ChunkUpdatableView;
import io.luna.game.model.item.GroundItem;
import io.netty.util.ResourceLeakDetector.Level;

/**
 * Settings parsed from the {@code "game"} section of {@code ./data/luna.json}.
 * <p>
 * This class is immutable and instantiated by the JSON loader (not directly in code). It provides core runtime
 * configuration such as networking, starting location, XP rate, persistence serializer selection,
 * password hashing policy, and a few world-behavior toggles.
 *
 * @author lare96
 */
public final class GameSettings {

    /**
     * Password hashing/verification policy for player logins.
     * <p>
     * The {@link #getRounds()} value corresponds to BCrypt "log rounds" when hashing is enabled. When {@link #NONE}
     * is selected, password verification is disabled entirely.
     */
    public enum PasswordStrength {

        /**
         * Disables password verification.
         * <p>
         * Intended only for local development/testing. In this mode, accounts are effectively protected only by
         * username, and password entries are not meaningfully validated.
         */
        NONE(-1),

        /**
         * Minimal BCrypt cost factor (fastest, weakest).
         * <p>
         * Useful for local testing or constrained environments where hashing cost must be low.
         */
        LOW(4),

        /**
         * Balanced BCrypt cost factor (recommended default).
         */
        DEFAULT(8),

        /**
         * Higher BCrypt cost factor (slowest, strongest).
         * <p>
         * Use when you want stronger brute-force resistance and can afford higher CPU cost on login.
         */
        HIGH(16);

        /**
         * BCrypt cost factor (log rounds), or {@code -1} when hashing is disabled.
         */
        private final int rounds;

        /**
         * Creates a new {@link PasswordStrength}.
         *
         * @param rounds BCrypt cost factor (log rounds), or {@code -1} when hashing is disabled.
         */
        PasswordStrength(int rounds) {
            this.rounds = rounds;
        }

        /**
         * Returns BCrypt cost factor (log rounds).
         *
         * @return BCrypt rounds, or {@code -1} when hashing/verification is disabled.
         */
        public int getRounds() {
            return rounds;
        }
    }

    /**
     * Runtime mode (development vs production).
     */
    private final LunaRuntime runtimeMode;

    /**
     * TCP port the server binds to.
     */
    private final int port;

    /**
     * Max simultaneous connections per IP/channel.
     */
    private final int connectionLimit;

    /**
     * Starting spawn position for new players / invalid logouts.
     */
    private final Position startingPosition;

    /**
     * Global experience multiplier applied to skill XP gains.
     */
    private final double experienceMultiplier;

    /**
     * Fully-qualified or simple serializer identifier from {@code io.luna.game.model.mob.persistence}.
     */
    private final String serializer;

    /**
     * Password hashing policy.
     */
    private final PasswordStrength passwordStrength;

    /**
     * Whether stackable ground items merge into a single stack.
     */
    private final boolean mergeStackableGroundItems;

    /**
     * Returns the TCP port that the server will bind on.
     */
    public int port() {
        return port;
    }

    /**
     * Returns the maximum number of concurrent connections allowed per IP.
     * <p>
     * This limits how many accounts can be logged in at the same time from the same IP address.
     */
    public int connectionLimit() {
        return connectionLimit;
    }

    /**
     * Returns the starting position for new players (and for safety fallbacks).
     */
    public Position startingPosition() {
        return startingPosition;
    }

    /**
     * Returns the global experience multiplier.
     * <p>
     * This value determines how quickly mobs gain skill XP relative to base rates.
     */
    public double experienceMultiplier() {
        return experienceMultiplier;
    }

    /**
     * Returns the persistence serializer identifier to use.
     * <p>
     * This value corresponds to a serializer implementation in the {@code io.luna.game.model.mob.persistence} package.
     */
    public String serializer() {
        return serializer;
    }

    /**
     * Returns the BCrypt password encryption/verification strength policy.
     * <p>
     * It is safe to change strength levels in production: existing character data is not broken, because verification
     * uses the stored BCrypt hash parameters.
     *
     * @see PasswordStrength
     */
    public PasswordStrength passwordStrength() {
        return passwordStrength;
    }

    /**
     * Returns whether stackable ground items are merged into a single stack on the same tile/view.
     * <p>
     * If enabled, stackable items with the same {@code id} and {@link ChunkUpdatableView} on the same {@link Position}
     * will be merged into one {@link GroundItem} stack. This reduces entity count and improves convenience.
     * <p>
     * If disabled, each stack is represented as its own {@link GroundItem} entity.
     */
    public boolean mergeStackableGroundItems() {
        return mergeStackableGroundItems;
    }

    /**
     * Returns {@code true} when the server is running in a “beta-like” mode.
     */
    public boolean betaMode() {
        switch (runtimeMode) {
            case DEVELOPMENT:
                return true;
            case PRODUCTION:
                return false;
        }
        throw new IllegalStateException("Invalid runtime mode!");
    }

    /**
     * Returns the Netty resource leak detection level appropriate for the current runtime mode.
     * <p>
     * <strong>Important:</strong> higher leak detection levels can cause substantial performance loss.
     * {@code PARANOID} should never be used in production.
     */
    public Level resourceLeakDetection() {
        switch (runtimeMode) {
            case PRODUCTION:
                return Level.DISABLED;
            case DEVELOPMENT:
                return Level.PARANOID;
            default:
                throw new IllegalStateException("Invalid runtime mode!");
        }
    }

    /**
     * Returns the current runtime mode.
     */
    public LunaRuntime runtimeMode() {
        return runtimeMode;
    }

    /**
     * Private constructor for JSON deserialization.
     */
    private GameSettings(LunaRuntime runtimeMode, int port, int connectionLimit, Position startingPosition,
                         double experienceMultiplier, String serializer, PasswordStrength passwordStrength,
                         boolean mergeStackableGroundItems) {
        // Will never be called directly.
        this.runtimeMode = runtimeMode;
        this.port = port;
        this.connectionLimit = connectionLimit;
        this.startingPosition = startingPosition;
        this.experienceMultiplier = experienceMultiplier;
        this.serializer = serializer;
        this.passwordStrength = passwordStrength;
        this.mergeStackableGroundItems = mergeStackableGroundItems;
    }
}
