package io.luna.game.model.mob.inter;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.TabInterfaceMessageWriter;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * A model representing a collection of sidebar tabs on the Player's game screen.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class GameTabSet {

    /**
     * An enumerated type representing the sidebar tab indexes.
     */
    public enum TabIndex {
        COMBAT(0),
        SKILL(1),
        QUEST(2),
        INVENTORY(3),
        EQUIPMENT(4),
        PRAYER(5),
        MAGIC(6),
        UNUSED(7),
        FRIENDS(8),
        IGNORES(9),
        LOGOUT(10),
        SETTINGS(11),
        EMOTE(12),
        MUSIC(13);

        /**
         * An immutable set containing all elements in this enumerated type.
         */
        public static final ImmutableSet<TabIndex> ALL =
                Arrays.stream(values()).collect(Sets.toImmutableEnumSet());

        /**
         * The index.
         */
        private final int id;

        /**
         * Creates a new {@link TabIndex}.
         *
         * @param id The index.
         */
        TabIndex(int id) {
            this.id = id;
        }

        /**
         * @return The index.
         */
        public final int getId() {
            return id;
        }
    }

    // Initialize map of default tabs.
    static {
        EnumMap<TabIndex, Integer> defaultTabs = new EnumMap<>(TabIndex.class);
        defaultTabs.put(TabIndex.COMBAT, 2423);
        defaultTabs.put(TabIndex.SKILL, 3917);
        defaultTabs.put(TabIndex.QUEST, 638);
        defaultTabs.put(TabIndex.INVENTORY, 3213);
        defaultTabs.put(TabIndex.EQUIPMENT, 1644);
        defaultTabs.put(TabIndex.PRAYER, 5608);
        defaultTabs.put(TabIndex.MAGIC, 1151);
        defaultTabs.put(TabIndex.UNUSED, -1);
        defaultTabs.put(TabIndex.FRIENDS, 5065);
        defaultTabs.put(TabIndex.IGNORES, 5715);
        defaultTabs.put(TabIndex.LOGOUT, 2449);
        defaultTabs.put(TabIndex.SETTINGS, 904);
        defaultTabs.put(TabIndex.EMOTE, 147);
        defaultTabs.put(TabIndex.MUSIC, 962);

        DEFAULT = ImmutableMap.copyOf(defaultTabs);
    }

    /**
     * The immutable map of default tabs.
     */
    private static final ImmutableMap<TabIndex, Integer> DEFAULT;

    /**
     * The Player instance.
     */
    private final Player player;

    /**
     * An array of currently set game tabs.
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
        if (!currentId.isPresent() || currentId.getAsInt() != id) {
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
     * Clears the interfaces on all tabs.
     */
    public void clearAll() {
        TabIndex.ALL.forEach(this::clear);
    }

    /**
     * Resets the interface on {@code index} to its default.
     *
     * @param index The tab to reset.
     */
    public void reset(TabIndex index) {
        set(index, DEFAULT.get(index));
    }

    /**
     * Resets the interfaces on all tabs back to their defaults.
     */
    public void resetAll() {
        TabIndex.ALL.forEach(this::reset);
    }

    /**
     * Returns a <strong>copy</strong> of the backing enum map. A new copy is created on each invocation of
     * this method.
     *
     * @return An immutable copy of the backing enum map.
     */
    public ImmutableMap<TabIndex, Integer> getAll() {
        return Maps.immutableEnumMap(tabs);
    }
}