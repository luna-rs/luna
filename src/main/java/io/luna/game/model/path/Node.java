package io.luna.game.model.path;

import com.google.common.base.MoreObjects;
import io.luna.game.model.Locatable;

/**
 * Represents a node in the A* search graph, wrapping a {@link Locatable} with pathfinding metadata. Nodes are compared
 * using their current cost, and they are considered equal if they wrap the same {@link Locatable} (i.e., equality is
 * based on location, not cost or parent).
 *
 * @param <T> The locatable type represented by this node.
 * @author lare96
 */
final class Node<T extends Locatable> implements Comparable<Node<T>> {

    /**
     * The locatable represented by this node.
     */
    private final T locatable;

    /**
     * The cost associated with this node as understood by the pathfinding algorithm.
     */
    private int cost;

    /**
     * Whether this node has been closed (fully expanded) by the search.
     */
    private boolean closed;

    /**
     * The parent node in the discovered path, or {@code null} if this node has no parent.
     */
    private Node<T> parent;

    /**
     * Creates a new {@link Node} with the specified {@link Locatable} and an initial cost of {@code 0}.
     *
     * @param locatable The locatable this node represents.
     */
    public Node(T locatable) {
        this(locatable, 0);
    }

    /**
     * Creates a new {@link Node} with the specified {@link Locatable} and cost.
     *
     * @param locatable The {@link Locatable} this node represents.
     * @param cost The initial cost of this node.
     */
    public Node(T locatable, int cost) {
        this.locatable = locatable;
        this.cost = cost;
    }

    @Override
    public int compareTo(Node<T> other) {
        return Integer.compare(cost, other.cost);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Node) {
            Node<T> other = (Node<T>) obj;
            return locatable.equals(other.locatable);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return locatable.hashCode();
    }

    /**
     * Marks this node as closed. Once closed, the node should not be added back into the open set or
     * expanded again.
     */
    public void close() {
        closed = true;
    }

    /**
     * Returns the current cost of this node.
     *
     * @return The cost value.
     */
    public int getCost() {
        return cost;
    }

    /**
     * Returns the parent of this node, or {@code null} if it has no parent. The parent chain is used to reconstruct
     * the final path once the target node is reached.
     *
     * @return The parent node, or {@code null} if none has been set.
     */
    public Node<T> getParent() {
        return parent;
    }

    /**
     * Returns the {@link Locatable} represented by this node.
     *
     * @return The underlying {@link Locatable}.
     */
    public T getLocatable() {
        return locatable;
    }

    /**
     * Returns whether this node has a parent assigned.
     *
     * @return {@code true} if a parent has been set, otherwise {@code false}.
     */
    public boolean hasParent() {
        return parent != null;
    }

    /**
     * Returns whether this node has been closed by the search.
     *
     * @return {@code true} if this node is closed, otherwise {@code false}.
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Sets the cost associated with this node.
     *
     * @param cost The new cost value.
     */
    public void setCost(int cost) {
        this.cost = cost;
    }

    /**
     * Sets the parent node for this node.
     *
     * @param parent The parent node, or {@code null} to clear the parent.
     */
    public void setParent(Node<T> parent) {
        this.parent = parent;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("position", locatable)
                .add("closed", closed)
                .add("cost", cost)
                .toString();
    }
}