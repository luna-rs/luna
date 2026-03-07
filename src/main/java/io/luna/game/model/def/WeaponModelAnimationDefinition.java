package io.luna.game.model.def;

/**
 * Holds the player model animation ids associated with a weapon.
 * <p>
 * These animations define the stance and locomotion used while the weapon is equipped, and are typically
 * used as a sub-definition by {@link WeaponDefinition} to determine how a character should appear while standing,
 * walking, and running.
 *
 * @author lare96
 */
public final class WeaponModelAnimationDefinition {

    /**
     * The standing animation id.
     */
    private final int standing;

    /**
     * The walking animation id.
     */
    private final int walking;

    /**
     * The running animation id.
     */
    private final int running;

    /**
     * Creates a new {@link WeaponModelAnimationDefinition}.
     *
     * @param standing The standing animation id.
     * @param walking The walking animation id.
     * @param running The running animation id.
     */
    public WeaponModelAnimationDefinition(int standing, int walking, int running) {
        this.standing = standing;
        this.walking = walking;
        this.running = running;
    }

    /**
     * @return The standing animation id.
     */
    public int getStanding() {
        return standing;
    }

    /**
     * @return The walking animation id.
     */
    public int getWalking() {
        return walking;
    }

    /**
     * @return The running animation id.
     */
    public int getRunning() {
        return running;
    }
}