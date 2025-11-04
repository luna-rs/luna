package io.luna.game.model.mob.overlay;

import io.luna.game.model.mob.Player;

/**
 * An {@link AbstractOverlay} that presents an input UI and delivers a typed result back to the server.
 * <p>
 * Implementations define how the client UI is opened (via {@link #setOpened(Player)} on the base class)
 * and must override {@link #input(Player, Object)} to handle the parsed value submitted by the player.
 *
 * @param <T> The value type produced by this input (e.g., {@link Integer}, {@link String}).
 * @author lare96
 */
public abstract class InputOverlay<T> extends AbstractOverlay {

    /**
     * Creates a new {@link InputOverlay}.
     */
    InputOverlay() {
        super(OverlayType.INPUT);
    }

    /**
     * Handles the value submitted by {@code player} from this input overlay.
     * <p>
     * Implementations should validate and act on {@code value}. This method is the terminal callback for the
     * input flow; it will typically close the overlay (or open a follow-up overlay) after processing.
     *
     * @param player The submitting player.
     * @param value  The parsed input value of type {@code T}.
     */
    public abstract void input(Player player, T value);
}
