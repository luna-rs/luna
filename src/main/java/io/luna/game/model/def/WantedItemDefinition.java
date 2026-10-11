package io.luna.game.model.def;

import com.google.common.collect.ImmutableSet;

import java.util.Objects;

/**
 * Defines an item that bots are interested in acquiring.
 *
 * @param id The unnoted item id.
 * @param min The minimum amount the bot should try to keep. -1 Indicates that the bot should always try to keep some
 * of this item up to {@link #target}.
 * @param target The preferred amount the bot should try to acquire.
 * @param skill The skill associated with this wanted item, or {@code -1} if no skill applies.
 * @param maxLevel The maximum skill level where this item remains wanted, or {@code -1} if there is no limit.
 * @param priority The priority of this wanted item.
 * @param tags The tags denoting this wanted item's purpose.
 * @author lare96
 */
public record WantedItemDefinition(int id, int min, int target, int skill, int maxLevel,
                                   WantedItemPriority priority,
                                   ImmutableSet<WantedItemTag> tags) implements Definition {

    /**
     * Represents a tag assigned to a wanted item dictating its purpose.
     */
    public enum WantedItemTag {
        WEAPON,
        EQUIPMENT,
        TOOL,
        SKILLING_CONSUMABLE,
        COMBAT_CONSUMABLE,
        MISCELLANEOUS
    }

    /**
     * Represents how badly the bot wants the item.
     */
    public enum WantedItemPriority {
        URGENT(3),
        HIGH(2),
        STANDARD(1),
        LOW(0);

        /**
         * The priority level.
         */
        private final int level;

        /**
         * Creates a new {@link WantedItemPriority}.
         *
         * @param level The priority level.
         */
        WantedItemPriority(int level) {
            this.level = level;
        }

        /**
         * @return The priority level.
         */
        public int getLevel() {
            return level;
        }
    }

    /**
     * The global repository of default wanted-item definitions.
     */
    public static final MapDefinitionRepository<WantedItemDefinition> DEFAULT = new MapDefinitionRepository<>();

    /**
     * An immutable set of always-wanted tags when none are available in {@link #DEFAULT}.
     */
    public static final ImmutableSet<WantedItemTag> DEFAULT_ALWAYS_TAGS = ImmutableSet.of(WantedItemTag.MISCELLANEOUS);

    /**
     * Creates an always-wanted {@link WantedItemDefinition} with {@code tags}.
     *
     * @param id The unnoted item id.
     * @param target The preferred amount the bot should try to acquire.
     */
    public WantedItemDefinition(int id, int target) {
        this(id, -1, target, -1, -1, WantedItemPriority.HIGH, DEFAULT.get(id).map(it -> it.tags).orElse(DEFAULT_ALWAYS_TAGS));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WantedItemDefinition that)) {
            return false;
        }
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /**
     * Creates a copy of this wanted-item definition with a different item id.
     *
     * @param newId The item id for the copied definition.
     * @return A wanted-item definition with the new item id and the same requirement values.
     */
    public WantedItemDefinition copy(int newId) {
        return new WantedItemDefinition(newId, min, target, skill, maxLevel, priority, tags);
    }
}