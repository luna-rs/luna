package io.luna.game.model.mob;

import com.google.common.base.MoreObjects;
import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionManager;
import io.luna.game.model.path.AStarPathfindingAlgorithm;
import io.luna.game.model.path.EuclideanHeuristic;
import io.luna.game.model.path.SimplePathfindingAlgorithm;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;

/**
 * A model representing an implementation of the walking queue.
 *
 * @author lare96
 * @author Graham
 */
public final class WalkingQueue {

    // TODO Clean up, rewrite

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

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("x", x)
                    .add("y", y)
                    .toString();
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
    private final Deque<Step> current = new ArrayDeque<>();

    /**
     * A deque of previous steps.
     */
    private final Deque<Step> previous = new ArrayDeque<>();

    private final CollisionManager collisionManager;
    private final SimplePathfindingAlgorithm simplePathfinder;
    private final AStarPathfindingAlgorithm astarPathfinder;

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
        collisionManager = mob.getWorld().getCollisionManager();
        simplePathfinder = new SimplePathfindingAlgorithm(collisionManager);
        astarPathfinder = new AStarPathfindingAlgorithm(collisionManager, new EuclideanHeuristic());
    }

    /**
     * A function that determines your next walking and running directions, as well as your new position after
     * taking steps.
     */
    public void process() {
        // TODO clean up function, traversable checks don't work
        Step current = new Step(mob.getPosition());

        Direction walkingDirection = Direction.NONE;
        Direction runningDirection = Direction.NONE;

        boolean restoreEnergy = true;
        Step next = this.current.poll();
        if (next != null) {
            walkingDirection = Direction.between(current, next);
            boolean blocked = false;//!collisionManager.traversable(mob.getPosition(), EntityType.NPC, walkingDirection);
            if (blocked) {
                walkingDirection = Direction.NONE;
                clear();
            } else {
                previous.add(next);
                current = next;
                mob.setLastDirection(walkingDirection);

                if (runningPath) {
                    next = decrementRunEnergy() ? this.current.poll() : null;
                    if (next != null) {
                        runningDirection = Direction.between(current, next);
                        blocked = false;//!collisionManager.traversable(mob.getPosition(), EntityType.NPC, runningDirection);
                        if (blocked) {
                            runningDirection = Direction.NONE;
                            clear();
                        } else {
                            restoreEnergy = false;
                            previous.add(next);
                            current = next;
                            mob.setLastDirection(runningDirection);
                        }
                    } else {
                        runningPath = false;
                    }
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


    public void walk(Position target) {
        Deque<Position> path = mob.getType() == EntityType.PLAYER ? astarPathfinder.find(mob.getPosition(), target) :
                       simplePathfinder.find(mob.getPosition(), target);
        int size = path.size();
        if (size == 1) {
            addFirst(new Step(path.poll()));
        } else if (size > 1) {
            addFirst(new Step(path.poll()));
            for (; ; ) {
                Position position = path.poll();
                if (position == null) {
                    break;
                }
                add(new Step(position));
            }
        }
    }

    public void walk(Entity target, Optional<Direction> facing) {
        int sizeX = mob.sizeX();
        int sizeY = mob.sizeY();
        int targetSizeX = target.sizeX();
        int targetSizeY = target.sizeY();
        Position position = mob.getPosition();
        int height = position.getZ();
        Position targetPosition = target.getPosition();

        Direction direction = facing.orElse(Direction.between(position, targetPosition));
        int dx = direction.getTranslation().getX();
        int dy = direction.getTranslation().getY();

        int targetX = dx <= 0 ? targetPosition.getX() : targetPosition.getX() + targetSizeX - 1;
        int targetY = dy <= 0 ? targetPosition.getY() : targetPosition.getY() + targetSizeY - 1;
        int offsetX;
        if (dx < 0) {
            offsetX = -sizeX;
        } else if (dx > 0) {
            offsetX = 1;
        } else {
            offsetX = 0;
        }
        int offsetY;
        if (dy < 0) {
            offsetY = -sizeY;
        } else if (dy > 0) {
            offsetY = 1;
        } else {
            offsetY = 0;
        }
        walk(new Position(targetX + offsetX, targetY + offsetY, height));
    }

    public void walk(Entity target) {
        if (target instanceof Mob) {
            Mob targetMob = (Mob) target;
            walk(target, Optional.of(targetMob.getLastDirection()));
        } else {
            walk(target, Optional.empty());
        }
    }

    public void walkBehind(Mob target) {
        Direction direction = target.getLastDirection().opposite();
        walk(target, Optional.of(direction));
    }

    /**
     * Adds an initial step to this walking queue.
     *
     * @param step The step to add.
     */
    public void addFirst(Step step) {
        current.clear();
        runningPath = false;

        Queue<Step> backtrack = new ArrayDeque<>();
        for (; ; ) {
            Step prev = previous.pollLast();
            if (prev == null) {
                break;
            }
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
     *
     * @param next The step to add.
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
     * A function that implements an algorithm to deplete run energy.
     *
     * @return {@code false} if the player can no longer run.
     */
    private boolean decrementRunEnergy() {
        if (mob.getType() != EntityType.PLAYER) {
            return true;
        }
        Player player = (Player) mob;
        double totalWeight = player.getWeight();
        double energyReduction = 0.117 * 2 * Math
                .pow(Math.E, 0.0027725887222397812376689284858327062723020005374410 * totalWeight);
        double newValue = player.getRunEnergy() - energyReduction;
        if (newValue <= 0.0) {
            player.setRunEnergy(0.0, true);
            player.setRunning(false);
            runningPath = false;
            return false;
        }
        player.setRunEnergy(newValue, true);
        return true;
    }

    /**
     * A function that implements an algorithm to restore run energy.
     */
    private void incrementRunEnergy() {
        if (mob.getType() != EntityType.PLAYER)
            return;
        Player player = mob.asPlr();

        double runEnergy = player.getRunEnergy();
        if (runEnergy >= 100.0) {
            return;
        }

        double agilityLevel = player.skill(Skill.AGILITY).getLevel();
        double energyRestoration = 0.096 * Math
                .pow(Math.E, 0.0162569486104454583293005993255170468638949631744294 * agilityLevel);
        double newValue = runEnergy + energyRestoration;
        newValue = Math.min(newValue, 100.0);

        player.setRunEnergy(newValue, true);
    }

    /**
     * Returns the current size of the walking queue.
     *
     * @return The amount of remaining steps.
     */
    public int getRemainingSteps() {
        return current.size();
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
        if (locked) {
            clear();
        }
        this.locked = locked;
    }

    public AStarPathfindingAlgorithm getAstarPathfinder() {
        return astarPathfinder;
    }
}
