package io.luna.game.model.mob.block;

import io.luna.game.model.mob.Player;

/**
 * Encapsulates the default animation set for a {@link Player}'s render model.
 * <p>
 * A {@code ModelAnimation} describes the base movement animations used by the client: standing, walking, running, and
 * various turning animations. These are typically used as the player's idle/movement defaults and may be overridden
 * by equipment or transformation logic.
 * </p>
 *
 * @author lare96
 */
public final class PlayerModelAnimation {

    /**
     * A builder for constructing {@link PlayerModelAnimation} instances with custom animation identifiers.
     * <p>
     * All fields are initialized to the default #377 player animation ids and may be selectively overridden through
     * the provided setters.
     * </p>
     */
    public static final class ModelAnimationBuilder {

        /**
         * The standing (idle) animation id.
         */
        private int standingId = 0x328;

        /**
         * The standing-and-turning animation id.
         */
        private int standingTurnId = 0x337;

        /**
         * The walking animation id.
         */
        private int walkingId = 0x333;

        /**
         * The 180-degree turning animation id.
         */
        private int turning180DegreesId = 0x334;

        /**
         * The 90-degree clockwise turning animation id.
         */
        private int turning90DegreesCwId = 0x335;

        /**
         * The 90-degree counter-clockwise turning animation id.
         */
        private int turning90DegreesCcwId = 0x336;

        /**
         * The running animation id.
         */
        private int runningId = 0x338;

        /**
         * Sets the standing (idle) animation id.
         *
         * @param standingId The new standing animation id.
         * @return This builder instance for method chaining.
         */
        public ModelAnimationBuilder setStandingId(int standingId) {
            this.standingId = standingId;
            return this;
        }

        /**
         * Sets the standing-and-turning animation id.
         *
         * @param standingTurnId The new standing-and-turning animation id.
         * @return This builder instance for method chaining.
         */
        public ModelAnimationBuilder setStandingTurnId(int standingTurnId) {
            this.standingTurnId = standingTurnId;
            return this;
        }

        /**
         * Sets the walking animation id.
         *
         * @param walkingId The new walking animation id.
         * @return This builder instance for method chaining.
         */
        public ModelAnimationBuilder setWalkingId(int walkingId) {
            this.walkingId = walkingId;
            return this;
        }

        /**
         * Sets the 180-degree turning animation id.
         *
         * @param turning180DegreesId The new 180-degree turning animation id.
         * @return This builder instance for method chaining.
         */
        public ModelAnimationBuilder setTurning180DegreesId(int turning180DegreesId) {
            this.turning180DegreesId = turning180DegreesId;
            return this;
        }

        /**
         * Sets the 90-degree clockwise turning animation id.
         *
         * @param turning90DegreesCwId The new 90-degree clockwise turning animation id.
         * @return This builder instance for method chaining.
         */
        public ModelAnimationBuilder setTurning90DegreesCwId(int turning90DegreesCwId) {
            this.turning90DegreesCwId = turning90DegreesCwId;
            return this;
        }

        /**
         * Sets the 90-degree counter-clockwise turning animation id.
         *
         * @param turning90DegreesCcwId The new 90-degree counter-clockwise turning animation id.
         * @return This builder instance for method chaining.
         */
        public ModelAnimationBuilder setTurning90DegreesCcwId(int turning90DegreesCcwId) {
            this.turning90DegreesCcwId = turning90DegreesCcwId;
            return this;
        }

        /**
         * Sets the running animation id.
         *
         * @param runningId The new running animation id.
         * @return This builder instance for method chaining.
         */
        public ModelAnimationBuilder setRunningId(int runningId) {
            this.runningId = runningId;
            return this;
        }

        /**
         * Builds a new {@link PlayerModelAnimation} using the currently configured animation ids.
         *
         * @return A new immutable {@code ModelAnimation} instance.
         */
        public PlayerModelAnimation build() {
            return new PlayerModelAnimation(standingId, standingTurnId, walkingId, turning180DegreesId, turning90DegreesCwId,
                    turning90DegreesCcwId, runningId);
        }
    }

    /**
     * The default player model animation set for the base #377 character.
     * <p>
     * This constant can be used as a safe fallback when no equipment-specific animation overrides are present.
     * </p>
     */
    public static final PlayerModelAnimation DEFAULT = new ModelAnimationBuilder().build();

    /**
     * Standing (idle) animation id.
     */
    private final int standingId;

    /**
     * Standing-and-turning animation id.
     */
    private final int standingTurnId;

    /**
     * Walking animation id.
     */
    private final int walkingId;

    /**
     * 180-degree turning animation id.
     */
    private final int turning180DegreesId;

    /**
     * 90-degree clockwise turning animation id.
     */
    private final int turningRightId;

    /**
     * 90-degree counter-clockwise turning animation id.
     */
    private final int turningLeftId;

    /**
     * Running animation id.
     */
    private final int runningId;

    /**
     * Creates a new {@link PlayerModelAnimation}.
     *
     * @param standingId Standing (idle) animation id.
     * @param standingTurnId Standing-and-turning animation id.
     * @param walkingId Walking animation id.
     * @param turning180DegreesId 180-degree turning animation id.
     * @param turningRightId 90-degree clockwise turning animation id.
     * @param turningLeftId 90-degree counter-clockwise turning animation id.
     * @param runningId Running animation id.
     */
    private PlayerModelAnimation(int standingId, int standingTurnId, int walkingId, int turning180DegreesId,
                                 int turningRightId, int turningLeftId, int runningId) {
        this.standingId = standingId;
        this.standingTurnId = standingTurnId;
        this.walkingId = walkingId;
        this.turning180DegreesId = turning180DegreesId;
        this.turningRightId = turningRightId;
        this.turningLeftId = turningLeftId;
        this.runningId = runningId;
    }

    /**
     * Returns the standing (idle) animation id.
     *
     * @return The standing animation id.
     */
    public int getStandingId() {
        return standingId;
    }

    /**
     * Returns the standing-and-turning animation id.
     *
     * @return The standing-turn animation id.
     */
    public int getStandingTurnId() {
        return standingTurnId;
    }

    /**
     * Returns the walking animation id.
     *
     * @return The walking animation id.
     */
    public int getWalkingId() {
        return walkingId;
    }

    /**
     * Returns the 180-degree turning animation id.
     *
     * @return The 180-degree turning animation id.
     */
    public int getTurning180DegreesId() {
        return turning180DegreesId;
    }

    /**
     * Returns the 90-degree clockwise turning animation id.
     *
     * @return The right-turn animation id.
     */
    public int getTurningRightId() {
        return turningRightId;
    }

    /**
     * Returns the 90-degree counter-clockwise turning animation id.
     *
     * @return The left-turn animation id.
     */
    public int getTurningLeftId() {
        return turningLeftId;
    }

    /**
     * Returns the running animation id.
     *
     * @return The running animation id.
     */
    public int getRunningId() {
        return runningId;
    }
}
