package io.luna.game.action;

import io.luna.game.model.World;
import io.luna.game.model.mob.Mob;

/**
 * An abstraction model representing an important task a mob is doing. There are three main action types: {@link RepeatingAction},
 * {@link QueuedAction}, and {@link ThrottledAction}. All actions are extensions of these types.
 * <p>
 * <p>
 * A mob can only be performing one action at a time, and it can be stopped (interrupted) by using {@code interrupt()} within
 * an action implementation or {@link Mob#interruptAction()} anywhere else. By default, actions are interrupted in instances
 * such as during walking, unregistering, teleporting, etc.
 *
 * @param <T> The mob that this Action is dedicated to.
 * @author lare96
 */
public abstract class Action<T extends Mob> {

    /**
     * The mob assigned to this action.
     */
    protected final T mob;

    /**
     * The world instance.
     */
    protected final World world;

    /**
     * The mob's action set.
     */
    protected final ActionManager actionManager;

    /**
     * Creates a new {@link Action}.
     *
     * @param mob The mob assigned to this action.
     */
    Action(T mob) {
        this.mob = mob;
        world = mob.getWorld();
        actionManager = mob.getActions();
    }

    /**
     * Runs this action.
     */
    public abstract void run();

    /**
     * @return {@code true} if this action is a {@link QueuedAction}.
     */
    public final boolean isQueued() {
        return this instanceof QueuedAction<?>;
    }

    /**
     * @return {@code true} if this action is a {@link RepeatingAction}.
     */
    public final boolean isRepeating() {
        return this instanceof RepeatingAction<?>;
    }

    /**
     * @return {@code true} if this action is a {@link ThrottledAction}.
     */
    public final boolean isThrottled() {
        return this instanceof ThrottledAction<?>;
    }

    /**
     * @return The mob assigned to this action.
     */
    public final T getMob() {
        return mob;
    }
}
