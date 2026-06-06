package io.luna.game.model.path;

/**
 * Describes the outcome of a pathfinding request.
 * <p>
 * This lets callers distinguish between a fully successful path, a usable fallback path, no movement being needed, and
 * a pathfinding failure where no usable route could be produced.
 *
 * @author lare96
 */
public enum PathResultType {

    /**
     * A full path to the requested destination was found.
     */
    COMPLETE,

    /**
     * A full path could not be found, but a usable partial path was produced.
     * <p>
     * This is useful when the pathfinder can move the mob closer to the target, such as toward a door, gate, obstacle,
     * or the closest reachable tile near the requested destination.
     */
    PARTIAL,

    /**
     * No path steps were produced because movement is not required.
     * <p>
     * This usually means the mob is already at, adjacent to, or otherwise within the required interaction range.
     */
    EMPTY,

    /**
     * No usable path could be produced.
     */
    FAILED
}