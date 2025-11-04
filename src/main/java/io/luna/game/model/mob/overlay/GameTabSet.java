package io.luna.game.model.mob.overlay;

import com.google.common.collect.ImmutableMap;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.ForceTabMessageWriter;
import io.luna.net.msg.out.TabInterfaceMessageWriter;

import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Manages the sidebar tabs and their associated interfaces for a single {@link Player}.
 * <p>
 * Each {@link TabIndex} represents a client sidebar slot (e.g., Inventory, Magic). This class tracks the
 * currently assigned interface id per tab, emits the appropriate packets when setting/clearing tabs, and
 * supports restoring defaults (including dynamic spellbook handling for the Magic tab).
 *
 * @author lare96
 */
public final class GameTabSet {

    /**
     * Enumerates client sidebar tab indices and their default interface ids for the 317/377 protocol.
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
     * Assigns an interface to the specified tab, emitting a {@link TabInterfaceMessageWriter} if the id changed.
     *
     * @param index The tab to modify.
     * @param id The interface id to assign (use {@code -1} to clear).
     * @return {@code true} if a packet was sent and the mapping updated; {@code false} if unchanged.
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
     * Returns the interface id currently assigned to {@code index}, if any.
     *
     * @param index The tab to query.
     * @return An {@link OptionalInt} containing the interface id, or empty if unset.
     */
    public OptionalInt get(TabIndex index) {
        Integer current = tabs.get(index);
        if (current == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(current);
    }

    /**
     * Clears the interface assigned to {@code index} (sends {@link TabInterfaceMessageWriter} with {@code -1}).
     *
     * @param index The tab to clear.
     */
    public void clear(TabIndex index) {
        player.queue(new TabInterfaceMessageWriter(index, -1));
        tabs.remove(index);
    }

    /**
     * Forces the client to display the specified tab (does not alter assigned interface ids).
     *
     * @param index The tab to show.
     */
    public void show(TabIndex index) {
        player.queue(new ForceTabMessageWriter(index));
    }

    /**
     * Clears all tabs (emits clear packets for each tab currently tracked).
     */
    public void clearAll() {
        for (TabIndex tabIndex : TabIndex.ID_MAP.values()) {
            clear(tabIndex);
        }
    }

    /**
     * Restores the interface on {@code index} to its default.
     * <p>
     * The Magic tab is dynamic and uses the player's current spellbook widget id.
     *
     * @param index The tab to reset.
     */
    public void reset(TabIndex index) {
        if (index == TabIndex.MAGIC) {
            set(index, player.getSpellbook().getWidgetId());
        } else {
            set(index, index.defaultTabId);
        }
    }

    /**
     * Restores all tabs to their default interfaces.
     */
    public void resetAll() {
        for (TabIndex taxIndex : TabIndex.ID_MAP.values()) {
            reset(taxIndex);
        }
    }
}