package io.luna.game.model.mob.movement;

import io.luna.game.model.Direction;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.path.route.RouteStrategy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;

/**
 * A model representing a walking queue for a {@link Mob}.
 * <p>
 * Ordinary paths are checked against live collision before each walking and running step, using the mob's size and
 * {@link RouteStrategy}. A blocked step drops the remaining path. Higher-level systems such as {@link WalkingNavigator}
 * remain responsible for finding routes around obstacles. Scripted crossings that deliberately pass through collision
 * must use {@link #replaceForcedPath(Deque)} or {@link #addForcedStep(Direction)} explicitly.
 * Immobilization is enforced here for every mob before consuming steps, including human players. This prevents
 * existing or directly queued paths from bypassing the freeze checks in player input and navigation.
 *
 * @author lare96
 * @author Graham
 */
public final class WalkingQueue {

    /**
     * The mob that owns this walking queue.
     */
    private final Mob mob;

    /**
     * Whether movement is currently locked for this mob.
     * <p>
     * When locked, all queued steps are cleared and no movement is processed.
     */
    private boolean locked;

    /**
     * Whether the current path should be processed as a running path.
     * <p>
     * When true, up to two steps will be processed per cycle (if run energy allows it).
     */
    private boolean running;

    /**
     * Whether the current scripted path deliberately crosses collision. Movement restrictions still apply.
     */
    private boolean forced;

    /**
     * The deque of upcoming steps to process for this mob.
     * <p>
     * This represents the forward walking path. Each position is a tile the mob will move to in order as {@link #process()}
     * is called every cycle.
     */
    private final Deque<Position> current = new ArrayDeque<>();

    /**
     * The deque of previously visited steps for this mob.
     * <p>
     * History is used when queuing a new path that starts on a tile the mob recently walked through. In that case, the
     * queue can "backtrack" using this history to smoothly splice the new path into the old one without teleporting
     * or snapping.
     */
    private final Deque<Position> history = new ArrayDeque<>();

    /**
     * Creates a new {@link WalkingQueue} for the specified mob.
     *
     * @param mob The owning mob.
     */
    public WalkingQueue(Mob mob) {
        this.mob = mob;
    }

