package io.luna.game.model.path;

import io.luna.game.model.Locatable;

/**
 * A family of heuristics used by the {@link AStarPathfinder} to estimate the distance between two {@link Locatable}s.
 * <p>
 * Each heuristic provides its own balance of accuracy, computational cost, and movement-model realism. The choice of
 * heuristic directly affects path optimality, naturalism, and CPU usage.
 * </p>
 *
 * @author lare96
 */
public enum Heuristic {

    /**
     * <strong>Chebyshev Distance</strong>: {@code max(|dx|, |dy|)}
     * <p>
     * Chebyshev returns the <em>exact</em> minimal number of steps required on an 8-direction grid. Because
     * it neither underestimates nor overestimates, it is both <strong>admissible</strong> and
     * <strong>consistent</strong>, making it the safest and fastest default heuristic for A* in normal gameplay.
     * </p>
     */
    CHEBYSHEV {
        @Override
        public int estimate(Locatable current, Locatable goal) {
            int dx = Math.abs(current.getX() - goal.getX());
            int dy = Math.abs(current.getY() - goal.getY());
            return Math.max(dx, dy);
        }
    },

    /**
     * <strong>Euclidean Distance</strong>: {@code ceil(sqrt(dx² + dy²))}
     * <p>
     * The Euclidean heuristic measures “as-the-crow-flies” geometric distance. It assumes diagonals cost more than
     * regular directions. Euclidean tends to generate slightly more curved or smoothed paths. Useful for bots or
     * “intelligent” actors, but not for strict correctness in the RS2 world.
     * </p>
     */
    EUCLIDEAN {
        @Override
        public int estimate(Locatable current, Locatable target) {
            int deltaX = current.getX() - target.getX();
            int deltaY = current.getY() - target.getY();
            return (int) Math.ceil(Math.sqrt(deltaX * deltaX + deltaY * deltaY));
        }
    },

    /**
     * <strong>Manhattan Distance</strong>: {@code |dx| + |dy|}
     * <p>
     * In an 8-direction movement system, Manhattan is admissible (never overestimates) but tends to
     * <em>underestimate</em> by a significant margin. This causes A* to expand more nodes than other heuristics,
     * running slower but still producing correct results. Manhattan paths look “boxy” and expand in a
     * diamond-shaped pattern. This can be useful for deliberately imperfect bot movement or weighted maze searches.
     * </p>
     */
    MANHATTAN {
        @Override
        public int estimate(Locatable current, Locatable goal) {
            int dx = Math.abs(current.getX() - goal.getX());
            int dy = Math.abs(current.getY() - goal.getY());
            return dx + dy;
        }
    };

    /**
     * Estimates the heuristic distance between two locatables.
     *
     * @param current The current position.
     * @param target The target position.
     * @return The heuristic value for this pair of points.
     */
    public abstract int estimate(Locatable current, Locatable target);
}
