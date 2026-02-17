package io.luna.game.model.item;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.def.ItemDefinition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represents single item stack (id + amount).
 * <p>
 * Items are identified by an {@code id} that maps to an {@link ItemDefinition}. The {@code amount} represents the
 * stack size for stackable items, or a per-slot quantity for non-stackable items.
 * <p>
 * <b>Immutability:</b> Instances of this class are immutable. Any "mutation" style operation (such as
 * {@link #withAmount(int)} or {@link #addAmount(int)}) returns a new {@link Item} instance.
 * <p>
 * <b>Equality:</b> {@link #equals(Object)} compares both {@code id} and {@code amount}. If you need a "does this
 * stack contain at least X" check, use {@link #contains(Item)} instead.
 * <p>
 * <b>Dynamic items:</b> Some items may be instances of {@link DynamicItem} (items with attributes). Use
 * {@link #isDynamic()} and {@link #asDynamic()} when you need to work with attributes.
 *
 * @author lare96
 */
public class Item {

    /**
     * The logger used for item lookup warnings.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The comparator used to sort items by their in-game base value (descending).
     * <p>
     * The value is taken from the <b>unnoted</b> item definition's {@link ItemDefinition#getValue()}. This prevents
     * noted variants from appearing to have different values purely due to being noted.
     */
    public static final Comparator<Item> VALUE_COMPARATOR = Comparator.<Item>comparingInt(it ->
            it.getUnnotedItemDef().getValue()).reversed();

    /**
     * The item ids that should not appear in name-based lookup results.
     * <p>
     * This is primarily intended to hide internal/test/unwanted entries from UI search or chat commands.
     */
    private static final ImmutableSet<Integer> SEARCH_RESTRICTED =
            ImmutableSet.of(6564, 6565, 6566, 617);

    /**
     * Retrieves an {@link Item} by exact name and amount.
     * <p>
     * This method performs a definition lookup using {@link #findId(String, boolean)} with {@code noted=false} and
     * returns null if no matching tradeable, non-restricted definition is found.
     *
     * @param name The exact item name to match against {@link ItemDefinition#getName()}.
     * @param amount The desired amount.
     * @return A new item instance, or null if not found.
     */
    public static Item byName(String name, int amount) {
        Integer id = findId(name, false);
        if (id == null) {
            return null;
        }
        return new Item(id, amount);
    }

    /**
     * Retrieves an {@link Item} by exact name with an amount of {@code 1}.
     *
     * @param name The exact item name.
     * @return A new item instance, or null if not found.
     */
    public static Item byName(String name) {
        return byName(name, 1);
    }

    /**
     * Retrieves a <b>noted</b> {@link Item} by exact name and amount.
     * <p>
     * This method resolves the item id with {@link #findId(String, boolean)} using {@code noted=true}. Returns null
     * if not found.
     *
     * @param name The exact item name.
     * @param amount The desired amount.
     * @return A new noted item instance, or null if not found.
     */
    public static Item byNameNoted(String name, int amount) {
        Integer id = findId(name, true);
        if (id == null) {
            return null;
        }
        return new Item(id, amount);
    }

    /**
     * Finds an item id by exact name.
     * <p>
     * Lookup filters:
     * <ul>
     *   <li>Id is not in {@link #SEARCH_RESTRICTED}</li>
     *   <li>Definition is tradeable ({@link ItemDefinition#isTradeable()})</li>
     *   <li>Definition name equals {@code name} exactly</li>
     *   <li>Definition noted flag matches {@code noted}</li>
     * </ul>
     * <p>
     * If no match exists, a warning is logged and null is returned.
     * <p>
     * <b>Performance note:</b> This currently performs a linear scan via {@code lookup(...)}. If used frequently
     * (e.g., live search), consider building a name → id index.
     *
     * @param name The exact item name to resolve.
     * @param noted Whether to search for the noted definition variant.
     * @return The resolved item id, or null if not found/invalid.
     */
    public static Integer findId(String name, boolean noted) {
        return ItemDefinition.ALL.lookup(def ->
                        !SEARCH_RESTRICTED.contains(def.getId())
                                && def.isTradeable()
                                && def.getName().equals(name)
                                && def.isNoted() == noted)
                .map(ItemDefinition::getId)
                .orElseGet(() -> {
                    logger.warn("Item ({}) was not valid or found.", name);
                    return null;
                });
    }

    /**
     * The item identifier.
     */
    private final int id;

    /**
     * The stack amount (must be non-negative).
     */
    private final int amount;

    /**
     * Creates a new {@link Item}.
     *
     * @param id The item id. Must be valid per {@link ItemDefinition#isIdValid(int)}.
     * @param amount The stack amount (must be >= 0).
     * @throws IllegalArgumentException If {@code id} is invalid or {@code amount} is negative.
     */
    public Item(int id, int amount) {
        checkArgument(ItemDefinition.isIdValid(id), "id [" + id + "] out of range");
        checkArgument(amount >= 0, "amount <= 0");

        this.id = id;
        this.amount = amount;
    }

    /**
     * Creates a new {@link Item} with amount {@code 1}.
     *
     * @param id The item id.
     */
    public Item(int id) {
        this(id, 1);
    }

    /**
     * Two {@link Item}s are equal only if their {@code id} and {@code amount} are both equal.
     * <p>
     * For "at least X amount" comparisons, use {@link #contains(Item)}.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Item) {
            Item other = (Item) obj;
            return id == other.id && amount == other.amount;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, amount);
    }

    @Override
    public String toString() {
        ItemDefinition def = getItemDef();
        ToStringHelper helper = MoreObjects.toStringHelper(this).add("id", id);
        if (def != null) {
            helper.add("name", def.getName());
        }
        helper.add("amount", amount);
        return helper.toString();
    }

    /**
     * Checks whether this stack "contains" {@code other}.
     * <p>
     * Containment means:
     * <ul>
     *   <li>Same {@code id}</li>
     *   <li>This {@code amount} is greater than or equal to {@code other.amount}</li>
     * </ul>
     *
     * @param other The required item stack.
     * @return {@code true} if this stack satisfies the required id and amount.
     */
    public boolean contains(Item other) {
        if (other == null) {
            return false;
        }
        return id == other.id && amount >= other.amount;
    }

    /**
     * @return The item id.
     */
    public int getId() {
        return id;
    }

    /**
     * Creates a new item with {@code newId} and the same {@link #amount}.
     * <p>
     * If {@code newId} equals the current id, this instance is returned.
     *
     * @param newId The new item id.
     * @return The new item instance (or this).
     */
    public Item withId(int newId) {
        if (id == newId) {
            return this;
        }
        return new Item(newId, amount);
    }

    /**
     * @return The stack amount.
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Returns a new item with {@code amount + add} as the new amount.
     * <p>
     * This method clamps overflow/underflow:
     * <ul>
     *   <li>If adding a positive value overflows int, the amount becomes {@link Integer#MAX_VALUE}.</li>
     *   <li>If adding a negative value underflows below 0, the amount becomes 0.</li>
     * </ul>
     *
     * @param add The delta to apply (may be negative).
     * @return A new item with the adjusted amount.
     */
    public Item addAmount(int add) {
        boolean positive = add > 0;
        int newAmount = amount + add;

        if (newAmount < 0) {
            newAmount = positive ? Integer.MAX_VALUE : 0;
        }
        return new Item(id, newAmount);
    }

    /**
     * Creates a new item with {@code newAmount} and the same {@link #id}.
     * <p>
     * If {@code newAmount} equals the current amount, this instance is returned.
     *
     * @param newAmount The new amount (must be >= 0).
     * @return The new item instance (or this).
     */
    public Item withAmount(int newAmount) {
        if (amount == newAmount) {
            return this;
        }
        return new Item(id, newAmount);
    }

    /**
     * Creates an {@link IndexedItem} using this item's id/amount and the provided slot index.
     *
     * @param index The container slot index.
     * @return A new indexed item instance.
     */
    public IndexedItem withIndex(int index) {
        return new IndexedItem(index, id, amount);
    }

    /**
     * Returns the unnoted {@link ItemDefinition} for this item.
     * <p>
     * If this item is already unnoted, this returns the same definition as {@link #getItemDef()}.
     *
     * @return The unnoted item definition for this item.
     */
    public ItemDefinition getUnnotedItemDef() {
        ItemDefinition current = getItemDef();
        return current.getUnnotedId().isPresent()
                ? ItemDefinition.ALL.retrieve(current.getUnnotedId().getAsInt())
                : current;
    }

    /**
     * Checks whether this instance is a {@link DynamicItem}.
     * <p>
     * Dynamic items typically carry extra attributes/state beyond id/amount (e.g., charges, owner binding, custom
     * metadata). Many container operations treat dynamic items as non-stackable.
     *
     * @return {@code true} if this item is a {@link DynamicItem}.
     */
    public boolean isDynamic() {
        return this instanceof DynamicItem;
    }

    /**
     * Casts this item to {@link DynamicItem}.
     * <p>
     * Call {@link #isDynamic()} first to avoid {@link ClassCastException}.
     *
     * @return This item as a dynamic item.
     * @throws ClassCastException If this item is not dynamic.
     */
    public DynamicItem asDynamic() {
        return (DynamicItem) this;
    }

    /**
     * Retrieves the {@link ItemDefinition} for this item id.
     *
     * @return The item definition (as stored in {@link ItemDefinition#ALL}).
     */
    public ItemDefinition getItemDef() {
        return ItemDefinition.ALL.retrieve(id);
    }

    /**
     * Retrieves the {@link EquipmentDefinition} for this item id.
     * <p>
     * Not all items have an equipment definition; depending on implementation, {@code retrieve(id)} may return a
     * default/empty definition or throw if undefined.
     *
     * @return The equipment definition for this item.
     */
    public EquipmentDefinition getEquipDef() {
        return EquipmentDefinition.ALL.retrieve(id);
    }
}
