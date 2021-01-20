package io.luna.game.model.mob;

/**
 * A {@link WalkingQueue} designed for testing purposes only.
 */
class NullWalkingQueue extends WalkingQueue {
    /**
     * Create a new {@link WalkingQueue}.
     *
     * @param mob The mob.
     */
    NullWalkingQueue(Mob mob) {
        super(mob);
    }

    @Override
    void updateEnergy() {
        return;
    }
}
