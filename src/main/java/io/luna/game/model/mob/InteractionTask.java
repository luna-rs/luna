package io.luna.game.model.mob;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.event.impl.InteractableEvent;
import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionManager;
import io.luna.game.task.Task;

import java.util.Objects;
import java.util.Set;

/**
 * A {@link Task} that drives walking towards an interactable {@link Entity} and fires an {@link InteractableEvent}
 * (plus an optional action) once the player is in range.
 * <p>
 * This task is responsible for the "click → path → reach → interact" flow:
 * </p>
 *
 * <ol>
 *     <li>The player clicks on an entity.</li>
 *     <li>An {@link InteractionTask} is scheduled, interrupting the player's current actions.</li>
 *     <li>Each tick, the task checks if the player has reached the required distance.</li>
 *     <li>Once in range, any final tile adjustments are made (e.g. move off diagonals or off the same tile).</li>
 *     <li>The interaction is performed (via {@link Player#interact(Entity)} and {@code action.run()}).</li>
 * </ol>
 *
 * <p>
 * For NPCs, an additional {@link InteractionFocusAction} is submitted to keep the NPC focused on the interacting
 * player, and to automatically cancel if either side ceases the interaction.
 * </p>
 *
 * @author lare96
 */
public final class InteractionTask extends Task {

    /**
     * A helper {@link Action} that maintains NPC focus on the interacting player and cancels the interaction if
     * either side abandons it.
     *
     * <p>
     * This action:
     * </p>
     * <ul>
     *     <li>Initially calls {@link Mob#interact(Entity)} when scheduled (on the first execution).</li>
     *     <li>On each subsequent execution, verifies that the NPC is still interacting with the same player
     *         and that the player is still interacting with this NPC.</li>
     *     <li>Stops (returns {@code true}) if the interaction relationship is broken on either side.</li>
     * </ul>
     *
     * <p>
     * It is submitted as a {@link ActionType#WEAK} action so that it will not prevent stronger NPC behaviors
     * (such as combat) from interrupting the interaction when appropriate.
     * </p>
     */
    private final class InteractionFocusAction extends Action<Npc> {

        /**
         * Creates a new {@link InteractionFocusAction}.
         *
         * @param npc The NPC that should focus on the player.
         * @param delayed If {@code true}, the action runs after 2 ticks instead of 1. This is used when
         * the player must first make a corrective move (e.g., diagonal or stacked tile)
         * before the NPC begins focusing.
         */
        public InteractionFocusAction(Npc npc, boolean delayed) {
            super(npc, ActionType.WEAK, false, delayed ? 2 : 1);
        }

        @Override
        public boolean run() {
            Npc interactNpc = (Npc) interactWith;

            // Interact with player on first iteration.
            if (getExecutionCounter() == 0) {
                interactNpc.interact(player);
            }

            // If the NPC is not interacting with anyone, or not with this player, stop.
            if (interactNpc.getInteractingWith() == null ||
                    !Objects.equals(interactNpc.getInteractingWith(), player)) {
                return true;
            }

            // If the player is no longer interacting with this NPC, reset and stop.
            if (player.getInteractingWith() == null ||
                    !Objects.equals(player.getInteractingWith(), interactNpc)) {
                interactNpc.resetInteractingWith();
                return true;
            }
            return false;
        }
    }

    /**
     * The player performing the interaction.
     */
    private final Player player;

    /**
     * The entity that the player is trying to interact with.
     */
    private final Entity interactWith;

    /**
     * The event describing the interaction (used for distance and behavior).
     */
    private final InteractableEvent event;

    /**
     * The callback to run once the player successfully reaches and interacts with {@link #interactWith}.
     */
    private final Runnable action;

    /**
     * Creates a new {@link InteractionTask}.
     *
     * @param player The interacting player.
     * @param interactWith The target entity that the player is trying to interact with.
     * @param event The {@link InteractableEvent} associated with the interaction. This defines
     * required distance and additional semantics of the interaction.
     * @param action The callback to execute once the interaction succeeds (after reach checks
     * and any corrective movement).
     */
    public InteractionTask(Player player, Entity interactWith, InteractableEvent event, Runnable action) {
        super(true, 1);
        this.player = player;
        this.interactWith = interactWith;
        this.event = event;
        this.action = action;
    }

