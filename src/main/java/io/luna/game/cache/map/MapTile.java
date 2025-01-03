package io.luna.game.cache.map;

import com.google.common.base.MoreObjects;
import io.luna.game.model.Region;

/**
 * Represents tiles on the map and their attributes, that were decoded from the cache.
 *
 * @author lare96
 */
public final class MapTile {

    /**
     * A bit flag determining if the tile is blocked.
     */
    public static final int BLOCKED = 0x1;

    /**
     * A bit flag determining if the tile is a bridge tile.
     */
    public static final int BRIDGE = 0x2;

    /**
     * The {@code x} offset of this tile from its {@link Region}.
     */
    private final int offsetX;

    /**
     * The {@code y} offset of this tile from its {@link Region}.
     */
    private final int offsetY;

    /**
     * The plane of this tile.
     */
    private final int plane;

    /**
     * The tile height.
     */
    private final int height;

    /**
     * The tile overlay.
     */
    private final int overlay;

    /**
     * The tile overlay type.
     */
    private final int overlayType;

    /**
     * The tile overlay direction.
     */
    private final int overlayOrientation;

    /**
     * The tile attributes.
     */
    private final int attributes;

    /**
     * The tile underlay.
     */
    private final int underlay;

    /**
     * Creates a new {@link MapTile}.
     *
     * @param offsetX The {@code x} offset of this tile from its {@link Region}.
     * @param offsetY The {@code y} offset of this tile from its {@link Region}.
     * @param plane The plane of this tile.
     * @param height The tile height.
     * @param overlay The tile overlay.
     * @param overlayType The tile overlay type.
     * @param overlayOrientation The tile overlay direction.
     * @param attributes The tile attributes.
     * @param underlay The tile underlay.
     */
    public MapTile(int offsetX, int offsetY, int plane, int height, int overlay, int overlayType, int overlayOrientation, int attributes, int underlay) {
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
     * @return If {@link #attributes} contains the {@link #BLOCKED} bit flag.
     */
    public boolean isBlocked() {
        return (attributes & BLOCKED) == BLOCKED;
    }

    /**
     * @return If {@link #attributes} contains the {@link #BRIDGE} bit flag.
     */
    public boolean isBridge() {
        return (attributes & BRIDGE) == BRIDGE;
    }

    /**
     * @return The {@code x} offset of this tile from its {@link Region}.
     */
    public int getOffsetX() {
        return offsetX;
    }

    /**
     * @return The {@code y} offset of this tile from its {@link Region}.
     */
    public int getOffsetY() {
        return offsetY;
    }

    /**
     * @return The plane of this tile.
     */
    public int getPlane() {
        return plane;
    }

    /**
     * @return The tile height.
     */
    public int getHeight() {
        return height;
    }

    /**
     * @return The tile overlay.
     */
    public int getOverlay() {
        return overlay;
    }

    /**
     * @return The tile overlay type.
     */
    public int getOverlayType() {
        return overlayType;
    }

    /**
     * @return The tile overlay direction.
     */
    public int getOverlayOrientation() {
        return overlayOrientation;
    }

    /**
     * @return The tile attributes.
     */
    public int getAttributes() {
        return attributes;
    }

    /**
     * @return The tile underlay.
     */
    public int getUnderlay() {
        return underlay;
    }
}
