package io.luna.game.model.mob;

import io.luna.game.model.Direction;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Queue;

import static com.google.common.base.Preconditions.checkState;

/**
 * A model representing an implementation of the walking queue.
 *
 * @author lare96 <http://github.org/lare96>
 * @author Graham
 */
public class WalkingQueue {

    // TODO Rewrite

    /**
     * A model representing a step in the walking queue.
     */
    public static final class Step {

        /**
         * The x coordinate.
         */
        private final int x;

        /**
         * The y coordinate.
         */
        private final int y;

        /**
         * Creates a new {@link Step}.
         *
         * @param x The x coordinate.
         * @param y The y coordinate.
         */
        public Step(int x, int y) {
            checkState(x >= 0, "x < 0");
            checkState(y >= 0, "y < 0");

            this.x = x;
            this.y = y;
        }

        /**
         * Creates a new {@link Step}.
         *
         * @param position The position.
         */
        public Step(Position position) {
            this(position.getX(), position.getY());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof Step) {
                Step other = (Step) obj;
                return x == other.x && y == other.y;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }

        /**
         * @return The x coordinate.
         */
        public int getX() {
            return x;
        }

        /**
         * @return The y coordinate.
         */
        public int getY() {
            return y;
        }
    }

    /**
     * A deque of current steps.
     */
    private final Deque<Step> currentQueue = new ArrayDeque<>();

    /**
     * A deque of previous steps.
     */
    private final Deque<Step> previousQueue = new ArrayDeque<>();

    /**
     * The mob.
     */
    private final Mob mob;

    /**
     * If movement is locked.
     */
    private boolean locked;

    /**
     * If the current path is a running path.
     */
    private boolean runningPath;
    /**
     * Create a new {@link WalkingQueue}.
     *
     * @param mob The mob.
     */
    public WalkingQueue(Mob mob) {
        this.mob = mob;
    }

    /**
     * A function that determines your next walking and running directions, as well as your new position after
     * taking steps.
     */
    public void process() {
        // TODO clean up function
        Step currentStep = new Step(mob.getPosition());

        Direction walkingDirection = Direction.NONE;
        Direction runningDirection = Direction.NONE;

        boolean restoreEnergy = true;

        Step nextStep = this.currentQueue.poll();
        if (nextStep != null) {
            previousQueue.add(nextStep);
            walkingDirection = Direction.between(currentStep, nextStep);
            currentStep = nextStep;

            if (mob.getType() == EntityType.PLAYER) {
                Player player = mob.asPlr();
                if (player.isRunning() || runningPath) {
                    if (player.hasEnoughEnergyToRun()) {
                        useEnergy(player);
                        updateEnergy();
                        nextStep = this.currentQueue.poll();
                    } else {
                        nextStep = null;
                    }
                    if (nextStep != null) {
                        restoreEnergy = false;
                        previousQueue.add(nextStep);
                        runningDirection = Direction.between(currentStep, nextStep);
                        currentStep = nextStep;
                    }
                }
            }

            Position newPosition = new Position(currentStep.getX(), currentStep.getY(), mob.getPosition().getZ());
            mob.setPosition(newPosition);
        }

        if (restoreEnergy && mob.getType() == EntityType.PLAYER) {
            Player player = mob.asPlr();
            incrementRunEnergy(player);
            updateEnergy();
        }

        mob.setWalkingDirection(walkingDirection);
        mob.setRunningDirection(runningDirection);
    }

    void updateEnergy(){
        mob.asPlr().updateRunEnergy();
    }

    /**
     * Depletes a player's energy. If the player doesn't have enough energy, the player will stop running.
     */
    void useEnergy(Player player) {
        double energyLeft = player.runEnergyAfterReduction();
        if (player.hasEnoughEnergyToRun()) {
            player.setRunEnergy(energyLeft);
        } else {
            player.setRunEnergy(0.0);
            player.setRunning(false);
            runningPath = false;
        }
    }

    /**
     * Walks to the specified offsets.
     *
     * @param offsetX The {@code x} offset.
     * @param offsetY The {@code y} offset.
     */
    public void walk(int offsetX, int offsetY) {
        var newPosition = mob.getPosition().translate(offsetX, offsetY);
        addFirst(new Step(newPosition));
    }

    /**
     * Walks to the specified {@code firstPos} and then {@code otherPos}.
     *
     * @param firstPos The first position.
     * @param otherPos The other positions.
     */
    public void walk(Position firstPos, Position... otherPos) {
        addFirst(new Step(firstPos));
        for (var nextPos : otherPos) {
            add(new Step(nextPos));
        }
    }

    /**
     * Adds an initial step to this walking queue.
     *
     * @param step The step to add.
     */
    public void addFirst(Step step) {
        currentQueue.clear();
        runningPath = false;
        Queue<Step> backtrack = new ArrayDeque<>();
        for (; ; ) {
            Step prev = previousQueue.pollLast();
            if (prev == null) {
                break;
            }
            backtrack.add(prev);
            if (prev.equals(step)) {
                backtrack.forEach(this::add);
                previousQueue.clear();
                return;
            }
        }
        previousQueue.clear();

        add(step);
    }

    /**
     * Adds a non-initial step to this walking queue.
     *
     * @param next The step to add.
     */
    public void add(Step next) {
        Step last = currentQueue.peekLast();
        if (last == null) {
            last = new Step(mob.getPosition());
        }

        int nextX = next.getX();
        int nextY = next.getY();
        int deltaX = nextX - last.getX();
        int deltaY = nextY - last.getY();

        int max = Math.max(Math.abs(deltaX), Math.abs(deltaY));

        for (int count = 0; count < max; count++) {
            if (deltaX < 0) {
                deltaX++;
            } else if (deltaX > 0) {
                deltaX--;
            }

            if (deltaY < 0) {
                deltaY++;
            } else if (deltaY > 0) {
                deltaY--;
            }
            currentQueue.add(new Step(nextX - deltaX, nextY - deltaY));
        }
    }

    /**
     * Clears the current and previous steps.
     */
    public void clear() {
        currentQueue.clear();
        previousQueue.clear();
    }

    /**
     * A function that implements an algorithm to restore run energy.
     */
    void incrementRunEnergy(Player player) {

        double runEnergy = player.getRunEnergy();
        if (runEnergy >= 100.0) {
            return;
        }

        double agilityLevel = player.skill(Skill.AGILITY).getLevel();
        double energyRestoration = 0.096 * Math
                .pow(Math.E, 0.0162569486104454583293005993255170468638949631744294 * agilityLevel);
        double newValue = runEnergy + energyRestoration;
        newValue = Math.min(newValue, 100.0);

        player.setRunEnergy(newValue);
    }

    /**
     * Returns the current size of the walking queue.
     *
     * @return The amount of remaining steps.
     */
    public int getRemainingSteps() {
        return currentQueue.size();
    }

    /**
     * Returns whether or not there are remaining steps.
     *
     * @return {@code true} if this walking queue is empty.
     */
    public boolean isEmpty() {
        return getRemainingSteps() == 0;
    }

    /**
     * Sets if the current path is a running path.
     *
     * @param runningPath The new value.
     */
    public void setRunningPath(boolean runningPath) {
        checkState(mob.getType() == EntityType.PLAYER, "cannot change running value for NPCs");
        this.runningPath = runningPath;
    }

    /**
     * @return {@code true} if movement is locked.
     */
    public boolean isLocked() {
        return locked;
    }

    /**
     * Sets if movement is locked.
     *
     * @param locked The new value.
     */
    public void setLocked(boolean locked) {
        this.locked = locked;
    }
}