    @Override
    protected boolean onSchedule() {
        // Interrupt any weaker actions so the interaction takes precedence.
        player.getActions().interruptWeak();

        // For mobs (NPCs/players), set the interaction focus immediately on the player.
        if (interactWith instanceof Mob) {
            player.interact(interactWith);
        }
        return true;
    }

    @Override
    protected void execute() {
        int distance = event.distance();
        CollisionManager collisionManager = player.getWorld().getCollisionManager();

        if (collisionManager.reached(player, interactWith, distance)) {
            onReached(interactWith instanceof Mob, collisionManager);
        } else if (player.getWalking().isEmpty()) {
            onStanding();
        }
    }

    /**
     * Called when the collision system determines that the player has reached the required distance to interact with
     * {@link #interactWith}.
     *
     * @param isMob {@code true} if the target is a {@link Mob}, otherwise {@code false}.
     * @param collisionManager The collision manager used to test traversability when performing
     * corrective steps.
     */
    private void onReached(boolean isMob, CollisionManager collisionManager) {
        // Stop walking once we are within interaction distance.
        player.getWalking().clear();

        if (isMob || interactWith.size() == 1) {
            boolean delayed = false;
            Position current = player.getPosition();
            Set<Direction> directions = getClosestNESW();

            if (!directions.isEmpty() && interactWith.isWithinDistance(player, 1)) {
                /*
                 * We are diagonal to the target: walk one step to the closest cardinal (NESW)
                 * tile so the player is no longer standing diagonally adjacent.
                 */
                for (Direction dir : directions) {
                    if (player.getNavigator().step(dir)) {
                        break;
                    }
                }
                delayed = true;
            } else if (interactWith.getPosition().equals(current)) {
                /*
                 * We are standing on top of the entity's tile. Move one step in a random
                 * NESW direction to get off the tile before interacting.
                 */
                player.getNavigator().stepRandom(false);
                delayed = true;
            }

            if (interactWith instanceof Npc) {
                // Schedule a focus action so the NPC looks at and tracks the player.
                Npc npc = (Npc) interactWith;
                npc.submitAction(new InteractionFocusAction(npc, delayed));
            }

            if (delayed) {
                // Delay the actual interaction until after the corrective step.
                cancel();
                player.lock();
                player.getWorld().schedule(new Task(false, 1) {
                    @Override
                    protected void execute() {
                        player.unlock();
                        player.interact(interactWith);
                        action.run();
                        cancel();
                    }
                });
                return;
            }
        }

        // No corrective movement needed: perform the interaction immediately.
        cancel();
        player.interact(interactWith);
        action.run();
    }

    /**
     * Called when the player has stopped walking (no path remaining) but has not reached the target.
     * <p>
     * This typically indicates that pathfinding has failed or that no valid route exists.
     * The player is informed that the target cannot be reached, and the task cancels itself.
     * </p>
     */
    private void onStanding() {
        /*
         * The player is no longer walking, but the target is still out of reach. This usually means there is no
         * valid route.
         */
        player.sendMessage("I can't reach that!");
        cancel();
    }

    /**
     * Returns the set of cardinal directions (N/E/S/W) that best approximate the direction from the player to the
     * target entity.
     * <p>
     * If the player is diagonally adjacent (e.g. {@link Direction#NORTH_EAST}), this returns the two relevant
     * cardinal options (NORTH and EAST). If the player is already aligned along a cardinal direction or is on the
     * same tile, this returns an empty set.
     * </p>
     *
     * @return A set of up to two cardinal directions used for step correction when diagonal.
     */
    private Set<Direction> getClosestNESW() {
        Direction direction = Direction.between(player.getPosition(), interactWith.getPosition());
        switch (direction) {
            case SOUTH_WEST:
                return Set.of(Direction.SOUTH, Direction.WEST);
            case SOUTH_EAST:
                return Set.of(Direction.SOUTH, Direction.EAST);
            case NORTH_WEST:
                return Set.of(Direction.NORTH, Direction.WEST);
            case NORTH_EAST:
                return Set.of(Direction.NORTH, Direction.EAST);
        }
        return Set.of();
    }
}
