package io.luna.game.model.def;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.item.Item;
import io.luna.util.StringUtils;
import io.luna.util.parser.impl.ItemDefinitionParser;

import java.util.Arrays;

/**
 * A cached definition that describes a specific {@link Item}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ItemDefinition {

    /**
     * An {@link ImmutableList} of the cached {@code ItemDefinition}s.
     */
    public static final ImmutableList<ItemDefinition> DEFINITIONS;

    /**
     * The default {@link ItemDefinition} used when none in {@code DEFINITIONS} can be assigned to an {@code Item}.
     */
    public static final ItemDefinition DEFAULT = new ItemDefinition(-1, null, null, false, -1, -1, -1, -1, false, 0.0, false,
        StringUtils.EMPTY_ARRAY, StringUtils.EMPTY_ARRAY);

    static {
        ItemDefinition[] definitions = new ItemDefinition[7956];
        Arrays.fill(definitions, DEFAULT);

        ItemDefinitionParser parser = new ItemDefinitionParser(definitions);
        parser.run();

        DEFINITIONS = ImmutableList.copyOf(definitions);
    }

    /**
     * The identifier for the {@code Item}.
     */
    private final int id;

    /**
     * The name of the {@code Item}.
     */
    private final String name;

    /**
     * The description of the {@code Item}.
     */
    private final String examine;

    /**
     * If the {@code Item} is stackable.
     */
    private final boolean stackable;

    /**
     * The base value of the {@code Item}, used for shop prices and low/high alch values.
     */
    private final int baseValue;

    /**
     * The 'special' value of the {@code Item}, unrelated to the base value.
     */
    private final int specialValue;

    /**
     * The noted id of the {@code Item}, -1 if this definition is noted.
     */
    private final int notedId;

    /**
     * The unnoted id of the {@code Item}, -1 if this definition is unnoted.
     */
    private final int unnotedId;

    /**
     * If the {@code Item} is for members only.
     */
    private final boolean membersOnly;

    /**
     * The weight value of the {@code Item}.
     */
    private final double weight;

    /**
     * If the {@code Item} is tradeable.
     */
    private final boolean tradeable;

    /**
     * The inventory actions of the {@code Item}.
     */
    private final ImmutableSet<String> inventoryActions;

    /**
     * The ground actions of the {@code Item}.
     */
    private final ImmutableSet<String> groundActions;

    /**
     * Creates a new {@link ItemDefinition}.
     *
     * @param id The identifier for the {@code Item}.
     * @param name The name of the {@code Item}.
     * @param examine The description of the {@code Item}.
     * @param stackable If the {@code Item} is stackable.
     * @param baseValue The base value of the {@code Item}, used for shop prices and low/high alch values.
     * @param specialValue The 'special' value of the {@code Item}, unrelated to the base value.
     * @param notedId The noted id of the {@code Item}, -1 if this definition is noted.
     * @param unnotedId The unnoted id of the {@code Item}, -1 if this definition is unnoted.
     * @param membersOnly If the {@code Item} is for members only.
     * @param weight The weight value of the {@code Item}.
     * @param tradeable If the {@code Item} is tradeable.
     * @param inventoryActions The inventory actions of the {@code Item}.
     * @param groundActions The ground actions of the {@code Item}.
     */
    public ItemDefinition(int id, String name, String examine, boolean stackable, int baseValue, int specialValue,
        int notedId, int unnotedId, boolean membersOnly, double weight, boolean tradeable, String[] inventoryActions,
        String[] groundActions) {
        this.id = id;
        this.name = name;
        this.examine = examine;
        this.stackable = stackable;
        this.baseValue = baseValue;
        this.specialValue = specialValue;
        this.notedId = notedId;
        this.unnotedId = unnotedId;
        this.membersOnly = membersOnly;
        this.weight = weight;
        this.tradeable = tradeable;
        this.inventoryActions = ImmutableSet.copyOf(inventoryActions);
        this.groundActions = ImmutableSet.copyOf(groundActions);
    }

    /**
     * @return {@code true} if {@code action} is contained by the backing set of inventory actions.
     */
    public boolean hasInventoryAction(String action) {
        return inventoryActions.contains(action);
    }

    /**
     * @return {@code true} if {@code action} is contained by the backing set of ground actions.
     */
    public boolean hasGroundAction(String action) {
        return groundActions.contains(action);
    }

    /**
     * @return {@code true} if this item can be noted, {@code false} otherwise.
     */
    public boolean canBeNoted() {
        return notedId != -1;
    }

    /**
     * @return {@code true} if this item is noted, {@code false} otherwise.
     */
    public boolean isNoted() {
        return examine.equals("Swap this note at any bank for the equivalent item.");
    }

    /**
     * @return The identifier for the {@code Item}.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The name of the {@code Item}.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The description of the {@code Item}.
     */
    public String getExamine() {
        return examine;
    }

    /**
     * @return If the {@code Item} is stackable.
     */
    public boolean isStackable() {
        return stackable;
    }

    /**
     * @return The base value of the {@code Item}, used for shop prices and low/high alch values.
     */
    public int getBaseValue() {
        return baseValue;
    }

    /**
     * @return The 'special' value of the {@code Item}, unrelated to the base value.
     */
    public int getSpecialValue() {
        return specialValue;
    }

    /**
     * @return The noted id of the {@code Item}, -1 if this definition is noted.
     */
    public int getNotedId() {
        return notedId;
    }

    /**
     * @return The unnoted id of the {@code Item}, -1 if this definition is unnoted.
     */
    public int getUnnotedId() {
        return unnotedId;
    }

    /**
     * @return If the {@code Item} is for members only.
     */
    public boolean isMembersOnly() {
        return membersOnly;
    }

    /**
     * @return The weight value of the {@code Item}.
     */
    public double getWeight() {
        return weight;
    }

    /**
     * @return If the {@code Item} is tradeable.
     */
    public boolean isTradeable() {
        return tradeable;
    }

    /**
     * @return The inventory actions of the {@code Item}.
     */
    public ImmutableSet<String> getInventoryActions() {
        return inventoryActions;
    }

    /**
     * @return The ground actions of the {@code Item}.
     */
    public ImmutableSet<String> getGroundActions() {
        return groundActions;
    }
}