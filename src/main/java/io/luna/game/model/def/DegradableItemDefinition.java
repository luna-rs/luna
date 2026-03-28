package io.luna.game.model.def;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import game.item.degradable.DegradableItemType;

/**
 * A {@link Definition} describing a single entry in a degradable-item chain.
 * <p>
 * Each definition represents one item state and links it to the previous and next states in its degradation sequence.
 * Definitions are also grouped by {@link DegradableItemType} for fast lookup by degradation category.
 *
 * @author lare96
 */
public final class DegradableItemDefinition implements Definition {

    /**
     * All loaded {@link DegradableItemDefinition} instances, keyed by item id.
     */
    public static final MapDefinitionRepository<DegradableItemDefinition> ALL = new MapDefinitionRepository<>();

    /**
     * All loaded degradable-item definitions grouped by degradation type.
     */
    public static volatile ImmutableSetMultimap<DegradableItemType, DegradableItemDefinition> TYPES =
            ImmutableSetMultimap.of();

    /**
     * Item ids that are restricted from being dropped.
     * <p>
     * This is populated with degradable item states that are not the first entry in a degradation chain.
     */
    public static volatile ImmutableSet<Integer> DROP_RESTRICTED = ImmutableSet.of();

    /**
     * Loads all degradable-item definitions and rebuilds the shared lookup sets.
     * <p>
     * The supplied definitions are:
     * <ul>
     *     <li>Stored in {@link #ALL}, keyed by item id.</li>
     *     <li>Grouped into {@link #TYPES} by {@link DegradableItemType}.</li>
     *     <li>Used to populate {@link #DROP_RESTRICTED} for all non-root degradation states.</li>
     * </ul>
     *
     * @param tokens The degradable-item definitions grouped by degradation type.
     */
    public static void loadAll(SetMultimap<DegradableItemType, DegradableItemDefinition> tokens) {
        DegradableItemDefinition.ALL.storeAndLock(tokens.values());
        TYPES = ImmutableSetMultimap.copyOf(tokens);

        var dropRestricted = ImmutableSet.<Integer>builder();
        for (DegradableItemDefinition def : tokens.values()) {
            if (def.previousId != -1) {
                dropRestricted.add(def.id);
            }
        }
        DROP_RESTRICTED = dropRestricted.build();
    }

    /**
     * The previous item id in the degradation chain, or {@code -1} if this is the first state in the chain.
     */
    private final int previousId;

    /**
     * The item id represented by this degradation state.
     */
    private final int id;

    /**
     * The next item id in the degradation chain.
     * <p>
     * This may point to the next degraded state, a broken base state, or another terminal outcome depending on
     * the configured chain.
     */
    private final int nextId;

    /**
     * The degradation category this item belongs to.
     */
    private final DegradableItemType type;

    /**
     * Creates a new {@link DegradableItemDefinition}.
     *
     * @param previousId The previous item id in the degradation chain, or {@code -1} if this is the first state.
     * @param id The item id represented by this degradation state.
     * @param nextId The next item id in the degradation chain.
     * @param type The degradation category this item belongs to.
     */
    public DegradableItemDefinition(int previousId, int id, int nextId, DegradableItemType type) {
        this.previousId = previousId;
        this.id = id;
        this.nextId = nextId;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DegradableItemDefinition)) return false;
        DegradableItemDefinition that = (DegradableItemDefinition) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public int getId() {
        return id;
    }

    /**
     * @return The previous item id, or {@code -1} if this is the first state.
     */
    public int getPreviousId() {
        return previousId;
    }

    /**
     * @return The next item id.
     */
    public int getNextId() {
        return nextId;
    }

    /**
     * @return The degradable item type.
     */
    public DegradableItemType getType() {
        return type;
    }
}