package io.luna.game.action;

import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.task.Task;

/**
 * A {@link DistancedAction} implementation that interacts with an entity when an appropriate distance has been reached.
 *
 * @author lare96 
 */
public abstract class InteractionAction extends DistancedAction<Player> {

    /**
     * A task that keeps track of the interaction and cancels the focus of the entity being interacted with.
     */
    private final class InteractionTask extends Task {


        /**
         * Creates a new {@link InteractionTask}.
         */
        public InteractionTask() {
            super(false, 1);
        }

        @Override
        protected void execute() {
            Npc interactNpc = (Npc) interactWith;
            if (interactNpc.getInteractingWith().isEmpty() ||
                    !interactNpc.getInteractingWith().get().equals(mob)) {
                // Do nothing and cancel if NPC is not interacting with anyone or anyone but the original mob.
                cancel();
            } else if (mob.getInteractingWith().isEmpty() ||
                    !mob.getInteractingWith().get().equals(mob)) {
                // If the original mob that initiated the interaction has left it, we leave too.
                interactNpc.resetInteractingWith();
                cancel();
            }
        }
    }

    /**
     * The entity to interact with.
     */
    private final Entity interactWith;

    /**
     * Creates a new {@link InteractionAction}.
     *
     * @param player The interacting player.
     * @param interactWith The entity to interact with.
     */
    public InteractionAction(Player player, Entity interactWith) {
        super(player, interactWith.getPosition(), interactWith.size());
        this.interactWith = interactWith;
    }

    @Override
    public void withinDistance() {
        if (!interactWith.isInteractable()) {
            return;
        }

        if (interactWith.getType() != EntityType.ITEM) {
            mob.interact(interactWith);
            if (interactWith.getType() == EntityType.NPC) {
                Npc interactNpc = (Npc) interactWith;
                interactNpc.interact(mob);
                world.schedule(new InteractionTask());
            }
        }
        mob.getWalking().clear();
        execute();
    }

    @Override
    public final boolean ignoreIf(Action<?> other) {
        // Ignore interaction action if the entity being interacted with is the same.
        if (other instanceof InteractionAction) {
            InteractionAction action = (InteractionAction) other;
            return interactWith.equals(action.interactWith);
        }
        return false;
    }

    /**
     * Function executed once the mob has interacted with {@link #interactWith}.
     */
    public abstract void execute();
}