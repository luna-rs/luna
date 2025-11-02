package io.luna.game.model.mob.overlay;

import io.luna.game.model.mob.Player;

/**
 * An abstract, stateful overlay that can be shown on a player's screen.
 * <p>
 * Overlays model UI surfaces such as standard interfaces (dialogues), walkable interfaces, and input boxes.
 * Implementations are responsible for sending the appropriate client packets inside {@link #open(Player)}.
 *
 * @author lare96
 */
public abstract class AbstractOverlay {

    /**
     * The overlay type (e.g., STANDARD, WALKABLE, INPUT).
     */
    private final OverlayType overlayType;

    /**
     * Current lifecycle state of this overlay.
     */
    private InterfaceState state = InterfaceState.IDLE;

    /**
     * Creates a new {@link AbstractOverlay} with the given type.
     *
     * @param type The overlay type classification.
     */
    public AbstractOverlay(OverlayType type) {
        this.overlayType = type;
    }

    /**
     * Sends the client packets required to present this overlay to {@code player}.
     * <p>
     * This is invoked by {@link #setOpened(Player)} after {@link #onOpen(Player)}. Implementations should assume
     * they are called only when the overlay is transitioning from a non-open state to {@link InterfaceState#OPEN}.
     *
     * @param player The player receiving the overlay.
     */
    public abstract void open(Player player);

    /**
     * Hook invoked after the overlay has transitioned to {@link InterfaceState#CLOSED}.
     * <p>
     * Use this to release resources, clear server-side flags, or send additional cleanup packets.
     * Called by {@link #setClosed(Player, AbstractOverlay)} regardless of whether a replacement exists.
     *
     * @param player The player for whom the overlay was closed.
     */
    public void onClose(Player player) {
    }

    /**
     * Hook invoked just before {@link #open(Player)} when transitioning to {@link InterfaceState#OPEN}.
     * <p>
     * Use this to prepare dynamic content (e.g., strings, config vars) or to perform access checks.
     *
     * @param player The player for whom the overlay is being opened.
     */
    public void onOpen(Player player) {

    }

    /**
     * Hook invoked when this overlay is being replaced by another overlay.
     * <p>
     * This is called during {@link #setClosed(Player, AbstractOverlay)} <em>before</em> {@link #onClose(Player)}.
     * It is not called when the overlay is simply closed without a replacement.
     *
     * @param player  The player affected by the replacement.
     * @param replace The overlay that will replace this one (never {@code null} in this callback).
     */
    public void onReplace(Player player, AbstractOverlay replace) {
    }

    /**
     * Transitions this overlay to {@link InterfaceState#CLOSED} and fires the appropriate hooks.
     *
     * @param player  The player for whom the overlay is closing.
     * @param replace The replacement overlay, or {@code null} if the overlay is simply closing.
     */
    final void setClosed(Player player, AbstractOverlay replace) {
        if (isOpen()) {
            state = InterfaceState.CLOSED;
            if (replace != null) {
                onReplace(player, replace);
            }
            onClose(player);
        }
    }

    /**
     * Transitions this overlay to {@link InterfaceState#OPEN} and fires the appropriate hooks.
     *
     * @param player The player for whom the overlay is opening.
     */
    final void setOpened(Player player) {
        if (!isOpen()) {
            state = InterfaceState.OPEN;
            onOpen(player);
            open(player);
        }
    }

    /**
     * Determines if this interface is open and viewable on the Player's screen.
     *
     * @return {@code true} if this interface is open.
     */
    public final boolean isOpen() {
        return state == InterfaceState.OPEN;
    }

    /**
     * @return The interface type.
     */
    public final OverlayType getOverlayType() {
        return overlayType;
    }

    /**
     * @return The interface state.
     */
    public final InterfaceState getState() {
        return state;
    }
}
