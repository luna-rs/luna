package io.luna.game.model.mob;

import io.luna.game.model.Direction;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.net.msg.out.ConfigMessageWriter;

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
public final class WalkingQueue {

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
     * The base amount of energy it takes to move across one tile.
     */
    private static final double DRAIN_PER_TILE = 0.117;

    /**
     * The base value of run energy restored per tick.
     */
    private static final double RESTORE_PER_TICK = 0.096;

    /**
     * A deque of current steps.
     */
    private final Deque<Step> current = new ArrayDeque<>();

    /**
     * A deque of previous steps.
     */
    private final Deque<Step> previous = new ArrayDeque<>();

    /**
     * The mob.
     */
    private final Mob mob;

    /**
     * If movement is locked.
     */
    private boolean locked;

    /**
     * If the mob is running.
     */
    private boolean running;

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
        Step current = new Step(mob.getPosition());

        Direction walkingDirection = Direction.NONE;
        Direction runningDirection = Direction.NONE;

        boolean restoreEnergy = true;

        if (running) {
            runningPath = true;
        }

        Step next = this.current.poll();
        if (next != null) {
            previous.add(next);
            walkingDirection = Direction.between(current, next);
            current = next;

            if (runningPath) {
                next = decrementRunEnergy() ? this.current.poll() : null;

                if (next != null) {
                    restoreEnergy = false;
                    previous.add(next);
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
     * Adds an initial step to this walking queue.
     */
    public void addFirst(Step step) {
        current.clear();
        runningPath = false;

        Queue<Step> backtrack = new ArrayDeque<>();
        while (!previous.isEmpty()) {
            Step prev = previous.pollLast();
            backtrack.add(prev);

            if (prev.equals(step)) {
                backtrack.forEach(this::add);
                previous.clear();
                return;
            }
        }
        previous.clear();

        add(step);
    }

    /**
     * Adds a non-initial step to this walking queue.
     */
    public void add(Step next) {
        Step last = current.peekLast();
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
            current.add(new Step(nextX - deltaX, nextY - deltaY));
        }
    }

    /**
     * Clears the current and previous steps.
     */
    public void clear() {
        current.clear();
        previous.clear();
    }

    /**
     * A function that implements an algorithm to deplete run energy. Returns {@code false} if the player can no
     * longer run.
     */
    private boolean decrementRunEnergy() {
        Player player = (Player) mob;

        double runEnergy = player.getRunEnergy();
        if (runEnergy <= 0) {
            running = false;
            runningPath = false;
            player.queue(new ConfigMessageWriter(173, 0));
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
     * A function that implements an algorithm to restore run energy.
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
     * Returns the amount of remaining steps.
     */
    public int remaining() {
        return current.size();
    }

    /**
     * Returns whether or not there are remaining steps.
     */
    public boolean empty() {
        return remaining() == 0;
    }

    /**
     * @return {@code true} if the mob is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Sets if the mob is running.
     */
    public void setRunning(boolean running) {
        checkState(mob.getType() == EntityType.PLAYER, "cannot change running value for NPCs");
        this.running = running;
    }

    /**
     * @return {@code true} if the current path is a running path.
     */
    public boolean isRunningPath() {
        return runningPath;
    }

    /**
     * Sets if the current path is a running path.
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
     */
    public void setLocked(boolean locked) {
        this.locked = locked;
    }
}
