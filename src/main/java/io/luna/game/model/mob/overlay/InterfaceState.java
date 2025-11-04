package io.luna.game.model.mob.overlay;

/**
 * Represents the lifecycle state of an {@link AbstractOverlay} (interface) instance.
 * <p>
 * Each overlay transitions through these states as it is opened or closed on a player's screen.
 * The state is managed internally by {@link AbstractOverlaySet} to ensure correct invocation
 * of {@link AbstractOverlay#onOpen}, {@link AbstractOverlay#onClose}, and {@link AbstractOverlay#onReplace}.
 * <p>
 * <b>State progression</b>
 * <ul>
 *   <li>{@link #IDLE} — The default state when the overlay is first created and not yet opened.</li>
 *   <li>{@link #OPEN} — The overlay is currently visible and active on the player's client.</li>
 *   <li>{@link #CLOSED} — The overlay was previously open but has since been removed from view.</li>
 * </ul>
 * <p>
 * Once closed, an overlay instance typically should not be reused; instead, a new instance should be created
 * and opened again through {@link AbstractOverlaySet#open(AbstractOverlay)}.
 *
 * @author lare96
 */
public enum InterfaceState {

    /**
     * The default state for all overlay instances when first constructed.
     */
    IDLE,

    /**
     * The overlay is open and visible on the player's screen.
     */
    OPEN,

    /**
     * The overlay has been closed and removed from the player's view.
     */
    CLOSED
}