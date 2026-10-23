
package io.luna.game.model.def;

import com.google.common.collect.ImmutableSet;

import java.util.Objects;

/**
 * Defines an item that a bot wants to acquire or maintain in its ownership.
 * <p>
 * Each definition specifies an ownership target, optional minimum stock, level restrictions, acquisition
 * priority, and tags describing the item's purpose.
 * <p>
 * Temporary requirements use {@code min == -1} and may be removed after their target is met.
 * Persistent requirements retain a minimum stock value and remain available for replenishment.
 *
 * @param id The unnoted item id.
 * @param min The minimum stock to maintain, or {@code -1} for a temporary requirement.
 * @param target The desired total ownership quantity.
 * @param skill The associated skill id, or {@code -1} if no skill is specified.
 * @param maxLevel The maximum applicable level, or {@code -1} if unrestricted.
 * @param priority The acquisition priority of this item.
 * @param tags The tags describing the item's purpose.
 * @author lare96
 */
public record WantedItemDefinition(int id, int min, int target, int skill, int maxLevel,
                                   WantedItemPriority priority,
                                   ImmutableSet<WantedItemTag> tags) implements Definition {

    /**
     * Categorizes wanted items according to their intended purpose.
     * <p>
     * Tags allow bot systems to distinguish skilling supplies, combat supplies, equipment,
     * and miscellaneous items. A wanted item may have multiple tags.
     */
    public enum WantedItemTag {

        /**
         * Weapons used for combat.
         */
        WEAPON,

        /**
         * Equipment worn primarily for appearance or cosmetic purposes.
         */
        COSMETIC_EQUIPMENT,

        /**
         * Equipment used for combat-related purposes.
         */
        COMBAT_EQUIPMENT,

        /**
         * Reusable tools used for skilling or other activities.
         */
        TOOL,

        /**
         * Consumable items used for skilling activities.
         */
        SKILLING_CONSUMABLE,

        /**
         * Consumable items used for combat activities.
         */
        COMBAT_CONSUMABLE,

        /**
         * Items that do not belong to a more specific category.
         */
        MISCELLANEOUS
    }

    /**
     * Defines the acquisition priority of a wanted item.
     * <p>
     * Higher priority levels indicate items that should be considered before lower-priority items.
     */
    public enum WantedItemPriority {

        /**
         * The highest acquisition priority.
         */
        URGENT(3),

        /**
         * A high acquisition priority.
         */
        HIGH(2),

        /**
         * The standard acquisition priority.
         */
        STANDARD(1),

        /**
         * The lowest acquisition priority.
         */
        LOW(0);

        /**
         * The numeric priority level.
         */
        private final int level;

        /**
         * Creates a wanted-item priority with the specified level.
         *
         * @param level The numeric priority level.
         */
        WantedItemPriority(int level) {
            this.level = level;
        }

        /**
         * Returns the numeric priority level.
         *
         * @return The priority level, where higher values indicate greater priority.
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
     * The fallback tags assigned to wanted items without specified tags.
     */
    public static final ImmutableSet<WantedItemTag> DEFAULT_TAGS = ImmutableSet.of(WantedItemTag.MISCELLANEOUS);

    /**
     * The default acquisition priority.
     */
    public static final WantedItemPriority DEFAULT_PRIORITY = WantedItemPriority.STANDARD;

    /**
     * Creates a temporary wanted-item definition with a high acquisition priority.
     * <p>
     * Uses the tags from [the default definition] if one exists, otherwise {@link #DEFAULT_TAGS}.
     * The minimum, skill, and maximum level are set to {@code -1}.
     *
     * @param id The unnoted item id.
     * @param target The desired total ownership quantity.
     */
    public WantedItemDefinition(int id, int target) {
        this(id, -1, target, -1, -1, WantedItemPriority.HIGH, DEFAULT.get(id).map(it -> it.tags).orElse(DEFAULT_TAGS));
    }

    /**
     * Compares this wanted-item definition with another object using only the item id.
     * <p>
     * Definitions with the same id are considered equal regardless of their quantities,
     * restrictions, priority, or tags.
     *
     * @param o The object to compare against.
     * @return {@code true} if the object is a wanted-item definition with the same id.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WantedItemDefinition that)) {
            return false;
        }
        return id == that.id;
    }

    /**
     * Computes the hash code using only the item id.
     *
     * @return The hash code of the item id.
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /**
     * Creates a copy of this wanted-item definition with a different item id.
     * <p>
     * Preserves the minimum, target, skill restrictions, priority, and tags.
     *
     * @param newId The item id assigned to the copied definition.
     * @return A new wanted-item definition with the specified id and existing metadata.
     */
    public WantedItemDefinition copy(int newId) {
        return new WantedItemDefinition(newId, min, target, skill, maxLevel, priority, tags);
    }
}
