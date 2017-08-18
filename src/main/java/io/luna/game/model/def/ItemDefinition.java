package io.luna.game.model.def;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import io.luna.util.IterableArray;
import io.luna.util.StringUtils;
import io.luna.util.ThreadUtils;
import io.luna.util.parser.impl.ItemDefinitionParser;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.OptionalInt;
import java.util.PrimitiveIterator;

/**
 * A definition model describing an item.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ItemDefinition {

    /**
     * The definition count.
     */
    public static final int SIZE = 7956;

    /**
     * An iterable array of definitions.
     */
    private static final IterableArray<ItemDefinition> DEFINITIONS = new IterableArray<>(SIZE);

    /**
     * Sets the backing definitions.
     */
    public static void set(ItemDefinition[] definitions) {
        ThreadUtils.ensureInitThread();

        System.arraycopy(definitions, 0, DEFINITIONS.getArray(), 0, SIZE);
    }

    /**
     * Retrieves a definition.
     */
    public static ItemDefinition get(int id) {
        ItemDefinition def = DEFINITIONS.get(id);
        if (def == null) {
            throw new NoSuchElementException("No definition for id " + id);
        }
        return def;
    }

    /**
     * Returns all definitions.
     */
    public static Iterable<ItemDefinition> all() {
        return DEFINITIONS;
    }

    /**
     * Returns the item name of {@code id}.
     */
    public static String computeNameForId(int id) {
        return get(id).getName();
    }

    /**
     * The identifier.
     */
    private final int id;

    /**
     * The name.
     */
    private final String name;

    /**
     * The examine text.
     */
    private final String examine;

    /**
     * If the item is stackable.
     */
    private final boolean stackable;

    /**
     * The base value.
     */
    private final int value;

    /**
     * The noted identifier.
     */
    private final OptionalInt notedId;

    /**
     * The unnoted identifier.
     */
    private final OptionalInt unnotedId;

    /**
     * If this item is members only.
     */
    private final boolean membersOnly;

    /**
     * The weight.
     */
    private final double weight;

    /**
     * If this item can be traded.
     */
    private final boolean tradeable;

    /**
     * A list of inventory actions.
     */
    private final ImmutableList<String> inventoryActions;

    /**
     * A list of ground actions.
     */
    private final ImmutableList<String> groundActions;

    /**
     * Creates a new {@link ItemDefinition}.
     *
     * @param id The identifier.
     * @param name The name.
     * @param examine The examine text.
     * @param stackable If the item is stackable.
     * @param value The base value.
     * @param notedId The noted identifier.
     * @param unnotedId The unnoted identifier.
     * @param membersOnly If this item is members only.
     * @param weight The weight.
     * @param tradeable If this item can be traded.
     * @param inventoryActions A list of inventory actions.
     * @param groundActions A list of ground actions.
     */
    public ItemDefinition(int id, String name, String examine, boolean stackable, int value, int notedId,
                          int unnotedId, boolean membersOnly, double weight, boolean tradeable, String[] inventoryActions,
                          String[] groundActions) {
        this.id = id;
        this.name = name;
        this.examine = examine;
        this.stackable = stackable;
        this.value = value;
        this.notedId = notedId == -1 ? OptionalInt.empty() : OptionalInt.of(notedId);
        this.unnotedId = unnotedId == -1 ? OptionalInt.empty() : OptionalInt.of(unnotedId);
        this.membersOnly = membersOnly;
        this.weight = weight;
        this.tradeable = tradeable;
        this.inventoryActions = ImmutableList.copyOf(inventoryActions);
        this.groundActions = ImmutableList.copyOf(groundActions);
    }

    /**
     * Determines if {@code action} is an inventory action.
     */
    public boolean hasInventoryAction(String action) {
        return inventoryActions.contains(action);
    }

    /**
     * Determines if {@code action} is a ground action.
     */
    public boolean hasGroundAction(String action) {
        return groundActions.contains(action);
    }

    /**
     * Returns {@code true} if the item can be noted.
     */
    public boolean isNoteable() {
        return notedId.isPresent();
    }

    /**
     * Returns {@code true} if the item is noted.
     */
    public boolean isNoted() {
        return unnotedId.isPresent();
    }

    /**
     * @return The item identifier.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The examine text.
     */
    public String getExamine() {
        return examine;
    }

    /**
     * @return If the item is stackable.
     */
    public boolean isStackable() {
        return stackable;
    }

    /**
     * @return The base value.
     */
    public int getValue() {
        return value;
    }

    /**
     * @return The noted identifier.
     */
    public OptionalInt getNotedId() {
        return notedId;
    }

    /**
     * @return The unnoted identifier.
     */
    public OptionalInt getUnnotedId() {
        return unnotedId;
    }

    /**
     * @return If this item is members only.
     */
    public boolean isMembersOnly() {
        return membersOnly;
    }

    /**
     * @return The weight.
     */
    public double getWeight() {
        return weight;
    }

    /**
     * @return If this item can be traded.
     */
    public boolean isTradeable() {
        return tradeable;
    }

    /**
     * @return A list of inventory actions.
     */
    public ImmutableList<String> getInventoryActions() {
        return inventoryActions;
    }

    /**
     * @return A list of ground actions.
     */
    public ImmutableList<String> getGroundActions() {
        return groundActions;
    }
}