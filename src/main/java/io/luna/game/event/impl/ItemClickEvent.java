package io.luna.game.event.impl;

import io.luna.game.event.Event;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkState;

/**
 * An event implementation sent when a player clicks any item index.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class ItemClickEvent extends Event {

    /**
     * An event implementation sent whenever a player clicks an item's first index.
     *
     * @author lare96 <http://github.org/lare96>
     */
    public static final class ItemFirstClickEvent extends ItemClickEvent {

        /**
         * Creates a new {@link ItemFirstClickEvent}.
         */
        public ItemFirstClickEvent(int itemId, int slot, int interfaceId) {
            super(itemId, slot, interfaceId);
        }
    }

    /**
     * An event implementation sent whenever a player clicks an item's second index.
     *
     * @author lare96 <http://github.org/lare96>
     */
    public static final class ItemSecondClickEvent extends ItemClickEvent {

        /**
         * Creates a new {@link ItemSecondClickEvent}.
         */
        public ItemSecondClickEvent(int itemId, int slot, int interfaceId) {
            super(itemId, slot, interfaceId);
        }
    }

    /**
     * An event implementation sent whenever a player clicks an item's third index.
     *
     * @author lare96 <http://github.org/lare96>
     */
    public static final class ItemThirdClickEvent extends ItemClickEvent {

        /**
         * Creates a new {@link ItemThirdClickEvent}.
         */
        public ItemThirdClickEvent(int itemId, int slot, int interfaceId) {
            super(itemId, slot, interfaceId);
        }
    }

    /**
     * An event implementation sent whenever a player clicks an item's fourth index.
     *
     * @author lare96 <http://github.org/lare96>
     */
    public static final class ItemFourthClickEvent extends ItemClickEvent {

        /**
         * Creates a new {@link ItemFourthClickEvent}.
         */
        public ItemFourthClickEvent(int id, int index, int interfaceId) {
            super(id, index, interfaceId);
        }
    }

    /**
     * An event implementation sent whenever a player clicks an item's fifth index.
     *
     * @author lare96 <http://github.org/lare96>
     */
    public static final class ItemFifthClickEvent extends ItemClickEvent {

        /**
         * Creates a new {@link ItemFifthClickEvent}.
         */
        public ItemFifthClickEvent(int id, int index, int interfaceId) {
            super(id, index, interfaceId);
        }
    }

    /**
     * The identifier of the item clicked.
     */
    private final int id;

    /**
     * The index of the item clicked.
     */
    private final int index;

    /**
     * The identifier of the interface the item was clicked on.
     */
    private final int interfaceId;

    /**
     * Creates a new {@link ItemClickEvent}.
     *
     * @param id The identifier of the item clicked.
     * @param index The index of the item clicked.
     * @param interfaceId The identifier of the interface the item was clicked on.
     */
    private ItemClickEvent(int id, int index, int interfaceId) {
        this.id = id;
        this.index = index;
        this.interfaceId = interfaceId;
    }

    @Override
    public final boolean matches(Object... args) {
        checkState(args.length == 1, "args.length != 1");
        return Objects.equals(args[0], id);
    }

    /**
     * @return The identifier of the item clicked.
     */
    public final int getId() {
        return id;
    }

    /**
     * @return The index of the item clicked.
     */
    public final int getIndex() {
        return index;
    }

    /**
     * @return The identifier of the interface the item was clicked on.
     */
    public final int getInterfaceId() {
        return interfaceId;
    }
}