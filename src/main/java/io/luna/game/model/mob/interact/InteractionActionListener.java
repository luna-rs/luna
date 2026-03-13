package io.luna.game.model.mob.interact;

/**
 * Holds a deferred interaction listener and the {@link InteractionPolicy} that determines when it may be executed.
 * <p>
 * Instances of this type are created during interaction event matching and later consumed by {@link InteractionAction}
 * once the player has satisfied the required interaction conditions.
 *
 * @author lare96
 */
public final class InteractionActionListener {

    /**
     * The policy that determines when {@link #action} may be executed.
     */
    private final InteractionPolicy policy;

    /**
     * The action to run once {@link #policy} has been satisfied.
     */
    private final Runnable action;

    /**
     * Creates a new {@link InteractionActionListener}.
     *
     * @param policy The interaction policy that gates execution of {@code action}.
     * @param action The action to execute once {@code policy} is satisfied.
     */
    public InteractionActionListener(InteractionPolicy policy, Runnable action) {
        this.policy = policy;
        this.action = action;
    }

    /**
     * @return The interaction policy.
     */
    public InteractionPolicy getPolicy() {
        return policy;
    }

    /**
     * @return The deferred action.
     */
    public Runnable getAction() {
        return action;
    }
}