package io.luna.game.cache.map;

import com.google.common.base.MoreObjects;

public class MapTile {

    public static final int BLOCKED = 0x1;
    public static final int BRIDGE = 0x2;
    private final int x;
    private final int y;
    private final int z;

    private final int height;
    private final int overlay;
    private final int overlayType;
    private final int overlayOrientation;
    private final int attributes;
    private final int underlay;

    public MapTile(int x, int y, int z, int height, int overlay, int overlayType, int overlayOrientation, int attributes, int underlay) {
        this.x = x;
        this.y = y;
        this.z = z;
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
                .add("x", x)
                .add("y", y)
                .add("z", z)
                .add("height", height)
                .add("overlay", overlay)
                .add("overlayType", overlayType)
                .add("overlayOrientation", overlayOrientation)
                .add("attributes", attributes)
                .add("underlay", underlay)
                .toString();
    }

    public boolean isBlocked() {
        return (attributes & BLOCKED) == BLOCKED;
    }

    public boolean isBridge() {
        return (attributes & BRIDGE) == BRIDGE;
    }


    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int getHeight() {
        return height;
    }

    public int getOverlay() {
        return overlay;
    }

    public int getOverlayType() {
        return overlayType;
    }

    public int getOverlayOrientation() {
        return overlayOrientation;
    }

    public int getAttributes() {
        return attributes;
    }

    public int getUnderlay() {
        return underlay;
    }
}
