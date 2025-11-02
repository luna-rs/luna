package io.luna.game.model.mob.overlay;

import io.luna.game.action.ActionType;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.CloseWindowsMessageWriter;
import io.luna.net.msg.out.WalkableInterfaceMessageWriter;

import java.util.EnumMap;

/**
 * Manages the set of {@link AbstractOverlay} instances currently displayed on a single {@link Player}'s screen.
 * <p>
 * This class is the single authority for opening, replacing, and closing overlays per overlay {@link OverlayType}.
 * It enforces client packet side effects (e.g., close-windows, clear walkable) and coordinates action interruption
 * for user-facing windows when applicable.
 * <p>
 * <b>Rules by type</b>
 * <ul>
 *   <li>{@link OverlayType#WIDGET_STANDARD} and {@link OverlayType#INPUT} are considered "windows". They are closed
 *       together by {@link #closeWindows()} which also interrupts {@link ActionType#WEAK} actions and clears dialogues.</li>
 *   <li>{@link OverlayType#WIDGET_WALKABLE} is a persistent overlay on the game scene and is closed separately by
 *       {@link #closeWalkable()}.</li>
 * </ul>
 *
 * @author lare96
 */
public final class AbstractOverlaySet {

    /**
     * The player this overlay set belongs to.
     */
    private final Player player;

    /**
     * Currently displayed overlays keyed by {@link OverlayType}.
     */
    private final EnumMap<OverlayType, AbstractOverlay> overlayMap = new EnumMap<>(OverlayType.class);

    /**
     * Creates a new {@link AbstractOverlaySet} for {@code player}.
     *
     * @param player The player instance.
     */
    public AbstractOverlaySet(Player player) {
        this.player = player;
    }

    /**
     * Opens (or replaces) the given overlay for its {@link OverlayType}.
     * <p>
     * If the overlay is a {@link OverlayType#WIDGET_STANDARD}, this will interrupt all {@link ActionType#WEAK} actions
     * before opening.
     * <p>
     * The previous overlay of the same type will receive {@code onReplace}/{@code onClose} callbacks and be
     * transitioned to {@code CLOSED}. The new overlay is transitioned to {@code OPEN}, invoking the respective
     * callbacks.
     *
     * @param inter The overlay to open.
     */
    public void open(AbstractOverlay inter) {
        OverlayType type = inter.getOverlayType();
        if (type == OverlayType.WIDGET_STANDARD) {
            player.getActions().interruptWeak();
        }
        replaceOverlay(type, inter);
    }

    /**
     * Closes all "window" overlays (standard + input) and interrupts {@link ActionType#WEAK} actions.
     * <p>
     * This sends a {@link CloseWindowsMessageWriter}, resets dialogues, clears the overlays internally,
     * and invokes lifecycle hooks on the closed overlays.
     */
    public void closeWindows() {
        closeWindows(true);
    }

    /**
     * Closes all "window" overlays ({@link OverlayType#WIDGET_STANDARD} and {@link OverlayType#INPUT}).
     * <p>
     * When {@code interruptActions} is {@code true}, also interrupts {@link ActionType#WEAK} actions and clears the
     * current interaction target. If any window overlays are present, sends a {@link CloseWindowsMessageWriter},
     * resets dialogues, and transitions those overlays to {@code CLOSED}.
     *
     * @param interruptActions Whether to interrupt weak actions and clear interaction.
     */
    public void closeWindows(boolean interruptActions) {
        if (interruptActions) {
            player.getActions().interruptWeak();
            player.resetInteractingWith();
        }
        if (overlayMap.containsKey(OverlayType.WIDGET_STANDARD) ||
                overlayMap.containsKey(OverlayType.INPUT)) {
            player.queue(new CloseWindowsMessageWriter());
            player.resetDialogues();
            replaceOverlay(OverlayType.WIDGET_STANDARD, null);
            replaceOverlay(OverlayType.INPUT, null);
        }
    }

