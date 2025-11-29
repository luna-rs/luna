package io.luna.game.model.path;

import io.luna.game.model.collision.CollisionManager;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A bot-oriented variant of {@link PlayerPathfinder} that uses a randomized A* search.
 * <p>
 * This implementation introduces two naturalizing effects:
 * <ul>
 *     <li><b>Random bias cost:</b> A small random weight is applied to each node expansion.</li>
 *     <li><b>Random heuristic:</b> A random heuristic is chosen on each pathfinding request.</li>
 * </ul>
 * The resulting paths remain valid but exhibit organic detours and varied shapes, preventing bots from all following
 * identical tile lines.
 *
 * @author lare96
 */
public final class BotPathfinder extends PlayerPathfinder {

    /**
     * The plane paths will be routed on.
     */
    private final int plane;

    /**
     * Creates a new {@link BotPathfinder}.
     *
     * @param collisionManager The {@link CollisionManager} used for traversability checks.
     * @param plane The plane paths will be routed on.
     */
    public BotPathfinder(CollisionManager collisionManager, int plane) {
        super(collisionManager, plane);
        this.plane = plane;
    }

    @Override
    public Heuristic getHeuristic() {
        // TODO Cache when all TODOs are done to ensure thread safety (pass as parameter).
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 60) {
            // TODO The most intelligent bots use this heuristic.
            return Heuristic.EUCLIDEAN;
        } else if (roll < 85) {
            // TODO The bots of average intelligence tend to use this heuristic.
            return Heuristic.CHEBYSHEV;
        } else {
            // TODO The lowest intelligence bots use this heuristic.
            return Heuristic.MANHATTAN;
        }
    }

    @Override
    public int adjustHeuristic(int estimate) {
        // TODO Greater intelligence = less likely to deviate from their heuristic estimation.
        return estimate + (ThreadLocalRandom.current().nextBoolean() ? 0 : ThreadLocalRandom.current().nextInt(0, 3));
    }
}
