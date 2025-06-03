package io.luna.game.model.map;

import io.luna.game.model.Location;
import io.luna.game.model.Position;
import io.luna.game.model.Region;
import io.luna.game.model.map.builder.DynamicMapPalette;

import java.util.Objects;
import java.util.Set;

/**
 * Represents a piece of empty map space that is reserved for {@link DynamicMap} instances.
 * </p>
 * These spaces are 128x128 in size and are made up of four 64x64 regions.
 * <li>One {@link #primary} region which will be used for rs2 world -> instance position calculations, to identify
 * the space, and serves as the base region for the palette.</li>
 * <li>Three padding regions which are generated from the primary region. They consist of one to the top of the primary
 * region, one to the right, and one to the top right. They ensure this space is large enough for an entire
 * {@link DynamicMapPalette} to be loaded within it.</li>
 */
public final class DynamicMapSpace implements Location {

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
