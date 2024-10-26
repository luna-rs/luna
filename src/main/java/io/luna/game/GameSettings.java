package io.luna.game;

import io.luna.LunaRuntime;
import io.luna.game.model.Position;
import io.netty.util.ResourceLeakDetector.Level;

/**
 * Holds settings parsed from the "game" section in {@code ./data/luna.json} file.
 *
 * @author lare96
 */
public final class GameSettings {

    private final LunaRuntime runtimeMode;
    private final int port;
    private final int connectionLimit;
    private final Position startingPosition;
    private final double experienceMultiplier;
    private final String serializer;
    private final int passwordStrength;

    /**
     * The port that the server will be bound on.
     */
    public int port() {
        return port;
    }

    /**
     * The maximum amount of connections allowed per channel. This restricts how many accounts can be logged in
     * at the same time, from the same IP address.
     */
    public int connectionLimit() {
        return connectionLimit;
    }

    /**
     * The position that new players will start on.
     */
    public Position startingPosition() {
        return startingPosition;
    }

    /**
     * The experience multiplier. This value determines how fast mobs can level up their skills.
     */
    public double experienceMultiplier() {
        return experienceMultiplier;
    }

    /**
     * The serializer from the {@code io.luna.game.model.mob.persistence} package that will be used to serialize and
     * deserialize player data.
     */
    public String serializer() {
        return serializer;
    }

    /**
     * How strong BCrypt password encryption will be. CPU usage increases by a factor of 2 for every +1 added to this
     * value. Values valid are 4-30. Ideally, this value should be set to where each password verification/hashing takes
     * around 150-250ms.
     */
    public int passwordStrength() {
        return passwordStrength;
    }

    /**
     * Determines if luna is running in Beta mode.
     */
    public boolean betaMode() {
        switch (runtimeMode) {
            case DEVELOPMENT:
            case BENCHMARK:
                return true;
            case PRODUCTION:
                return false;
        }
        throw new IllegalStateException("Invalid runtime mode!");
    }

    /**
     * Please note as the leak detection levels get higher, the tradeoff is a <strong>substantial</strong>
     * performance loss. {@code PARANOID} should <strong>never</strong> be used in a production environment.
     */
    public Level resourceLeakDetection() {
        switch (runtimeMode) {
            case PRODUCTION:
                return Level.DISABLED;
            case BENCHMARK:
                return Level.SIMPLE;
            case DEVELOPMENT:
                return Level.PARANOID;
            default:
                throw new IllegalStateException("Invalid runtime mode!");
        }
    }

    /**
     * Determines what mode Luna is running in.
     */
    public LunaRuntime runtimeMode() {
        return runtimeMode;
    }

    /**
     * To prevent public instantiation.
     */
    private GameSettings(LunaRuntime runtimeMode, int port, int connectionLimit, Position startingPosition,
                         double experienceMultiplier, String serializer, int passwordStrength) {
        // Will never be called.
        this.runtimeMode = runtimeMode;
        this.port = port;
        this.connectionLimit = connectionLimit;
        this.startingPosition = startingPosition;
        this.experienceMultiplier = experienceMultiplier;
        this.serializer = serializer;
        this.passwordStrength = passwordStrength;
    }
}