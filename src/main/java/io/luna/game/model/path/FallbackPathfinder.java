package io.luna.game.model.path;

import io.luna.game.model.Locatable;

import java.util.function.Function;

/**
 * A {@link GamePathfinder} that searches with a primary pathfinder, and hands the searches that the primary
 * pathfinder does not {@link GamePathfinder#supports(Locatable, Locatable) support} to a fallback pathfinder.
 * <p>
 * Neither pathfinder knows of the other. The fallback is created for each search that needs it, from the origin of that
 * search, because some pathfinders are bound to the area they search in.
 *
 * @param <T> The locatable type used by this pathfinder.
 * @author hydrozoa
 */
public final class FallbackPathfinder<T extends Locatable> extends GamePathfinder<T> {

    /**
     * The pathfinder that searches whenever it can.
     */
    private final GamePathfinder<T> primary;

    /**
     * Creates the pathfinder for the searches that the primary pathfinder can't do, given the origin of the search.
     */
    private final Function<T, GamePathfinder<T>> fallbackFunction;

    /**
     * Creates a new {@link FallbackPathfinder}.
     *
     * @param primary The pathfinder that searches whenever it can.
     * @param fallbackFunction The function that creates the pathfinder for the searches that the primary pathfinder
     * can't do, given the origin of the search.
     */
    public FallbackPathfinder(GamePathfinder<T> primary, Function<T, GamePathfinder<T>> fallbackFunction) {
        super(primary.collisionManager);
        this.primary = primary;
        this.fallbackFunction = fallbackFunction;
    }

    @Override
    public PathResult<T> find(T origin, T target) {
        if (primary.supports(origin, target)) {
            return primary.find(origin, target);
        }
        return fallbackFunction.apply(origin).find(origin, target);
    }
}
