package io.luna.game.model.mob.movement;

import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.Locatable;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.game.model.path.GamePathfinder;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/**
 * Represents a single navigation request for a {@link Mob}.
 * <p>
 * A navigation request describes where a mob should move, which interaction policy should be used to determine
 * completion, whether movement should be processed asynchronously, and which pathfinder should resolve the route.
 *
 * @author lare96
 */
public final class NavigationRequest {

    /**
     * Builds {@link NavigationRequest} instances.
     *
     * @author lare96
     */
    public static final class Builder {

        private Mob mob;
        private Locatable target;
        private InteractionPolicy policy;
        private Direction offsetDir;
        private boolean async;
        private boolean continuous;
        private GamePathfinder<Position> pathfinder;

        private Builder(Mob mob) {
            this.mob = mob;
        }

        /**
         * Sets the target position or entity to navigate toward.
         *
         * @param target The target position or entity.
         * @return This builder.
         */
        public Builder target(Locatable target) {
            this.target = target;
            return this;
        }

        /**
         * Sets the interaction policy used to determine when the target has been reached.
         *
         * @param policy The interaction policy.
         * @return This builder.
         */
        public Builder policy(InteractionPolicy policy) {
            this.policy = policy;
            return this;
        }

        /**
         * Sets an optional direction used to offset the final target tile.
         * <p>
         * Offset directions are only valid when the target is an {@link Entity} and the interaction policy distance
         * is exactly {@code 1}.
         *
         * @param offsetDir The offset direction.
         * @return This builder.
         */
        public Builder offsetDir(Direction offsetDir) {
            this.offsetDir = offsetDir;
            return this;
        }

        /**
         * Sets whether this request should perform pathfinding asynchronously.
         *
         * @param async {@code true} to process pathfinding asynchronously, otherwise {@code false}.
         * @return This builder.
         */
        public Builder async(boolean async) {
            this.async = async;
            return this;
        }

        /**
         * Sets whether this request should continuously track the target while navigating.
         *
         * @param continuous {@code true} to continuously track the target, otherwise {@code false}.
         * @return This builder.
         */
        public Builder continuous(boolean continuous) {
            this.continuous = continuous;
            return this;
        }

        /**
         * Sets the pathfinder type used to create the pathfinder for this request.
         *
         * @param pathfinder The pathfinder type.
         * @return This builder.
         */
        public Builder pathfinder(PathfinderType pathfinder) {
            this.pathfinder = pathfinder.getPfFunction().apply(mob);
            return this;
        }

        /**
         * Builds a new {@link NavigationRequest}.
         * <p>
         * This validates that a target and interaction policy have been supplied. It also verifies that offset
         * navigation is only used for adjacent entity interactions.
         *
         * @return The built navigation request.
         * @throws NullPointerException If the target or policy is missing.
         * @throws IllegalStateException If {@code offsetDir} is used with an invalid target or policy distance.
         */
        public NavigationRequest build() {
            requireNonNull(target, "target");
            requireNonNull(policy, "policy");

            if (offsetDir != null && (!(target instanceof Entity) || policy.getDistance() != 1)) {
                throw new IllegalStateException(
                        "offsetDir can only be set when policy distance is 1 and the target is an Entity.");
            }

            return new NavigationRequest(this);
        }
    }

    /**
     * Creates a new navigation request builder for the supplied mob.
     *
     * @param mob The mob that will process the navigation request.
     * @return A new navigation request builder.
     */
    public static Builder builder(Mob mob) {
        return new Builder(mob);
    }

    /**
     * The mob that will process this navigation request.
     */
    private final Mob mob;

    /**
     * The target position or entity the mob should navigate toward.
     */
    private final Locatable target;

    /**
     * The interaction policy used to determine when the target has been reached.
     */
    private final InteractionPolicy policy;

    /**
     * The optional direction used to offset the final target tile.
     */
    private final Optional<Direction> offsetDir;

    /**
     * Whether this request should perform pathfinding asynchronously.
     */
    private final boolean async;

    /**
     * Whether this request should continuously track the target while navigating.
     */
    private final boolean continuous;

    /**
     * The pathfinder used to resolve the route for this request.
     */
    private final GamePathfinder<Position> pathfinder;

    /**
     * The completion result for this navigation request.
     * <p>
     * This future is completed by the navigation system once the request reaches, fails, or finishes without reaching
     * the target.
     */
    private final CompletableFuture<NavigationResult> pending = new CompletableFuture<>();

    /**
     * Creates a new navigation request from the supplied builder.
     *
     * @param builder The builder containing the request configuration.
     */
    private NavigationRequest(Builder builder) {
        mob = builder.mob;
        target = builder.target;
        policy = builder.policy;
        async = builder.async;
        continuous = builder.continuous;
        pathfinder = requireNonNullElse(builder.pathfinder, mob.getNavigator().getDefaultPathfinder());
        offsetDir = Optional.ofNullable(builder.offsetDir);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NavigationRequest)) {
            return false;
        }
        NavigationRequest that = (NavigationRequest) o;
        return async == that.async &&
                continuous == that.continuous &&
                Objects.equals(target, that.target) &&
                Objects.equals(policy, that.policy) &&
                Objects.equals(offsetDir, that.offsetDir);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, policy, offsetDir, async, continuous);
    }

    /**
     * @return The navigating mob.
     */
    public Mob getMob() {
        return mob;
    }

    /**
     * @return The navigation target.
     */
    public Locatable getTarget() {
        return target;
    }

    /**
     * @return The interaction policy.
     */
    public InteractionPolicy getPolicy() {
        return policy;
    }

    /**
     * @return The optional offset direction.
     */
    public Optional<Direction> getOffsetDir() {
        return offsetDir;
    }

    /**
     * @return {@code true} if this request is asynchronous, otherwise {@code false}.
     */
    public boolean isAsync() {
        return async;
    }

    /**
     * @return {@code true} if this request continuously tracks its target, otherwise {@code false}.
     */
    public boolean isContinuous() {
        return continuous;
    }

    /**
     * @return The pathfinder for this request.
     */
    public GamePathfinder<Position> getPathfinder() {
        return pathfinder;
    }

    /**
     * @return The navigation result future.
     */
    public CompletableFuture<NavigationResult> getPending() {
        return pending;
    }
}