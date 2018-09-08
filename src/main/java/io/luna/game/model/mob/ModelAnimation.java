package io.luna.game.model.mob;

/**
 * A model representing the default animation of a {@link Player} model.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class ModelAnimation {

    /**
     * A builder that builds {@link ModelAnimation} instances.
     */
    public static final class ModelAnimationBuilder {

        /**
         * The standing animation.
         */
        private int standingId = 808;

        /**
         * The standing and turning animation.
         */
        private int standingTurnId = 823;

        /**
         * The walking animation.
         */
        private int walkingId = 819;

        /**
         * The 180 degree turning animation.
         */
        private int turning180DegreesId = 820;

        /**
         * The 90 degree clockwise turning animation.
         */
        private int turning90DegreesCwId = 821;

        /**
         * The 90 degree counterclockwise turning animation.
         */
        private int turning90DegreesCcwId = 822;

        /**
         * The running animation.
         */
        private int runningId = 824;


        /**
         * Sets the standing animation.
         *
         * @param standingId The new standing animation.
         * @return This builder, for chaining.
         */
        public ModelAnimationBuilder setStandingId(int standingId) {
            this.standingId = standingId;
            return this;
        }

        /**
         * Sets the standing and turning animation.
         *
         * @param standingTurnId The new standing and turning animation.
         * @return This builder, for chaining.
         */
        public ModelAnimationBuilder setStandingTurnId(int standingTurnId) {
            this.standingTurnId = standingTurnId;
            return this;
        }

        /**
         * Sets the walking animation.
         *
         * @param walkingId The new walking animation.
         * @return This builder, for chaining.
         */
        public ModelAnimationBuilder setWalkingId(int walkingId) {
            this.walkingId = walkingId;
            return this;
        }

        /**
         * Sets the 180 degree turning animation.
         *
         * @param turning180DegreesId The new 180 degree turning animation.
         * @return This builder, for chaining.
         */
        public ModelAnimationBuilder setTurning180DegreesId(int turning180DegreesId) {
            this.turning180DegreesId = turning180DegreesId;
            return this;
        }

        /**
         * Sets the 90 degree clockwise turning animation.
         *
         * @param turning90DegreesCwId The new 90 degree clockwise turning animation.
         * @return This builder, for chaining.
         */
        public ModelAnimationBuilder setTurning90DegreesCwId(int turning90DegreesCwId) {
            this.turning90DegreesCwId = turning90DegreesCwId;
            return this;
        }

        /**
         * Sets the 90 degree counterclockwise turning animation
         *
         * @param turning90DegreesCcwId The new 90 degree counterclockwise turning animation.
         * @return This builder, for chaining.
         */
        public ModelAnimationBuilder setTurning90DegreesCcwId(int turning90DegreesCcwId) {
            this.turning90DegreesCcwId = turning90DegreesCcwId;
            return this;
        }

        /**
         * Sets the running animation.
         *
         * @param runningId The new running animation.
         * @return This builder, for chaining.
         */
        public ModelAnimationBuilder setRunningId(int runningId) {
            this.runningId = runningId;
            return this;
        }

        /**
         * Builds the model animation using the backing values.
         *
         * @return The new model animation.
         */
        public ModelAnimation build() {
            return new ModelAnimation(standingId, standingTurnId, walkingId, turning180DegreesId,
                    turning90DegreesCwId, turning90DegreesCcwId, runningId);
        }
    }

    /**
     * The default model animation.
     */
    public static final ModelAnimation DEFAULT = new ModelAnimationBuilder().build();

    /**
     * The standing animation.
     */
    private final int standingId;

    /**
     * The standing and turning animation.
     */
    private final int standingTurnId;

    /**
     * The walking animation.
     */
    private final int walkingId;

    /**
     * The 180 degree turning animation.
     */
    private final int turning180DegreesId;

    /**
     * The 90 degree clockwise turning animation.
     */
    private final int turning90DegreesCwId;

    /**
     * The 90 degree counterclockwise turning animation.
     */
    private final int turning90DegreesCcwId;

    /**
     * The running animation.
     */
    private final int runningId;

    /**
     * Creates a new {@link ModelAnimation}.
     *
     * @param standingId The standing animation.
     * @param standingTurnId The standing and turning animation.
     * @param walkingId The walking animation.
     * @param turning180DegreesId The 180 degree turning animation.
     * @param turning90DegreesCwId The 90 degree clockwise turning animation.
     * @param turning90DegreesCcwId The 90 degree counterclockwise turning animation.
     * @param runningId The running animation.
     */
    private ModelAnimation(int standingId, int standingTurnId, int walkingId, int turning180DegreesId,
                          int turning90DegreesCwId, int turning90DegreesCcwId, int runningId) {
        this.standingId = standingId;
        this.standingTurnId = standingTurnId;
        this.walkingId = walkingId;
        this.turning180DegreesId = turning180DegreesId;
        this.turning90DegreesCwId = turning90DegreesCwId;
        this.turning90DegreesCcwId = turning90DegreesCcwId;
        this.runningId = runningId;
    }

    /**
     * @return The standing animation.
     */
    public int getStandingId() {
        return standingId;
    }

    /**
     * @return The standing and turning animation.
     */
    public int getStandingTurnId() {
        return standingTurnId;
    }

    /**
     * @return The walking animation.
     */
    public int getWalkingId() {
        return walkingId;
    }

    /**
     * @return The 180 degree turning animation.
     */
    public int getTurning180DegreesId() {
        return turning180DegreesId;
    }

    /**
     * @return The 90 degree clockwise turning animation.
     */
    public int getTurning90DegreesCwId() {
        return turning90DegreesCwId;
    }

    /**
     * @return The 90 degree counterclockwise turning animation.
     */
    public int getTurning90DegreesCcwId() {
        return turning90DegreesCcwId;
    }

    /**
     * @return The running animation.
     */
    public int getRunningId() {
        return runningId;
    }
}