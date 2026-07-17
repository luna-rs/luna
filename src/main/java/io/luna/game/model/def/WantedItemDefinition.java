package io.luna.game.model.def;

import java.util.Objects;

/**
 * Defines an item that bots are interested in acquiring.
 *
 * @param id The unnoted item id.
 * @param min The minimum amount the bot should try to keep. -1 Indicates that the bot should always try to keep some
 * of this item.
 * @param target The preferred amount the bot should try to acquire.
 * @param skill The skill associated with this wanted item, or {@code -1} if no skill applies.
 * @param maxLevel The maximum skill level where this item remains wanted, or {@code -1} if there is no limit.
 * @author lare96
 */
public record WantedItemDefinition(int id, int min, int target, int skill, int maxLevel) implements Definition {

    /**
     * The global repository of default wanted-item definitions.
     */
    public static final MapDefinitionRepository<WantedItemDefinition> DEFAULT = new MapDefinitionRepository<>();

    /**
     * Creates an always-wanted {@link WantedItemDefinition}.
     *
     * @param id The unnoted item id.
     * @param target The preferred amount the bot should try to acquire.
     */
    public WantedItemDefinition(int id, int target) {
        this(id, -1, target, -1, -1);
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
        return new WantedItemDefinition(newId, min, target, skill, maxLevel);
    }
}