    /**
     * Processes this walking queue for a single game cycle.
     * <p>
     * This method:
     * <ol>
     *     <li>Aborts if the mob is immobilized, movement-locked, or a stationary NPC, clearing the queue.</li>
     *     <li>Consumes one step as the walking step, updating the mob's walking direction.</li>
     *     <li>If running is enabled and the player has enough run energy, consumes a second step
     *         as the running step, updating the mob's running direction.</li>
     *     <li>Updates the mob's position and maintains the history of visited tiles.</li>
     *     <li>Depletes or restores run energy for players as appropriate.</li>
     * </ol>
     */
    public void process() {
        // Apply the movement restriction at execution, regardless of how the path was queued.
        boolean isNpc = mob instanceof Npc;
        if ((isNpc && mob.asNpc().isStationary()) || mob.getStatus().isImmobilized() || locked) {
            clear();
            mob.setWalkingDirection(Direction.NONE);
            mob.setRunningDirection(Direction.NONE);
            incrementRunEnergy();
            return;
        }
        if(mob instanceof Bot botController && (botController.isBot() || botController.isAutomationActive())) {
            // Close bot windows while moving without interrupting the weak navigation action.
            Bot bot = mob.asPlr().asBot();
            if(!current.isEmpty()) {
                // Without interrupting actions, or the navigation that is moving the bot would be stopped.
                bot.getOverlays().closeWindows(false);
            }
        }

        Position currentStep = mob.getPosition();
        int plane = mob.getZ();

        Position nextStep = current.poll();
        if (nextStep == null) {
            // No steps to process this cycle.
            mob.setWalkingDirection(Direction.NONE);
            mob.setRunningDirection(Direction.NONE);
            incrementRunEnergy();
            return;
        }

        if (!isAdjacent(currentStep, nextStep)) {
            clear();
            mob.setWalkingDirection(Direction.NONE);
            mob.setRunningDirection(Direction.NONE);
            incrementRunEnergy();
            return;
        }
        if (exitInstance(nextStep.setZ(plane))) return;

        // Process a single step.
        Direction firstStepDir = Direction.between(currentStep, nextStep);
        if (!allowsStep(currentStep.setZ(plane), firstStepDir)) {
            clear();
            mob.setWalkingDirection(Direction.NONE);
            mob.setRunningDirection(Direction.NONE);
            incrementRunEnergy();
            return;
        }
        history.add(nextStep);
        mob.setLastDirection(firstStepDir);
        mob.setWalkingDirection(firstStepDir);
        currentStep = nextStep;

        if (isNpc) {
            // Move its collision before checking a second step that could turn back into its old footprint.
            mob.setPosition(currentStep.setZ(plane));
        }

        // A second step is charged only after it passes validation. Keep it queued if energy runs out.
        nextStep = running ? current.peek() : null;
        if (nextStep != null) {
            if (!isAdjacent(currentStep, nextStep)) {
                clear();
                mob.setRunningDirection(Direction.NONE);
                mob.setPosition(currentStep.setZ(plane));
                incrementRunEnergy();
                return;
            }
            Position destination = nextStep.setZ(plane);
            boolean leavingInstance = mob instanceof Player player && player.getDynamicMap() != null &&
                    !player.getDynamicMap().contains(destination);
            Direction nextStepDir = Direction.between(currentStep, nextStep);
            if (!leavingInstance && !allowsStep(currentStep.setZ(plane), nextStepDir)) {
                clear();
                mob.setRunningDirection(Direction.NONE);
                mob.setPosition(currentStep.setZ(plane));
                incrementRunEnergy();
                return;
            }
            if (decrementRunEnergy()) {
                if (exitInstance(destination)) return;
                current.poll();
                history.add(nextStep);
                mob.setLastDirection(nextStepDir);
                mob.setRunningDirection(nextStepDir);
                currentStep = nextStep;
            } else {
                mob.setRunningDirection(Direction.NONE);
                incrementRunEnergy();
            }
        } else {
            mob.setRunningDirection(Direction.NONE);
            incrementRunEnergy(); // Restore run energy while not running.
        }

        mob.setPosition(currentStep.setZ(plane));
    }

    /**
     * Leaves a private map through its controller before a queued step enters an unmapped tile.
     * This runs before collision checks, since unmapped space is deliberately blocked.
     *
     * @param destination The next walking or running tile.
     * @return Whether this step exited the instance and ended movement processing.
     */
    private boolean exitInstance(Position destination) {
        if (mob instanceof Player player && player.getDynamicMap() != null &&
                !player.getDynamicMap().contains(destination)) {
            player.getDynamicMap().remove(player);
            clear();
            mob.setWalkingDirection(Direction.NONE);
            mob.setRunningDirection(Direction.NONE);
            return true;
        }
        return false;
    }

    /**
     * Adds a pre-computed path to this walking queue.
     * <p>
     * Intermediate tiles are filled in without pathfinding. Each step is checked against live collision when consumed.
     * Use {@link WalkingNavigator#navigate(Position, boolean)} to find a route around obstacles. A null or empty path
     * clears the existing path, including any forced movement mode.
     *
     * @param path The path to walk as a sequence of absolute {@link Position} tiles.
     */
    public void replacePath(Deque<Position> path) {
        replacePath(path, false);
    }

    /**
     * Queues a scripted crossing that deliberately ignores terrain, wall and mob collision. Locks, immobilization,
     * stationary NPCs and private-map exits still apply. Previous movement history is discarded so the crossing follows
     * only its supplied positions. Ordinary paths queued later restore collision checks.
     * A null or empty path clears the existing path and forced movement mode.
     *
     * @param path The scripted crossing as absolute positions.
     */
    public void replaceForcedPath(Deque<Position> path) {
        replacePath(path, true);
    }

