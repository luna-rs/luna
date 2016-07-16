package io.luna.game.model.mobile;

import io.luna.game.model.Direction;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.WorldSynchronizer;
import io.luna.net.msg.out.StateMessageWriter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Queue;

import static com.google.common.base.Preconditions.checkState;

/**
 * An implementation of the walking queue, assigned to all {@link MobileEntity}s.
 *
 * @author lare96 <http://github.org/lare96>
 * @author Graham
 */
public final class WalkingQueue {

    /**
     * A POJO representing a single step in a {@code WalkingQueue}.
     */
    public static final class Step {

        /**
         * The {@code x} coordinate.
         */
        private final int x;

        /**
         * The {@code y} coordinate.
         */
        private final int y;

        /**
         * Creates a new {@link Step}.
         *
         * @param x The {@code x} coordinate.
         * @param y The {@code x} coordinate.
         */
        public Step(int x, int y) {
            checkState(x >= 0, "x < 0");
            checkState(y >= 0, "y < 0");

            this.x = x;
            this.y = y;
        }

        /**
         * Creates a new {@link Step} from {@code position}.
         *
         * @param position The {@link Position} to create the new {@code Step} from.
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
         * @return The {@code x} coordinate.
         */
        public int getX() {
            return x;
        }

        /**
         * @return The {@code y} coordinate.
         */
        public int getY() {
            return y;
        }
    }

    /**
     * The base value of run energy it takes to move across a single tile. Increasing this number will make run energy
     * deplete faster, decreasing it will make it deplete slower.
     */
    private static final double DRAIN_PER_TILE = 0.117;

    /**
     * The base value of run energy restored per tick. Increasing this number will make run energy restore faster, decreasing
     * it will make it restore slower.
     */
    private static final double RESTORE_PER_TICK = 0.096;

    /**
     * A {@link Deque} of the current {@link Step}s.
     */
    private final Deque<Step> steps = new ArrayDeque<>();

    /**
     * A {@link Deque} of the previous {@link Step}s.
     */
    private final Deque<Step> previousSteps = new ArrayDeque<>();

    /**
     * The {@link MobileEntity} assigned to this walking queue.
     */
    private final MobileEntity mob;

    /**
     * If the {@link MobileEntity} assigned to this walking queue is running.
     */
    private boolean running;

    /**
     * If the current path is a running path.
     */
    private boolean runningPath;

    /**
     * Create a new {@link WalkingQueue}.
     *
     * @param mob The {@link MobileEntity} assigned to this walking queue.
     */
    public WalkingQueue(MobileEntity mob) {
        this.mob = mob;
    }

    /**
     * Called every tick by the {@link WorldSynchronizer}, this method determines your next walking and running directions as
     * well as your new position after taking one step (or two steps, if running).
     */
    public void process() {
        Step current = new Step(mob.getPosition());

        Direction walkingDirection = Direction.NONE;
        Direction runningDirection = Direction.NONE;

        boolean restoreEnergy = true;

        if (running) {
            runningPath = true;
        }

        Step next = steps.poll();
        if (next != null) {
            previousSteps.add(next);
            walkingDirection = Direction.between(current, next);
            current = next;

            if (runningPath) {
                next = decrementRunEnergy() ? steps.poll() : null;

                if (next != null) {
                    restoreEnergy = false;
                    previousSteps.add(next);
                    runningDirection = Direction.between(current, next);
                    current = next;
                } else {
                    runningPath = false;
                }
            }
        }

        if (restoreEnergy) {
            incrementRunEnergy();
        }

        mob.setWalkingDirection(walkingDirection);
        mob.setRunningDirection(runningDirection);

        Position newPosition = new Position(current.getX(), current.getY(), mob.getPosition().getZ());
        mob.setPosition(newPosition);
    }

    /**
     * Adds a first {@link Step} to this walking queue.
     *
     * @param step The {@code Step} to add.
     */
    public void addFirst(Step step) {
        steps.clear();
        runningPath = false;

        Queue<Step> backtrack = new ArrayDeque<>();
        while (!previousSteps.isEmpty()) {
            Step prev = previousSteps.pollLast();
            backtrack.add(prev);

            if (prev.equals(step)) {
                backtrack.forEach(this::add);
                previousSteps.clear();
                return;
            }
        }
        previousSteps.clear();

        add(step);
    }

    /**
     * Adds a {@link Step} that isn't the first step, to this walking queue.
     *
     * @param next The next {@code Step} to add.
     */
    public void add(Step next) {
        Step last = steps.peekLast();
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
            steps.add(new Step(nextX - deltaX, nextY - deltaY));
        }
    }

    /**
     * Clears the backing {@link Deque}s in this walking queue, effectively stopping the movement of the assigned {@link
     * MobileEntity}.
     */
    public void clear() {
        steps.clear();
        previousSteps.clear();
    }

    /**
     * Implements an algorithm that will deplete run energy. Will return {@code false} if no run energy is available to
     * deplete, and {@code true} otherwise.
     */
    private boolean decrementRunEnergy() {
        Player player = (Player) mob;

        double runEnergy = player.getRunEnergy();
        if (runEnergy <= 0) {
            running = false;
            runningPath = false;
            player.queue(new StateMessageWriter(173, 0));
            return false;
        }

        double totalWeight = player.getWeight();
        double energyReduction = DRAIN_PER_TILE * 2 * Math
            .pow(Math.E, 0.0027725887222397812376689284858327062723020005374410 * totalWeight);
        double newValue = runEnergy - energyReduction;
        newValue = newValue < 0.0 ? 0.0 : newValue;

        player.setRunEnergy(newValue);
        return true;
    }

    /**
     * Implements an algorithm that will restore run energy. Will return {@code false} if no run energy is available to
     * deplete, and {@code true} otherwise.
     */
    private void incrementRunEnergy() {
        Player player = (Player) mob;

        double runEnergy = player.getRunEnergy();
        if (runEnergy >= 100.0) {
            return;
        }

        double agilityLevel = player.skill(Skill.AGILITY).getLevel();
        double energyRestoration = RESTORE_PER_TICK * Math
            .pow(Math.E, 0.0162569486104454583293005993255170468638949631744294 * agilityLevel);
        double newValue = runEnergy + energyRestoration;
        newValue = newValue > 100.0 ? 100.0 : newValue;

        player.setRunEnergy(newValue);
    }

    /**
     * @return The amount of {@link Step}s remaining in this walking queue.
     */
    public int remaining() {
        return steps.size();
    }

    /**
     * @return {@code true} if this walking queue is empty, {@code false} otherwise.
     */
    public boolean empty() {
        return remaining() == 0;
    }

    /**
     * @return {@code true} if this WalkingQueue has running enabled.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Sets the running flag status of this WalkingQueue.
     */
    public void setRunning(boolean running) {
        checkState(mob.type() == EntityType.PLAYER, "cannot change running value for NPCs");
        this.running = running;
    }

    /**
     * @return {@code true} if the current path is a running path, {@code false} otherwise.
     */
    public boolean isRunningPath() {
        return runningPath;
    }

    /**
     * Sets if the current path is a running path.
     */
    public void setRunningPath(boolean runningPath) {
        checkState(mob.type() == EntityType.PLAYER, "cannot change running value for NPCs");
        this.runningPath = runningPath;
    }

}
