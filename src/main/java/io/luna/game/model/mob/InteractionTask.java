package io.luna.game.model.mob;

import io.luna.game.event.Event;
import io.luna.game.event.impl.InteractableEvent;
import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionManager;
import io.luna.game.task.Task;

import java.util.Set;

/**
 * A {@link Task} implementation that posts an {@link Event} when an appropriate interactable distance to an entity has
 * been reached.
 *
 * @author lare96
 */
public final class InteractionTask extends Task {

    /**
     * A task that keeps track of the interaction and cancels the focus of the entity being interacted with.
     */
    private final class InteractionFocusTask extends Task {

        /**
         * Creates a new {@link InteractionFocusTask}.
         */
        public InteractionFocusTask(boolean delayed) {
            super(false, delayed ? 2 : 1);
        }

        @Override
        protected boolean onSchedule() {
            Npc interactNpc = (Npc) interactWith;
            interactNpc.interact(player);
            return true;
        }

        @Override
        protected void execute() {
            Npc interactNpc = (Npc) interactWith;
            if (interactNpc.getInteractingWith().isEmpty() ||
                    !interactNpc.getInteractingWith().get().equals(player)) {
                // Do nothing and cancel if NPC is not interacting with anyone or anyone but the original player.
                cancel();
            } else if (player.getInteractingWith().isEmpty() ||
                    !player.getInteractingWith().get().equals(interactWith)) {
                // If the original player that initiated the interaction has left it, we leave too.
                interactNpc.resetInteractingWith();
                cancel();
            }
        }
    }

    /**
     * The player interacting.
     */
    private final Player player;

    /**
     * The entity to interact with.
     */
    private final Entity interactWith;

    /**
     * The event to post.
     */
    private final InteractableEvent event;

    /**
     * The action to run if the player reaches {@link #interactWith}.
     */
    private final Runnable action;

    /**
     * Creates a new {@link InteractionTask}.
     *
     * @param player The interacting player.
     * @param interactWith The entity to interact with.
     * @param event The event to post.
     * @param action The action to run.
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
        player.getActions().interruptWeak();
        if (interactWith instanceof Mob) {
            player.interact(interactWith);
        }
        return true;
    }

    @Override
    protected void execute() {
        int distance = event.distance();
        boolean isMob = interactWith instanceof Mob;
        boolean standing = player.getWalking().isEmpty();
        CollisionManager collision = player.getWorld().getCollisionManager();
        boolean reached = collision.reached(player, interactWith, distance);
        if (reached) {
            player.getWalking().clear();
            if (isMob || interactWith.size() == 1) {
                boolean delayed = false;
                Position current = player.getPosition();
                Set<Direction> directions = getClosestNESW();
                if (!directions.isEmpty() && interactWith.isWithinDistance(player, 1)) {
                    // If we're diagonal to entity, walk to closest NESW direction.
                    for (Direction dir : directions) {
                        if (collision.traversable(current, EntityType.NPC, dir)) {
                            player.getWalking().walk(current.translate(1, dir));
                            break;
                        }
                    }
                    delayed = true;
                } else if (interactWith.getPosition().equals(current)) {
                    // If we're on top of the mob, walk a random NESW direction.
                    player.getWalking().walkRandomDirection();
                    delayed = true;
                }
                if (interactWith.getType() == EntityType.NPC) {
                    // Focus NPC on our player.
                    player.getWorld().schedule(new InteractionFocusTask(delayed));
                }
                if (delayed) {
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
            cancel();
            player.interact(interactWith);
            action.run();
        } else if (standing) {
            // We've reached the end of the path, but nothing is within reach.
            player.sendMessage("I can't reach that!");
            cancel();
        }
    }

    /**
     * Returns a set of the closest NESW directions to the entity being interacted with.
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