package io.luna.game.model.mob.overlay;

/**
 * Defines the three primary categories of overlays (interfaces) that can appear on a player's screen.
 * <p>
 * Each {@link OverlayType} determines how the client manages visibility, stacking rules, and whether the interface
 * persists during player movement.
 *
 * @author lare96
 */
public enum OverlayType {

    /**
     * A standard, modal interface such as a dialogue, shop, or bank.
     * <p>
     * Only one standard widget can be open at a time; opening another will close the existing one.
     */
    WIDGET_STANDARD,

    /**
     * A walkable overlay drawn above the 3D scene (e.g., wilderness level or status indicators).
     * <p>
     * Walkable widgets remain visible while moving and can coexist with all other overlay types.
     */
    WIDGET_WALKABLE,

    /**
     * An input overlay that accepts text or number entry.
     * <p>
     * These overlays have no fixed interface identifier and do not interfere with other overlays.
     */
    INPUT
}
