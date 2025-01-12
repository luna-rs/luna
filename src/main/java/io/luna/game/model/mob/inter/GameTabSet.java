package io.luna.game.model.mob.inter;

import com.google.common.collect.ImmutableMap;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.ForceTabMessageWriter;
import io.luna.net.msg.out.TabInterfaceMessageWriter;

import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * A model representing a collection of sidebar tabs on a player's game screen.
 *
 * @author lare96
 */
public final class GameTabSet {

    /**
     * An enumerated type representing the sidebar tab indexes.
     */
    public enum TabIndex {
        COMBAT(0, 2423),
        SKILL(1, 3917),
        QUEST(2, 638),
        INVENTORY(3, 3213),
        EQUIPMENT(4, 1644),
        PRAYER(5, 5608),
        MAGIC(6, 1151),
        UNUSED(7, -1),
        FRIENDS(8, 5065),
        IGNORES(9, 5715),
        LOGOUT(10, 2449),
        SETTINGS(11, 904),
        EMOTE(12, 147),
        MUSIC(13, 962);

        public static final ImmutableMap<Integer, TabIndex> ID_MAP;

        static {
            ImmutableMap.Builder<Integer, TabIndex> builder = ImmutableMap.builder();
            for (TabIndex tab : values()) {
                builder.put(tab.index, tab);
            }
            ID_MAP = builder.build();
        }

        public static TabIndex forIndex(int index) {
            return ID_MAP.get(index);
        }

        /**
         * The index.
         */
        private final int index;

        /**
         * The default tab.
         */
        private final int defaultTabId;

        /**
         * Creates a new {@link TabIndex}.
         *
         * @param index The index.
         * @param defaultTabId The default tab.
         */
        TabIndex(int index, int defaultTabId) {
            this.index = index;
            this.defaultTabId = defaultTabId;
        }

        /**
         * @return The index.
         */
        public final int getIndex() {
            return index;
        }

        /**
         * @return The default tab.
         */
        public int getDefaultTabId() {
            return defaultTabId;
        }
    }

    /**
     * The Player instance.
     */
    private final Player player;

    /**
     * A map of currently-set game tabs.
     */
    private final Map<TabIndex, Integer> tabs = new EnumMap<>(TabIndex.class);

    /**
     * Creates a new {@link GameTabSet}.
     *
     * @param player The Player instance.
     */
    public GameTabSet(Player player) {
        this.player = player;
    }

    /**
     * Sets the tab interface at {@code index}.
     *
     * @param index The tab to set the interface at.
     * @param id The identifier of the interface to set.
     * @return {@code true} if the interface was set.
     */
    public boolean set(TabIndex index, int id) {
        OptionalInt currentId = get(index);
        if (currentId.isEmpty() || currentId.getAsInt() != id) {
            player.queue(new TabInterfaceMessageWriter(index, id));
            tabs.put(index, id);
            return true;
        }
        return false;
    }

    /**
     * Retrieves the tab interface at {@code index}.
     *
     * @param index The tab to retrieve the interface on.
     * @return The interface on {@code index}, wrapped in an optional.
     */
    public OptionalInt get(TabIndex index) {
        Integer current = tabs.get(index);
        if (current == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(current);
    }

    /**
     * Clears the interface on {@code index}.
     *
     * @param index The tab to clear.
     */
    public void clear(TabIndex index) {
        player.queue(new TabInterfaceMessageWriter(index, -1));
        tabs.remove(index);
    }

    /**
     * Forces the client to show tab {@code index}.
     *
     * @param index The tab to force.
     */
    public void show(TabIndex index) {
        player.queue(new ForceTabMessageWriter(index));
    }

    /**
     * Clears the interfaces on all tabs.
     */
    public void clearAll() {
        for (var tabIndex : TabIndex.values()) {
            clear(tabIndex);
        }
    }

    /**
     * Resets the interface on {@code index} to its default.
     *
     * @param index The tab to reset.
     */
    public void reset(TabIndex index) {
        set(index, index.defaultTabId);
    }

    /**
     * Resets the interfaces on all tabs back to their defaults.
     */
    public void resetAll() {
        for (var taxIndex : TabIndex.values()) {
            reset(taxIndex);
        }
    }
}