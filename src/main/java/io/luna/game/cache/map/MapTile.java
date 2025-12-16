package io.luna.game.cache.map;

import com.google.common.base.MoreObjects;
import io.luna.game.model.Position;
import io.luna.game.model.Region;

/**
 * Represents a single terrain tile decoded from the cache.
 * <p>
 * Each {@code MapTile} stores the raw terrain metadata used by the client:
 * <ul>
 *     <li>Its local offset inside a {@link Region} (0–63 × 0–63)</li>
 *     <li>Its plane (z-level)</li>
 *     <li>Height (world-space height value)</li>
 *     <li>Overlay/underlay IDs and shape/orientation</li>
 *     <li>Attribute flags (blocked, bridge, etc.)</li>
 * </ul>
 *
 * <p>
 * These values are later converted into world-space positions and collision data by the map loader and
 * {@code CollisionManager}.
 * </p>
 *
 * @author lare96
 */
public final class MapTile {

    /**
     * Bit flag: this tile is blocked for normal movement (solid terrain).
     */
    public static final int BLOCKED = 0x1;

    /**
     * Bit flag: this tile is part of a bridge structure.
     * <p>
     * Bridges are rendered on a higher plane but often apply collision one level below, requiring special handling
     * in the collision system.
     * </p>
     */
    public static final int BRIDGE = 0x2;

    /**
     * Local X offset inside the {@link Region} this tile belongs to (0–63).
     */
    private final int offsetX;

    /**
     * Local Y offset inside the {@link Region} this tile belongs to (0–63).
     */
    private final int offsetY;

    /**
     * Plane index (z-level) of this tile (0–3).
     */
    private final int plane;

    /**
     * Terrain height value for this tile, in world height units.
     */
    private final int height;

    /**
     * Overlay ID applied on top of this tile, or {@code 0} if none.
     */
    private final int overlay;

    /**
     * Overlay shape/type index (e.g. corner, diagonal, full).
     */
    private final int overlayType;

    /**
     * Overlay orientation (0–3), controlling rotation of the overlay shape.
     */
    private final int overlayOrientation;

    /**
     * Attribute bitmask for this tile (blocked, bridge, etc.).
     */
    private final int attributes;

    /**
     * Underlay ID for this tile (base floor texture).
     */
    private final int underlay;

    /**
     * Creates a new {@link MapTile} with the given decoded attributes.
     *
     * @param offsetX Local X offset inside the owning {@link Region}.
     * @param offsetY Local Y offset inside the owning {@link Region}.
     * @param plane Plane index (z-level) of this tile.
     * @param height Terrain height value for this tile.
     * @param overlay Overlay ID applied to this tile.
     * @param overlayType Overlay shape/type index.
     * @param overlayOrientation Overlay orientation (0–3).
     * @param attributes Attribute bitmask for this tile.
     * @param underlay Underlay ID for this tile.
     */
    public MapTile(int offsetX, int offsetY, int plane, int height, int overlay, int overlayType,
                   int overlayOrientation, int attributes, int underlay) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.plane = plane;
        this.height = height;
        this.overlay = overlay;
        this.overlayType = overlayType;
        this.overlayOrientation = overlayOrientation;
        this.attributes = attributes;
        this.underlay = underlay;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("offsetX", offsetX)
                .add("offsetY", offsetY)
                .add("plane", plane)
                .add("height", height)
                .add("overlay", overlay)
                .add("overlayType", overlayType)
                .add("overlayOrientation", overlayOrientation)
                .add("attributes", attributes)
                .add("underlay", underlay)
                .toString();
    }

    /**
     * Converts this tile's local offsets into an absolute world {@link Position}.
     *
     * @param region The region this tile belongs to.
     * @return A new {@link Position} representing the tile in absolute coordinates.
     */
    public Position getAbsPosition(Region region) {
        Position abs = region.getAbsPosition();
        return new Position(abs.getX() + offsetX, abs.getY() + offsetY, plane);
    }

    /**
     * Returns whether this tile is treated as fully blocked terrain.
     * <p>
     * A tile is considered blocked if:
     * <ul>
     *     <li>The {@link #BLOCKED} bit is set in {@link #attributes}</li>
     *     <li>It has a known water overlay ({@link #isWater()})</li>
     * </ul>
     *
     * @return {@code true} if the tile should block movement, otherwise {@code false}.
     */
    public boolean isBlocked() {
        return (attributes & BLOCKED) != 0 || isWater();
    }

    /**
     * Returns whether this tile is part of a bridge structure.
     *
     * @return {@code true} if the {@link #BRIDGE} bit is set, otherwise {@code false}.
     */
    public boolean isBridge() {
        return (attributes & BRIDGE) != 0;
    }

    /**
     * Returns whether this tile uses a water overlay texture.
     *
     * @return {@code true} if the overlay is the water texture, otherwise {@code false}.
     */
    public boolean isWater() {
        return overlay == 6;
    }

    /**
     * Returns the local X offset of this tile inside its region (0–63).
     */
    public int getOffsetX() {
        return offsetX;
    }

    /**
     * Returns the local Y offset of this tile inside its region (0–63).
     */
    public int getOffsetY() {
        return offsetY;
    }

    /**
     * Returns the plane index (z-level) of this tile.
     */
    public int getPlane() {
        return plane;
    }

    /**
     * Returns the terrain height value of this tile.
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns the overlay ID applied to this tile.
     */
    public int getOverlay() {
        return overlay;
    }

    /**
     * Returns the overlay shape/type index for this tile.
     */
    public int getOverlayType() {
        return overlayType;
    }

    /**
     * Returns the overlay orientation (0–3) for this tile.
     */
    public int getOverlayOrientation() {
        return overlayOrientation;
    }

    /**
     * Returns the raw attribute bitmask for this tile.
     */
    public int getAttributes() {
        return attributes;
    }

    /**
     * Returns the underlay ID applied to this tile.
     */
    public int getUnderlay() {
        return underlay;
    }
}
