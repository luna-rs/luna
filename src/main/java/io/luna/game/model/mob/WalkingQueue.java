package io.luna.game.model.mob;

import com.google.common.util.concurrent.ListenableFuture;
import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.Region;
import io.luna.game.model.collision.CollisionManager;
import io.luna.game.model.path.AStarPathfindingAlgorithm;
import io.luna.game.model.path.EuclideanHeuristic;
import io.luna.game.model.path.PathfindingAlgorithm;
import io.luna.game.model.path.SimplePathfindingAlgorithm;
import io.luna.math.Vector2;
import io.luna.util.RandomUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * A model representing an implementation of the walking queue.
 *
 * @author lare96
 * @author Graham
 */
public final class WalkingQueue {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * A deque of current steps.
     */
    private final Deque<Vector2> current = new ArrayDeque<>();

    /**
     * A deque of previous steps.
     */
    private final Deque<Vector2> previous = new ArrayDeque<>();

    /**
     * The collision manager.
     */
    private final CollisionManager collisionManager;
    private final PathfindingAlgorithm pathfindingAlgorithm;

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
     * The current lazy walking task result.
     */
    private ListenableFuture<?> lazyWalkResult;

    /**
     * Create a new {@link WalkingQueue}.
     *
     * @param mob The mob.
     */
    public WalkingQueue(Mob mob) {
        this.mob = mob;
        collisionManager = mob.getWorld().getCollisionManager();
        if (mob instanceof Player) {
            this.pathfindingAlgorithm = new AStarPathfindingAlgorithm(collisionManager, new EuclideanHeuristic());
        } else {
            this.pathfindingAlgorithm = new SimplePathfindingAlgorithm(collisionManager);
        }
    }

    /**
     * Forces the mob to walk {@code 1} square in a random traversable NESW direction. If none of the directions are
     * traversable, the mob will not move.
     */
    public void walkRandomDirection() {
        CollisionManager collision = mob.getWorld().getCollisionManager();
        Direction[] directions = RandomUtils.shuffle(Arrays.copyOf(Direction.NESW, Direction.NESW.length));
        for (Direction dir : directions) {
            if (collision.traversable(mob.getPosition(), EntityType.NPC, dir)) {
                walk(mob.getPosition().translate(1, dir));
                break;
            }
        }
    }