    /**
     * Closes the current {@link OverlayType#WIDGET_WALKABLE} overlay, if present.
     * <p>
     * Emits a {@link WalkableInterfaceMessageWriter} with {@code -1} and transitions the overlay to {@code CLOSED}.
     */
    public void closeWalkable() {
        if (containsType(OverlayType.WIDGET_WALKABLE)) {
            player.queue(new WalkableInterfaceMessageWriter(-1));
            replaceOverlay(OverlayType.WIDGET_WALKABLE, null);
        }
    }

    /**
     * Closes all overlays (windows and walkable) and interrupts weak actions.
     * <p>
     * Equivalent to {@code closeWindows(true)} followed by {@code closeWalkable()}.
     */
    public void closeAll() {
        closeWindows(true);
        closeWalkable();
    }

    /**
     * Returns whether an overlay of the specified type is currently open.
     *
     * @param type The overlay type.
     * @return {@code true} if an overlay for {@code type} is present; otherwise {@code false}.
     */
    public boolean containsType(OverlayType type) {
        return overlayMap.containsKey(type);
    }

    /**
     * Returns whether an overlay of the specified concrete class is currently open.
     *
     * @param type The overlay class to check for.
     * @param <T> The overlay subtype.
     * @return {@code true} if an overlay of the specified class is currently active; otherwise {@code false}.
     */
    public <T extends AbstractOverlay> boolean contains(Class<T> type) {
        return getOverlay(type) != null;
    }

    /**
     * Returns whether a "window" overlay is currently open ({@link OverlayType#WIDGET_STANDARD} or
     * {@link OverlayType#INPUT}).
     *
     * @return {@code true} if any window overlay is present; otherwise {@code false}.
     */
    public boolean hasWindow() {
        return containsType(OverlayType.WIDGET_STANDARD) || containsType(OverlayType.INPUT);
    }

    /**
     * Retrieves the currently open overlay by concrete class.
     * <p>
     * This first resolves by well-known type buckets (INPUT/STANDARD/WALKABLE); if the requested class is not a direct
     * subclass of those, a linear scan is performed for an exact class match.
     *
     * @param type The overlay class (concrete subtype of {@link AbstractOverlay}).
     * @param <T> The overlay subtype.
     * @return The overlay instance if found; otherwise {@code null}.
     */
    public <T extends AbstractOverlay> T getOverlay(Class<T> type) {
        if (overlayMap.isEmpty()) {
            return null;
        }
        if (InputOverlay.class.isAssignableFrom(type)) {
            return (T) overlayMap.get(OverlayType.INPUT);
        } else if (StandardInterface.class.isAssignableFrom(type)) {
            return (T) overlayMap.get(OverlayType.WIDGET_STANDARD);
        } else if (WalkableInterface.class.isAssignableFrom(type)) {
            return (T) overlayMap.get(OverlayType.WIDGET_WALKABLE);
        } else {
            // More expensive lookup, could be one of those types without being a subclass.
            for (AbstractOverlay overlay : overlayMap.values()) {
                if (overlay.getClass() == type) {
                    return (T) overlay;
                }
            }
            return null;
        }
    }

    /**
     * Retrieves the currently open overlay for the specified {@link OverlayType}.
     *
     * @param type The overlay type.
     * @param <T> The overlay subtype (caller responsibility to cast appropriately).
     * @return The overlay instance if present; otherwise {@code null}.
     */
    public <T extends AbstractOverlay> T getOverlay(OverlayType type) {
        return (T) overlayMap.get(type);
    }

    /**
     * Replaces the overlay for {@code type} with {@code replaceWith}, handling lifecycle transitions.
     *
     * @param type The overlay type slot to replace.
     * @param replaceWith The new overlay or {@code null} to clear the slot.
     */
    private void replaceOverlay(OverlayType type, AbstractOverlay replaceWith) {
        AbstractOverlay overlay = replaceWith == null ? overlayMap.remove(type) :
                overlayMap.put(type, replaceWith);
        if (overlay != null) {
            overlay.setClosed(player, replaceWith);
        }
        if (replaceWith != null) {
            replaceWith.setOpened(player);
        }
    }

    /**
     * @return The player this overlay set belongs to.
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * @return Currently displayed overlays keyed by {@link OverlayType}.
     */
    public EnumMap<OverlayType, AbstractOverlay> getOverlayMap() {
        return overlayMap;
    }
}
