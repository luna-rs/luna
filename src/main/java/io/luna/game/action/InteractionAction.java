package io.luna.game.action;

import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.mob.Player;

/**
 * A {@link DistancedAction} implementation that interacts with an entity when an appropriate distance has been reached.
 *
 * @author lare96 <http://github.com/lare96>
 */
public abstract class InteractionAction extends DistancedAction<Player> {

    /**
     * The entity to interact with.
     */
    private final Entity interact;

    /**
     * Creates a new {@link InteractionAction}.
     *
     * @param player The interacting player.
     * @param interact The entity to interact with.
     */
    public InteractionAction(Player player, Entity interact) {
        super(player, interact.getPosition(), interact.size());
        this.interact = interact;
    }

    @Override
    public void withinDistance() {
        if (interact.getType() != EntityType.ITEM) {
            mob.interact(interact);
        }
        mob.getWalking().clear();
        execute();
    }

    @Override
    public final boolean ignoreIf(Action<?> other) {
        // Ignore interaction action if the entity being interacted with is the same.
        if (other instanceof InteractionAction) {
            InteractionAction action = (InteractionAction) other;
            return interact.equals(action.interact);
        }
        return false;
    }

    /**
     * Function executed once the mob has interacted with {@link #interact}.
     */
    public abstract void execute();
}