    /**
     * A function that determines your next walking and running directions, as well as your new position after
     * taking steps.
     */
    public void process() {
        // TODO clean up function, traversable checks don't work
        // TODO retest traversable checks, figure out a better way for runningPath to work thats less clunky
        if(mob instanceof Npc && mob.asNpc().isStationary()) {
            return;
        }
        Vector2 currentStep = new Vector2(mob.getPosition());

        Direction walkingDirection = Direction.NONE;
        Direction runningDirection = Direction.NONE;

        boolean restoreEnergy = true;
        Vector2 nextStep = current.poll();
        if (nextStep != null) {
            walkingDirection = Direction.between(currentStep, nextStep);
            boolean blocked = false;// !collisionManager.traversable(mob.getPosition(), EntityType.NPC, walkingDirection);
            if (blocked) {
                walkingDirection = Direction.NONE;
                clear();
            } else {
                previous.add(nextStep);
                currentStep = nextStep;
                mob.setLastDirection(walkingDirection);

                if (runningPath) {
                    nextStep = decrementRunEnergy() ? current.poll() : null;
                    if (nextStep != null) {
                        runningDirection = Direction.between(currentStep, nextStep);
                        blocked = false;// !collisionManager.traversable(mob.getPosition(), EntityType.NPC, runningDirection);
                        if (blocked) {
                            runningDirection = Direction.NONE;
                            clear();
                        } else {
                            restoreEnergy = false;
                            previous.add(nextStep);
                            currentStep = nextStep;
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

        Position newPosition = new Position(currentStep.getX(), currentStep.getY(), mob.getPosition().getZ());
        mob.setPosition(newPosition);
    }

    /**
     * Uses one of the pathfinder implementations in order to build a path to walk to the {@code destination}. For
     * destinations exceeding {@link Region#SIZE}, {@link #lazyWalk(Position)} will be used instead. <strong>Still must be
     * called from the game thread to ensure thread safety.</strong>
     *
     * @param destination The destination position.
     */
    public void walk(Position destination) {
        int distance = destination.computeLongestDistance(mob.getPosition());
        if (distance > Region.SIZE) {
            lazyWalk(destination);
        } else {
            addPath(findPath(destination));
        }
    }

    /**
     * Uses the smart pf implementations in order to build a path to walk to the {@code target}.
     *
     * @param target The destination target.
     */
    public void walkUntilReached(Entity target) {
        Deque<Vector2> newPath = new ArrayDeque<>();
        Deque<Position> path = pathfindingAlgorithm.find(mob.getPosition(), target.getPosition());
        Position lastPosition = mob.getPosition();
        for (; ; ) {
            Position nextPosition = path.poll();
            boolean reached = lastPosition.isViewable(target.getPosition()) &&
                    collisionManager.reached(mob, target, 1);
            if (nextPosition == null || reached) {
                break;
            }
            newPath.add(new Vector2(nextPosition));
            lastPosition = nextPosition;
        }
        addPath(newPath);
    }

    /**
     * Will asynchronously build a path to {@code destination} and only move the mob once the path is available.
     *
     * @param destination The destination position.
     */
    public void lazyWalk(Position destination) {
        if (lazyWalkResult != null && !lazyWalkResult.isDone()) {
            logger.warn("Existing lazy walk request in progress.");
            return;
        }
        ListenableFuture<Deque<Vector2>> pathResult = mob.getService().submit(() -> findPath(destination));
        pathResult.addListener(() -> {
            try {
                addPath(pathResult.get());
            } catch (Exception e) {
                logger.catching(e);
            }
        }, mob.getService().getExecutor());
        lazyWalkResult = pathResult;
    }

    /**
     * Forces the mob to walk to {@code target}, interacting with it from {@code facing}.
     *
     * @param target The target.
     * @param facing The interaction direction.
     */
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

    /**
     * Forces the mob to walk to {@code target}, interacting with it from the current direction they're facing.
     *
     * @param target The target.
     */
    public void walk(Entity target) {
        if (target instanceof Mob) {
            Mob targetMob = (Mob) target;
            walk(target, Optional.of(targetMob.getLastDirection()));
        } else {
            walk(target, Optional.empty());
        }
    }

    /**
     * Forces the mob to walk to {@code target}, interacting with them from behind.
     *
     * @param target The target.
     */
    public void walkBehind(Mob target) {
        Direction direction = target.getLastDirection().opposite();
        walk(target, Optional.of(direction));
    }

    /**
     * Adds {@code path} to the walking queue.
     *
     * @param path The path to walk.
     */
    public void addPath(Deque<Vector2> path) {
        int size = path.size();
        if (size == 1) {
            addFirst(path.poll());
        } else if (size > 1) {
            addFirst(path.poll());
            for (; ; ) {
                Vector2 nextStep = path.poll();
                if (nextStep == null) {
                    break;
                }
                add(nextStep);
            }
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
     * Adds an initial step to this walking queue.
     *
     * @param step The step to add.
     */
    private void addFirst(Vector2 step) {
        current.clear();
        Deque<Vector2> backtrack = new ArrayDeque<>();
        for (; ; ) {
            Vector2 prev = previous.pollLast();
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
    void add(Vector2 next) {
        Vector2 last = current.peekLast();
        if (last == null) {
            last = new Vector2(mob.getPosition());
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
            current.add(new Vector2(nextX - deltaX, nextY - deltaY));
        }
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
        if(player.getRunEnergy() <= 0.0) {
            return false;
        }
        double totalWeight = player.getWeight();
        double energyReduction = 0.117 * 2 * Math
                .pow(Math.E, 0.0027725887222397812376689284858327062723020005374410 * totalWeight);
        double newValue = player.getRunEnergy() - energyReduction;
        if (newValue <= 0.0) {
            player.setRunEnergy(0.0, true);
            player.setRunning(false);
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
     * Determines the optimal pathfinder to use and finds a path to {@code target}.
     *
     * @param target The target.
     * @return The path.
     */
    private Deque<Vector2> findPath(Position target) {
        Deque<Position> positionPath = pathfindingAlgorithm.find(mob.getPosition(), target);
        Deque<Step> stepPath = new ArrayDeque<>(positionPath.size());
        for (; ; ) {
            // TODO remove step class?
            Position next = positionPath.poll();
            if (next == null) {
                break;
            }
            stepPath.add(new Step(next));
        Deque<Vector2> stepPath = new ArrayDeque<>(positionPath.size());
        }
        return stepPath;
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
     * Returns whether there are remaining steps.
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
}
