package io.luna.game.action;

import io.luna.game.event.Event;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.mob.Player;
import io.luna.game.plugin.PluginManager;

/**
 * A {@link DistancedAction} implementation that posts an event upon interacting with an {@link Entity}.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class InteractionAction extends DistancedAction<Player> {

    /**
     * The entity to interact with.
     */
    private final Entity interact;

    /**
     * The event to post when interacting.
     */
    private final Event event;

    /**
     * Creates a new {@link InteractionAction}.
     *
     * @param player The interacting player.
     * @param interact The entity to interact with.
     * @param event The event to post after interacting.
     */
    public InteractionAction(Player player, Entity interact, Event event) {
        super(player, interact.getPosition(), interact.size(), true);
        this.interact = interact;
        this.event = event;
    }

    @Override
    protected void execute() {
        if (interact.getType() != EntityType.ITEM) {
            mob.interact(interact);
        }
        mob.getWalkingQueue().clear();

        PluginManager plugins = mob.getPlugins();
        plugins.post(event);
    }

    @Override
    protected boolean isEqual(Action<?> other) {
        if (other instanceof InteractionAction) {
            InteractionAction action = (InteractionAction) other;
            return interact.equals(action.interact);
        }
        return false;
    }
}