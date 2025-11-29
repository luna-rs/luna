package io.luna.game.model.path;

import io.luna.game.model.Direction;
import io.luna.game.model.Locatable;
import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionManager;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

/**
 * A {@link GamePathfinder} implementation that uses the A* search algorithm to find a path.
 * <p>
 * Subclasses are responsible for:
 * </p>
 *
 * <ul>
 *     <li>Defining traversability rules via {@link #isTraversable(Locatable, Locatable, Direction)}.</li>
 *     <li>Creating neighbor locatables via {@link #createNeighbor(int, int)}.</li>
 *     <li>Providing a {@link Heuristic} via {@link #getHeuristic()}.</li>
 * </ul>
 *
 * @param <T> The locatable type being searched over (e.g., {@link Position}).
 * @author lare96
 */
public abstract class AStarPathfinder<T extends Locatable> extends GamePathfinder<T> {

    /**
     * Creates a new  {@link AStarPathfinder}.
     *
     * @param collisionManager The collision manager used to determine whether tiles/steps are blocked.
     */
    public AStarPathfinder(CollisionManager collisionManager) {
        super(collisionManager);
    }

    @Override
    public final Deque<T> find(T origin, T target) {
        Heuristic heuristic = getHeuristic();
        Map<T, Node<T>> nodes = new HashMap<>();
        Node<T> start = new Node<>(origin);
        Node<T> end = new Node<>(target);
        nodes.put(origin, start);
        nodes.put(target, end);

        Set<Node<T>> open = new HashSet<>();
        Queue<Node<T>> sorted = new PriorityQueue<>();
        open.add(start);
        sorted.add(start);

        do {
            if (nodes.size() >= 250_000) {
                break;
            }

            Node<T> active = getCheapest(sorted);
            if (active == null) {
                break;
            }

            T locatable = active.getLocatable();
            if (locatable.equals(target)) {
                break;
            }

            open.remove(active);
            active.close();

            int x = locatable.getX();
            int y = locatable.getY();

            for (int nextX = x - 1; nextX <= x + 1; nextX++) {
                for (int nextY = y - 1; nextY <= y + 1; nextY++) {
                    if (nextX == x && nextY == y) {
                        continue;
                    }
                    if (nextX < 0 || nextY < 0) {
                        continue;
                    }

                    T adjacent = createNeighbor(nextX, nextY);
                    if (adjacent == null) {
                        continue;
                    }

                    Direction direction = Direction.between(x, y, nextX, nextY);
                    if (isTraversable(locatable, adjacent, direction)) {
                        Node<T> neighbor = nodes.computeIfAbsent(adjacent, Node::new);
                        compare(active, neighbor, open, sorted, heuristic);
                    }
                }
            }
        } while (!open.isEmpty());

        nodes.clear();

        Deque<T> shortest = new ArrayDeque<>();
        Node<T> active = end;

        if (active.hasParent()) {
            T locatable = active.getLocatable();
            while (!origin.equals(locatable)) {
                shortest.addFirst(locatable);
                active = active.getParent();
                locatable = active.getLocatable();
            }
        }
        return shortest;
    }

    /**
     * Determines if a move from {@code locatable} to {@code adjacent} in the given {@code direction} is traversable.
     * 
     * @param locatable The current location.
     * @param adjacent The candidate neighboring location.
     * @param direction The direction from the current location to the neighbor.
     * @return {@code true} if movement to {@code adjacent} is allowed, otherwise {@code false}.
     */
    public abstract boolean isTraversable(T locatable, T adjacent, Direction direction);

    /**
     * Returns the heuristic used for estimating costs between locatables.
     * 
     * @return The heuristic implementation.
     */
    public abstract Heuristic getHeuristic();

    /**
     * Creates a neighbor locatable given raw coordinates.
     *
     * @param nextX The x-coordinate of the neighbor.
     * @param nextY The y-coordinate of the neighbor.
     * @return A new locatable representing the neighbor, or {@code null} if invalid.
     */
    public abstract T createNeighbor(int nextX, int nextY);

    /**
     * Adjusts the heuristic estimate, allowing subclasses to globally bias or scale the values produced 
     * by {@link Heuristic#estimate(Locatable, Locatable)}.
     *
     * @param estimate The raw heuristic estimate.
     * @return The adjusted heuristic value.
     */
    public int adjustHeuristic(int estimate) {
        return estimate;
    }

    /**
     * Compares the specified neighbor {@link Node} to the active node, updating its cost and parent if a cheaper path
     * is discovered, and adding it to the open sets as needed.
     *
     * @param active The node currently being expanded.
     * @param neighbor The neighboring node being evaluated.
     * @param open The set of open nodes.
     * @param sorted The priority queue ordered by node cost.
     * @param heuristic The heuristic used for estimating relative movement cost.
     */
    private void compare(Node<T> active,
                         Node<T> neighbor,
                         Set<Node<T>> open,
                         Queue<Node<T>> sorted,
                         Heuristic heuristic) {
        int estimate = heuristic.estimate(active.getLocatable(), neighbor.getLocatable());
        estimate = adjustHeuristic(estimate);
        int cost = active.getCost() + estimate;

        if (neighbor.getCost() > cost) {
            open.remove(neighbor);
            neighbor.close();
        } else if (!neighbor.isClosed() && !open.contains(neighbor)) {
            neighbor.setCost(cost);
            neighbor.setParent(active);
            open.add(neighbor);
            sorted.add(neighbor);
        }
    }

    /**
     * Retrieves the cheapest open {@link Node} from the given {@link Queue}, polling any nodes that have already
     * been closed.
     *
     * @param nodes The priority queue of nodes.
     * @return The cheapest non-closed node, or {@code null} if none exist.
     */
    private Node<T> getCheapest(Queue<Node<T>> nodes) {
        Node<T> node = nodes.peek();
        while (node != null && node.isClosed()) {
            nodes.poll();
            node = nodes.peek();
        }
        return node;
    }
}