    /**
     * Replaces the path and the collision mode together.
     *
     * @param path The path to queue.
     * @param forced Whether the scripted path deliberately crosses collision.
     */
    private void replacePath(Deque<Position> path, boolean forced) {
        if (path == null || path.isEmpty()) {
            clear();
            return;
        }
        if (forced) {
            clear();
        }
        this.forced = forced;
        addFirst(path.poll());
        for (; ; ) {
            Position nextPosition = path.poll();
            if (nextPosition == null) {
                break;
            }
            add(nextPosition);
        }
    }

    /**
     * Adds a single step in the specified direction to this walking queue.
     * <p>
     * The step is checked against live collision when consumed, using the mob's size and routing strategy.
     *
     * @param direction The direction to step from the mob's current position.
     */
    public void addStep(Direction direction) {
        forced = false;
        addFirst(mob.getPosition().translate(1, direction));
    }

    /**
     * Queues exactly one scripted step that deliberately crosses collision. Previous movement history is discarded;
     * movement restrictions still apply.
     *
     * @param direction The direction of the scripted step.
     */
    public void addForcedStep(Direction direction) {
        clear();
        forced = true;
        addFirst(mob.getPosition().translate(1, direction));
    }

    /**
     * Clears both the current and previous steps for this walking queue.
     * <p>
     * After calling this, the mob will remain stationary until new steps are queued.
     */
    public void clear() {
        current.clear();
        history.clear();
        forced = false;
    }

    /**
     * Returns the number of remaining queued tiles in this walking queue.
     *
     * @return The amount of remaining steps.
     */
    public int size() {
        return current.size();
    }

    /**
     * Returns whether there are no remaining steps in this walking queue.
     *
     * @return {@code true} if this walking queue is empty, otherwise {@code false}.
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Adds an initial step to this walking queue, potentially backtracking through the movement history.
     * <p>
     * Semantics:
     * <ul>
     *     <li>Clears the current queue.</li>
     *     <li>Walks backwards through {@link #history} until it finds the requested tile, collecting the intermediate
     *     tiles used to reach that point.</li>
     *     <li>If the tile is found in history, replays those backtracked steps into {@link #current} so the mob can
     *     smoothly walk back to that tile and then continue from there.</li>
     *     <li>If the tile is not found in history, simply starts a new path from that tile.</li>
     * </ul>
     *
     * @param step The first step of the new path.
     */
    private void addFirst(Position step) {
        current.clear();
        Queue<Position> backtrack = new ArrayDeque<>();
        for (; ; ) {
            Position prev = history.pollLast();
            if (prev == null) {
                break;
            }
            backtrack.add(prev);
            if (prev.getX() == step.getX() && prev.getY() == step.getY()) {
                backtrack.forEach(this::add);
                history.clear();
                return;
            }
        }
        history.clear();
        add(step);
    }

    /**
     * Adds a non-initial step to this walking queue.
     * <p>
     * The supplied position is an absolute destination tile. If that destination is more than one tile away from the
     * last queued step, this method will fill in all intermediate tiles so that the resulting path is continuous.
     *
     * @param next The next step to add, as an absolute position.
     */
    private void add(Position next) {
        Position last = current.peekLast();
        if (last == null) {
            last = mob.getPosition();
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
            current.add(new Position(nextX - deltaX, nextY - deltaY));
        }
    }

    /**
     * Determines if the mob may take an ordinary step against live collision, including its current size and routing
     * strategy. An explicit scripted path bypasses only this collision check.
     *
     * @param from The tile the step is taken from.
     * @param direction The direction of the step.
     * @return {@code true} if the step is allowed.
     */
    private boolean allowsStep(Position from, Direction direction) {
        if (forced) {
            return true;
        }
        return mob.getWorld().getCollisionManager()
                .traversable(from, mob.getType(), direction, mob.size(), mob.getRouteStrategy());
    }

    /**
     * Rejects a queued tile that became stale after the mob's position changed outside this queue.
     *
     * @param from The mob's current tile.
     * @param to The queued tile.
     * @return Whether the tiles are exactly one walking step apart.
     */
    private static boolean isAdjacent(Position from, Position to) {
        return Math.max(Math.abs(from.getX() - to.getX()), Math.abs(from.getY() - to.getY())) == 1;
    }

