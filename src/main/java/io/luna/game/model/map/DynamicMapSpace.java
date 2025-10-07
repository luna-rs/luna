package io.luna.game.model.map;

import io.luna.game.model.Locatable;
import io.luna.game.model.Position;
import io.luna.game.model.Region;

import java.util.Objects;
import java.util.Set;

/**
 * Represents a 128x128 reserved world area for instancing, comprised of four 64x64 regions:
 *
 * <ul>
 *     <li>1 primary region (anchor for palette mapping)</li>
 *     <li>3 padding regions: top, right, and top-right</li>
 * </ul>
 *
 * These extra regions ensure room for any 64x64 dynamic chunk layout and prevent conflicts
 * between concurrently running instances.
 *
 * <p>All region math (e.g. {@link #contains(Position)}) automatically checks all four regions.</p>
 */
public final class DynamicMapSpace implements Locatable {

    // TODO store mappings of all dynamic map chunks -> their real chunk?

    /**
     * The primary region.
     */
    private final Region primary;

    /**
     * The top padding region.
     */
    private final Region paddingTop;

    /**
     * The right padding region.
     */
    private final Region paddingRight;

    /**
     * The top-right padding region.
     */
    private final Region paddingTopRight;

    /**
     * An immutable set of all regions this space represents.
     */
    private final Set<Region> regions;

    /**
     * An immutable set of all <strong>padding</strong> regions this space represents. This does <strong>not</strong>
     * include the primary region.
     */
    private final Set<Region> paddingRegions;

    /**
     * Creates a new {@link DynamicMapSpace}.
     *
     * @param primary The primary region.
     */
    public DynamicMapSpace(Region primary) {
        this.primary = primary;
        paddingTop = new Region(primary.getX(), primary.getY() + 1);
        paddingRight = new Region(primary.getX() + 1, primary.getY());
        paddingTopRight = new Region(primary.getX() + 1, primary.getY() + 1);
        regions = Set.of(primary, paddingTop, paddingRight, paddingTopRight);
        paddingRegions = Set.of(paddingTop, paddingRight, paddingTopRight);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DynamicMapSpace)) return false;
        DynamicMapSpace space = (DynamicMapSpace) o;
        return Objects.equals(primary, space.primary);
    }

    @Override
    public int hashCode() {
        return primary.hashCode();
    }

    @Override
    public boolean contains(Position position) {
        return primary.contains(position) ||
                paddingTop.contains(position) ||
                paddingRight.contains(position) ||
                paddingTopRight.contains(position);
    }

    @Override
    public Position location() {
        // todo change to spawn location?
        return primary.getAbsPosition();
    }

    /**
     * Determines if this space is within a one region radius to {@code other}.
     *
     * @param other The other space.
     * @return {@code true} if this space is near {@code other}.
     */
    public boolean isNear(DynamicMapSpace other) {
        return primary.isWithinDistance(other.primary, 1) ||
                primary.isWithinDistance(other.paddingTop, 1) ||
                primary.isWithinDistance(other.paddingRight, 1) ||
                primary.isWithinDistance(other.paddingTopRight, 1) ||

                paddingTop.isWithinDistance(other.primary, 1) ||
                paddingTop.isWithinDistance(other.paddingTop, 1) ||
                paddingTop.isWithinDistance(other.paddingRight, 1) ||
                paddingTop.isWithinDistance(other.paddingTopRight, 1) ||

                paddingRight.isWithinDistance(other.primary, 1) ||
                paddingRight.isWithinDistance(other.paddingTop, 1) ||
                paddingRight.isWithinDistance(other.paddingRight, 1) ||
                paddingRight.isWithinDistance(other.paddingTopRight, 1) ||

                paddingTopRight.isWithinDistance(other.primary, 1) ||
                paddingTopRight.isWithinDistance(other.paddingTop, 1) ||
                paddingTopRight.isWithinDistance(other.paddingRight, 1) ||
                paddingTopRight.isWithinDistance(other.paddingTopRight, 1);
    }

    /**
     * @return The primary region.
     */
    public Region getPrimary() {
        return primary;
    }

    /**
     * @return The top padding region.
     */
    public Region getPaddingTop() {
        return paddingTop;
    }

    /**
     * @return The right padding region.
     */
    public Region getPaddingRight() {
        return paddingRight;
    }

    /**
     * @return The top-right padding region.
     */
    public Region getPaddingTopRight() {
        return paddingTopRight;
    }

    /**
     * @return An immutable set of all regions this space represents.
     */
    public Set<Region> getAllRegions() {
        return regions;
    }

    /**
     * @return An immutable set of all <strong>padding</strong> regions this space represents. This does
     * <strong>not</strong> include the primary region.
     */
    public Set<Region> getAllPaddingRegions() {
        return paddingRegions;
    }
}
