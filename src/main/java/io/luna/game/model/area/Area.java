package io.luna.game.model.area;

import com.google.common.collect.ImmutableList;
import io.luna.game.model.Locatable;
import io.luna.game.model.Position;
import io.luna.util.RandomUtils;

import java.awt.*;
import java.util.List;

/**
 * A geometric region of the game world composed of tile {@link Position} coordinates.
 * <p>
 * Concrete implementations include:
 * <ul>
 *     <li>{@link SimpleBoxArea}: an axis-aligned rectangle (inclusive bounds)</li>
 *     <li>{@link PolygonArea}: an arbitrary polygon defined by vertices</li>
 *     <li>{@link CircularArea}: a circle defined by a center and radius</li>
 * </ul>
 * <p>
 * Areas provide:
 * <ul>
 *     <li>containment checks via {@link #contains(Position)}</li>
 *     <li>enumeration/caching of all contained positions via {@link #getPositions()}</li>
 *     <li>random sampling via {@link #randomPosition()}</li>
 * </ul>
 * <p>
 * <b>Anchor position:</b> Each area has an "anchor" used to satisfy {@link Locatable#absLocation()} and to provide
 * {@link #getX()} / {@link #getY()} coordinates. If not set explicitly, it is lazily initialized to a random
 * position within the area.
 *
 * @author lare96
 */
public abstract class Area implements Locatable {

    /**
     * Creates a rectangular {@link Area} using south-west and north-east coordinates.
     * <p>
     * Coordinates are normalized so callers may pass corners in any order. The resulting {@link SimpleBoxArea}
     * uses inclusive bounds.
     *
     * @param southWestX The first x coordinate (treated as south-west x after normalization).
     * @param southWestY The first y coordinate (treated as south-west y after normalization).
     * @param northEastX The second x coordinate (treated as north-east x after normalization).
     * @param northEastY The second y coordinate (treated as north-east y after normalization).
     * @return A new {@link SimpleBoxArea} instance.
     */
    public static SimpleBoxArea of(int southWestX, int southWestY, int northEastX, int northEastY) {
        int normalizedSwX = Math.min(southWestX, northEastX);
        int normalizedSwY = Math.min(southWestY, northEastY);
        int normalizedNeX = Math.max(southWestX, northEastX);
        int normalizedNeY = Math.max(southWestY, northEastY);

        SimpleBoxArea box = new SimpleBoxArea(normalizedSwX, normalizedSwY, normalizedNeX, normalizedNeY);
        box.setAnchorPosition(new Position(normalizedSwX, normalizedSwY));
        return box;
    }

    /**
     * Creates a square {@link SimpleBoxArea} centered around {@code center} with a tile radius.
     * <p>
     * The produced box includes the edges at {@code center +/- radius}.
     *
     * @param center The center position.
     * @param radius The radius in tiles.
     * @return A new {@link SimpleBoxArea}.
     */
    public static SimpleBoxArea of(Position center, int radius) {
        SimpleBoxArea box = of(
                center.getX() - radius, center.getY() - radius,
                center.getX() + radius, center.getY() + radius
        );
        box.setAnchorPosition(center);
        return box;
    }

    /**
     * Creates a polygonal {@link Area} from an ordered vertex list.
     *
     * @param vertices The vertices that form the polygon outline.
     * @return A new {@link PolygonArea}.
     */
    public static PolygonArea of(List<Point> vertices) {
        return new PolygonArea(vertices);
    }

    /**
     * Creates a {@link CircularArea}.
     *
     * @param center The center point.
     * @param radius The radius in tiles (must be {@code >= 1}).
     * @return A new {@link CircularArea}.
     */
    public static CircularArea of(Point center, int radius) {
        CircularArea area = new CircularArea(center.x, center.y, radius);
        area.setAnchorPosition(new Position(center.x, center.y));
        return area;
    }

    /**
     * Cached immutable list of every position contained by this area.
     * <p>
     * Built lazily on first call to {@link #getPositions()}.
     */
    private ImmutableList<Position> positions;

    /**
     * Anchor position used to satisfy {@link #absLocation()} and {@link Locatable} coordinates.
     */
    private Position anchorPosition;

    /**
     * Returns the area anchor position.
     *
     * @return The anchor position.
     */
    @Override
    public final Position absLocation() {
        return getAnchorPosition();
    }

    @Override
    public int getX() {
        return getAnchorPosition().getX();
    }

    @Override
    public int getY() {
        return getAnchorPosition().getY();
    }

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);

    /**
     * Returns the total number of contained tile coordinates.
     * <p>
     * Implementations may return a cached/enumerated value or an approximation depending on shape.
     *
     * @return The number of tiles in this area.
     */
    public abstract int size();

    /**
     * Retrieves a random {@link Position} inside this area.
     * <p>
     * The default implementation samples uniformly from the enumerated positions list. Implementations may override
     * with a faster O(1) sampler (see {@link SimpleBoxArea#randomPosition()}).
     *
     * @return A random position contained by this area.
     */
    public Position randomPosition() {
        return RandomUtils.random(computePositions());
    }

    /**
     * Computes every {@link Position} contained within this area.
     * <p>
     * This method does not cache; use {@link #getPositions()} for cached access.
     *
     * @return An immutable list of positions in this area.
     */
    abstract ImmutableList<Position> computePositions();

    /**
     * Returns every {@link Position} contained within this area, building and caching the list if needed.
     *
     * @return The cached immutable list of positions.
     */
    public final ImmutableList<Position> getPositions() {
        if (positions == null) {
            positions = computePositions();
        }
        return positions;
    }

    /**
     * Returns the anchor position.
     * <p>
     * If the anchor was never set, it is initialized lazily to {@link #randomPosition()}.
     *
     * @return The anchor position.
     */
    public Position getAnchorPosition() {
        if (anchorPosition == null) {
            anchorPosition = randomPosition();
        }
        return anchorPosition;
    }

    /**
     * Sets the anchor position for this area.
     * <p>
     * The anchor must be contained within this area; invalid positions are ignored.
     *
     * @param position The new anchor position.
     */
    public void setAnchorPosition(Position position) {
        if (contains(position)) {
            anchorPosition = position;
        }
    }
}
