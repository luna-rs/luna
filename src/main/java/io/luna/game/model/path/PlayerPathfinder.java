package io.luna.game.model.path;

import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionManager;

/**
 * A deterministic {@link AStarPathfinder} implementation used for all player movement.
 * <p>
 * Unlike {@link BotPathfinder}, which introduces controlled randomness to simulate human-like
 * variation, {@code PlayerPathfinder} uses strict and predictable path computation. All searches
 * use the {@link Heuristic#CHEBYSHEV} heuristic, which exactly matches RuneScape's 8-direction
 * movement model where diagonal and cardinal steps share identical traversal cost.
 * </p>
 *
 * @author lare96
 */
public class PlayerPathfinder extends AStarPathfinder<Position> {

    /**
     * The plane (height level) this pathfinder operates on.
     */
    private final int plane;

    /**
     * Creates a new {@link PlayerPathfinder}.
     *
     * @param collisionManager The {@link CollisionManager} used for traversability checks.
     * @param plane The plane paths will be routed on.
     */
    public PlayerPathfinder(CollisionManager collisionManager, int plane) {
        super(collisionManager);
        this.plane = plane;
    }

    @Override
    public boolean isTraversable(Position position, Position adjacent, Direction direction) {
        return traversable(position, direction);
    }

    @Override
    public Position createNeighbor(int nextX, int nextY) {
        return new Position(nextX, nextY, plane);
    }

    @Override
    public Heuristic getHeuristic() {
        return Heuristic.CHEBYSHEV;
    }
}