    /**
     * Depletes run energy for a player if they are running.
     * <p>
     * The depletion rate is based on the player's carried weight (see {@link #drainRate(double)}). If run energy
     * reaches zero, this method will:
     * <ul>
     *     <li>Set the player's run energy to {@code 0}.</li>
     *     <li>Disable running on the player.</li>
     * </ul>
     *
     * @return {@code true} if the player still has run energy remaining and can continue running, otherwise {@code false}.
     */
    private boolean decrementRunEnergy() {
        if (mob.getType() != EntityType.PLAYER) {
            return true;
        }
        Player player = (Player) mob;
        if (player.getRunEnergy() <= 0.0) {
            player.setRunning(false);
            return false;
        }
        double newValue = player.getRunEnergy() - drainRate(player.getWeight());
        if (newValue <= 0.0) {
            player.setRunEnergy(0.0, true);
            player.setRunning(false);
            return false;
        }
        player.setRunEnergy(newValue, true);
        return true;
    }

    /**
     * The run energy drained in one running tick, in percent: {@code 67 + 67 * weight / 64} hundredths of a percent,
     * rounded down, with the weight held between 0 and 64 kg. Agility does not change it. A full bar lasts about 150
     * running ticks at 0 kg and 75 at 64 kg or more.
     *
     * @param weight The player's carried weight, in kilograms.
     * @return The percentage drained.
     */
    static double drainRate(double weight) {
        double clamped = Math.max(0.0, Math.min(weight, 64.0));
        return (67 + (int) (67 * clamped / 64)) / 100.0;
    }

    /**
     * Restores run energy for a player while they are not running.
     * <p>
     * The restoration rate is based on the player's current Agility level (see {@link #restoreRate(int)}). Run energy
     * is clamped to a maximum of {@code 100.0}.
     */
    private void incrementRunEnergy() {
        if (mob.getType() != EntityType.PLAYER)
            return;
        Player player = mob.asPlr();

        double runEnergy = player.getRunEnergy();
        if (runEnergy >= 100.0) {
            return;
        }

        double newValue = runEnergy + restoreRate(player.skill(Skill.AGILITY).getLevel());
        newValue = Math.min(newValue, 100.0);

        player.setRunEnergy(newValue, true);
    }

    /**
     * The run energy restored in one tick, in percent: {@code agilityLevel / 6 + 8} hundredths of a percent, with the
     * division rounded down. Boosted and drained levels count. A full bar takes 1,250 ticks at level 1 and 417 at 99.
     *
     * @param agilityLevel The player's current Agility level.
     * @return The percentage restored.
     */
    static double restoreRate(int agilityLevel) {
        return (agilityLevel / 6 + 8) / 100.0;
    }

    /**
     * Determines if this path is a running path.
     *
     * @return {@code true} if this path is a running path, {@code false} otherwise.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Sets whether the current path should be treated as a running path.
     * <p>
     * When enabled, {@link #process()} will attempt to consume up to two tiles per cycle, subject to available run
     * energy.
     *
     * @param running {@code true} to enable running, {@code false} to disable.
     */
    public void setRunning(boolean running) {
        this.running = running;
    }

    /**
     * Returns whether movement is currently locked for this mob.
     *
     * @return {@code true} if movement is locked, otherwise {@code false}.
     */
    public boolean isLocked() {
        return locked;
    }

    /**
     * Locks or unlocks movement for this mob.
     * <p>
     * When movement is locked:
     * <ul>
     *     <li>All queued steps and history are cleared.</li>
     *     <li>{@link #process()} will not move the mob until it is unlocked.</li>
     * </ul>
     *
     * @param locked {@code true} to lock movement and clear the queue, {@code false} to unlock.
     */
    public void setLocked(boolean locked) {
        if (locked) {
            clear();
        }
        this.locked = locked;
    }
}
