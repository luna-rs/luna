package io.luna.game.model.def;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;

import java.util.Arrays;
import java.util.Optional;

/**
 * An {@link Iterable} implementation representing the data within context menu popup shown when right
 * clicking an entity.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class ContextMenu implements Iterable<String> {

    /**
     * Builds an array of menu actions.
     *
     * @param length The length of the array.
     * @param actionStrings The old menu actions.
     * @return The new menu actions.
     */
    private static String[] buildMenuActions(int length, String[] actionStrings) {
        String[] newActions = new String[length];
        for (int index = 0; index < length; index++) {
            // Cache the action if it's non-null.
            String action = actionStrings[index];
            if (!action.equals("null")) {
                newActions[index] = action;
            }
        }
        return newActions;
    }

    /**
     * A list of menu actions.
     */
    private final String[] actions;

    /**
     * Creates a new {@link ContextMenu}.
     *
     * @param actionStrings An array of menu actions.
     */
    public ContextMenu(String[] actionStrings) {
        int length = actionStrings.length;
        if (length == 0) {
            actions = new String[length];
        } else {
            actions = buildMenuActions(length, actionStrings);
        }
    }

    @Override
    public UnmodifiableIterator<String> iterator() {
        return Iterators.forArray(actions);
    }

    /**
     * Determines if {@code index} is out of bounds for this context menu.
     *
     * @param index The index.
     * @return {@code true} if the index is out of bounds.
     */
    private boolean isIndexOutOfBounds(int index) {
        return index < 0 || index >= actions.length;
    }

    /**
     * Retrieves the action on {@code index}.
     *
     * @param index The index.
     * @return The action on {@code index}.
     */
    public String retrieve(int index) {
        if (isIndexOutOfBounds(index)) {
            return null;
        }
        return get(index).get();
    }

    /**
     * Gets the action on {@code index}.
     *
     * @param index The index.
     * @return The action on {@code index}, wrapped in an optional.
     */
    public Optional<String> get(int index) {
        if (isIndexOutOfBounds(index)) {
            return Optional.empty();
        }
        String action = actions[index];
        return Optional.ofNullable(action);
    }

    /**
     * Determines if this context menu has {@code action} on {@code index}.
     *
     * @param index The index.
     * @param action The action.
     * @return {@code true} if this context menu has the action on the index.
     */
    public boolean has(int index, String action) {
        if (isIndexOutOfBounds(index)) {
            return false;
        }
        return get(index).filter(action::equals).isPresent();
    }

    /**
     * Determines if this context menu has {@code action}.
     *
     * @param action The action.
     * @return {@code true} if this context menu has the action.
     */
    public boolean has(String action) {
        return Arrays.stream(actions).anyMatch(action::equals);
    }

    /**
     * Returns an immutable view of the actions.
     *
     * @return The list of actions.
     */
    public ImmutableList<String> toList() {
        return ImmutableList.copyOf(actions);
    }
}
