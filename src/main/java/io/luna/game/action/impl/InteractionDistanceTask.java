package io.luna.game.action.impl;

import io.luna.game.event.Event;
import io.luna.game.event.impl.InteractableEvent;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.object.GameObject;
import io.luna.game.task.Task;

import java.util.Optional;

/**
 * A {@link Task} implementation that posts an {@link Event} when an appropriate interactable distance to an entity has
 * been reached.
 *
 * @author lare96
 */
public final class InteractionDistanceTask extends Task {

    /**
     * A task that keeps track of the interaction and cancels the focus of the entity being interacted with.
     */
    private final class InteractionFocusTask extends Task {

        /**
         * Creates a new {@link InteractionFocusTask}.
         */
        public InteractionFocusTask() {
            super(false, 1);
        }

        @Override
        protected void execute() {
            Npc interactNpc = (Npc) interactWith;
            if (interactNpc.getInteractingWith().isEmpty() ||
                    !interactNpc.getInteractingWith().get().equals(player)) {
                // Do nothing and cancel if NPC is not interacting with anyone or anyone but the original player.
                cancel();
            } else if (player.getInteractingWith().isEmpty() ||
                    !player.getInteractingWith().get().equals(player)) {
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
     * The distance to interact from.
     */
    private final int distance;

    /**
     * The event to post.
     */
    private final InteractableEvent event;

    private boolean delayed; // todo some interactions, delayed is true already. this needs to be set from scripts somehow lol
    // along with the interaction distance. maybe will have to do it from events (probably)

    /**
     * Creates a new {@link InteractionDistanceTask}.
     *
     * @param player The interacting player.
     * @param interactWith The entity to interact with.
     * @param distance The distance to interact from.
     * @param event The event to post.
     */
    public InteractionDistanceTask(Player player, Entity interactWith, int distance, InteractableEvent event) {
        super(true, 1);
        this.player = player;
        this.interactWith = interactWith;
        this.distance = distance;
        this.event = event;
    }

    @Override
    protected boolean onSchedule() {
        // TODO Why is this needed? Make bots always use pathfinding when walking
        if (player.isBot() && !player.canInteractWith(interactWith, distance)) {
            if (interactWith instanceof GameObject) {
                player.getWalking().walk(interactWith, Optional.of(((GameObject) interactWith).getDirection().toNormalDirection()));
            } else if (interactWith instanceof Mob) {
                player.getWalking().walk(interactWith, Optional.of(((Mob) interactWith).getLastDirection()));
            }
        }
        player.getActions().interruptWeak();
        return true;
    }

    @Override
    protected void execute() {
        if (player.canInteractWith(interactWith, distance)) {
            if (!delayed) {
                if (interactWith.getType() != EntityType.ITEM) {
                    player.interact(interactWith);
                }
                delayed = true;
            } else {
                if (interactWith.getType() == EntityType.NPC) {
                    Npc interactNpc = (Npc) interactWith;
                    interactNpc.interact(player);
                    player.getWorld().schedule(new InteractionFocusTask());
                }
                cancel();
                if (event instanceof Event) {
                    player.getPlugins().post((Event) event);
                } else {
                    throw new IllegalStateException("InteractableEvent must also be of type Event!");
                }
            }
        }
    }

    /**
     * @return The entity to interact with.
     */
    public Entity getInteractWith() {
        return interactWith;
    }
}