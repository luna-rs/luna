package io.luna.game.model.path;

import io.luna.game.model.Position;

import java.util.List;

/**
 * The result of a search by a {@link RouteFinder}.
 *
 * @author hydrozoa
 */
public final class Route {

    /**
     * A route for a search that found nothing.
     */
    static final Route FAILED = new Route(List.of(), false, false);

    /**
     * The corners of the route, from the first one after the start to the end. Tiles between two corners are in a
     * straight line, either along an axis or diagonally.
     */
    private final List<Position> waypoints;

    /**
     * If the route ends at the closest tile that could be reached instead of at the destination.
     */
    private final boolean alternative;

    /**
     * If a route was found at all.
     */
    private final boolean success;

    /**
     * Creates a new {@link Route}.
     *
     * @param waypoints The corners of the route.
     * @param alternative If the route ends at the closest reachable tile instead of the destination.
     * @param success If a route was found.
     */
    Route(List<Position> waypoints, boolean alternative, boolean success) {
        this.waypoints = waypoints;
        this.alternative = alternative;
        this.success = success;
    }

    /**
     * Returns the corners of the route.
     */
    public List<Position> getWaypoints() {
        return waypoints;
    }

    /**
     * Returns whether the route ends at the closest tile that could be reached instead of at the destination.
     */
    public boolean isAlternative() {
        return alternative;
    }

    /**
     * Returns whether a route was found.
     */
    public boolean isSuccess() {
        return success;
    }
}
