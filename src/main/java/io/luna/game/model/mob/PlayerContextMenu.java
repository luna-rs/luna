package io.luna.game.model.mob;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Maintains the set of right-click context menu options displayed for a {@link Player}.
 *
 * @author lare96
 */
public final class PlayerContextMenu implements Iterable<PlayerContextMenuOption> {

    /**
     * The maximum number of context menu option slots supported by this menu.
     */
    private static final int SIZE = 5;

    /**
     * The menu option slots.
     * <p>
     * The slot used for an option is determined by {@link PlayerContextMenuOption#getIndex()} (1-based), mapped to
     * this array using {@code index - 1}. Slots are {@code null} when no option is displayed.
     * </p>
     */
    private final PlayerContextMenuOption[] options = new PlayerContextMenuOption[SIZE];

    /**
     * The player that owns this context menu.
     * <p>
     * Menu updates (show/hide) are sent by queueing messages onto this player.
     * </p>
     */
    private final Player player;

    /**
     * Creates a new {@link PlayerContextMenu} for {@code player}.
     *
     * @param player The owning player.
     */
    public PlayerContextMenu(Player player) {
        this.player = player;
    }

    /**
     * Returns an unmodifiable iterator over the backing option slots. This iterator includes {@code null} elements
     * for empty slots.
     *
     * @return An iterator over the raw option slots.
     */
    @Override
    public UnmodifiableIterator<PlayerContextMenuOption> iterator() {
        return Iterators.forArray(options);
    }

    /**
     * Retrieves an option in this menu with the specified {@code name}.
     * <p>
     * Name matching is exact and case-sensitive, using {@link String#equals(Object)}.
     * </p>
     *
     * @param name The option name to match.
     * @return The matching menu option (if present), otherwise {@link Optional#empty()}.
     */
    public Optional<PlayerContextMenuOption> findOptionWithName(String name) {
        return Arrays.stream(options)
                .filter(Objects::nonNull)
                .filter(interaction -> interaction.getName().equals(name))
                .findAny();
    }

    /**
     * Determines if {@code option} is currently displayed in this menu.
     * <p>
     * This checks the slot implied by {@link PlayerContextMenuOption#getIndex()} and compares the stored value
     * to {@code option} using {@link Objects#equals(Object, Object)}.
     * </p>
     *
     * @param option The option to check.
     * @return {@code true} if this menu currently contains {@code option} in its indexed slot.
     * @throws NullPointerException if {@code option} is {@code null}.
     * @throws ArrayIndexOutOfBoundsException if {@code option.getIndex()} is not in {@code [1, SIZE]}.
     */
    public boolean contains(PlayerContextMenuOption option) {
        return Objects.equals(options[getIndex(option)], option);
    }

    /**
     * Displays {@code option} in its configured slot and queues the corresponding “show” message to the client.
     * <p>
     * If the slot already contains an option, it will be overwritten.
     * </p>
     *
     * @param option The option to display.
     * @throws NullPointerException if {@code option} is {@code null}.
     * @throws ArrayIndexOutOfBoundsException if {@code option.getIndex()} is not in {@code [1, SIZE]}.
     */
    public void show(PlayerContextMenuOption option) {
        options[getIndex(option)] = option;
        player.queue(option.toMessage(false));
    }

    /**
     * Hides {@code option} from its configured slot and queues the corresponding “hide” message to the client.
     * <p>
     * This method clears the slot implied by {@link PlayerContextMenuOption#getIndex()}, regardless of what option
     * is currently in that slot.
     * </p>
     *
     * @param option The option to hide.
     * @throws NullPointerException if {@code interaction} is {@code null}.
     * @throws ArrayIndexOutOfBoundsException if {@code option.getIndex()} is not in {@code [1, SIZE]}.
     */
    public void hide(PlayerContextMenuOption option) {
        options[getIndex(option)] = null;
        player.queue(option.toMessage(true));
    }

    /**
     * Converts an option's 1-based menu index ({@link PlayerContextMenuOption#getIndex()}) into the 0-based array
     * index used by {@link #options}.
     *
     * @param option The menu option (1-based index).
     * @return The internal 0-based array index.
     * @throws NullPointerException if {@code option} is {@code null}.
     */
    private int getIndex(PlayerContextMenuOption option) {
        return option.getIndex() - 1;
    }
}
