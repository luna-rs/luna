package io.luna.game.model.item;

import com.google.common.collect.ImmutableList;
import io.luna.game.event.impl.EquipmentChangeEvent;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.item.RefreshListener.PlayerRefreshListener;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.game.plugin.PluginManager;

import java.util.BitSet;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.IntUnaryOperator;

import static io.luna.util.OptionalUtils.ifPresent;
import static io.luna.util.OptionalUtils.mapToInt;
import static io.luna.util.OptionalUtils.matches;

/**
 * An item container model representing a player's equipment.
 *
 * @author lare96 
 */
public final class Equipment extends ItemContainer {

    /**
     * A listener that updates equipment bonuses and posts equipment change events.
     */
    private final class EquipmentListener implements ItemContainerListener {

        /**
         * The player.
         */
        private final Player player;

        /**
         * A bit set that keeps track of which bonuses need to be updated.
         */
        private final BitSet writeBonuses = new BitSet(12);

        /**
         * Creates a new {@link EquipmentListener}.
         *
         * @param player The player.
         */
        public EquipmentListener(Player player) {
            this.player = player;
        }

        @Override
        public void onSingleUpdate(int index, ItemContainer items, Optional<Item> oldItem, Optional<Item> newItem) {
            if (isIdUnequal(oldItem, newItem)) {
                updateBonus(oldItem, newItem);
                writeBonuses();
                flagAppearance(index);
            }
            sendEvent(index, oldItem, newItem);
        }

        @Override
        public void onBulkUpdate(int index, ItemContainer items, Optional<Item> oldItem, Optional<Item> newItem) {
            if (isIdUnequal(oldItem, newItem)) {
                updateBonus(oldItem, newItem);
                flagAppearance(index);
            }
            sendEvent(index, oldItem, newItem);
        }

        @Override
        public void onBulkUpdateCompleted(ItemContainer items) {
            writeBonuses();
        }

        /**
         * Posts an equipment change event.
         *
         * @param index The index of the change.
         * @param oldItem The old item.
         * @param newItem The new item.
         */
        private void sendEvent(int index, Optional<Item> oldItem, Optional<Item> newItem) {
            PluginManager plugins = player.getPlugins();
            plugins.post(new EquipmentChangeEvent(player, index, oldItem, newItem));
        }

        /**
         * Determines if the identifiers are unequal.
         *
         * @param oldItem The old item.
         * @param newItem The new item.
         * @return {@code true} if the identifiers are unequal.
         */
        private boolean isIdUnequal(Optional<Item> oldItem, Optional<Item> newItem) {
            OptionalInt oldId = mapToInt(oldItem, Item::getId);
            OptionalInt newId = mapToInt(newItem, Item::getId);
            return !oldId.equals(newId);
        }

        /**
         * Updates bonuses for two potential items.
         *
         * @param oldItem The old item.
         * @param newItem The new item.
         */
        private void updateBonus(Optional<Item> oldItem, Optional<Item> newItem) {
            IntUnaryOperator oldBonusFunction, newBonusFunction;

            if (oldItem.isPresent()) {
                var equipmentDefinition = oldItem.get().getEquipDef();
                oldBonusFunction = equipmentDefinition::getBonus;
            } else {
                oldBonusFunction = id -> 0;
            }

            if (newItem.isPresent()) {
                var equipmentDefinition = newItem.get().getEquipDef();
                newBonusFunction = equipmentDefinition::getBonus;
            } else {
                newBonusFunction = id -> 0;
            }

            for (int index = 0; index < bonuses.length; index++) {
                int old = oldBonusFunction.applyAsInt(index);
                int replace = newBonusFunction.applyAsInt(index);

                // Bonus(es) nonzero, this index needs updating.
                if (old != 0 || replace != 0) {
                    writeBonuses.set(index);
                }

                // Apply old (-) and new (+) bonuses.
                bonuses[index] = bonuses[index] - old + replace;
            }
        }

        /**
         * Does a smart write of the bonuses to the equipment interface.
         */
        private void writeBonuses() {
            StringBuilder sb = new StringBuilder();
            for (int index = 0; index < bonuses.length; index++) {
                // Smart write, only write bonuses if they've changed.
                if (writeBonuses.get(index)) {
                    String name = BONUS_NAMES.get(index);
                    int value = bonuses[index];
                    boolean positive = value >= 0;
                    int widget = 1675 + index + (index == 10 || index == 11 ? 1 : 0);

                    // Append the bonus string.
                    sb.append(name).append(": ").
                            append(positive ? "+" : "").
                            append(value);

                    // Queue the packet to display it.
                    player.sendText(sb.toString(), widget);
                    sb.setLength(0);
                }
            }
            writeBonuses.clear();
        }
    }

    /**
     * The head index.
     */
    public static final int HEAD = 0;

    /**
     * The cape index.
     */
    public static final int CAPE = 1;

    /**
     * The amulet index.
     */
    public static final int AMULET = 2;

    /**
     * The weapon index.
     */
    public static final int WEAPON = 3;

    /**
     * The chest index.
     */
    public static final int CHEST = 4;

    /**
     * The shield index.
     */
    public static final int SHIELD = 5;

    /**
     * The legs index.
     */
    public static final int LEGS = 7;

    /**
     * The hands index.
     */
    public static final int HANDS = 9;

    /**
     * The feet index.
     */
    public static final int FEET = 10;

    /**
     * The ring index.
     */
    public static final int RING = 12;

    /**
     * The ammunition index.
     */
    public static final int AMMUNITION = 13;

    /**
     * The stab attack bonus index.
     */
    public static final int STAB_ATTACK = 0;

    /**
     * The slash attack bonus index.
     */
    public static final int SLASH_ATTACK = 1;

    /**
     * The crush attack bonus index.
     */
    public static final int CRUSH_ATTACK = 2;

    /**
     * The magic attack bonus index.
     */
    public static final int MAGIC_ATTACK = 3;

    /**
     * The ranged attack bonus index.
     */
    public static final int RANGED_ATTACK = 4;

    /**
     * The stab defence bonus index.
     */
    public static final int STAB_DEFENCE = 5;

    /**
     * The slash defence bonus index.
     */
    public static final int SLASH_DEFENCE = 6;

    /**
     * The crush defence bonus index.
     */
    public static final int CRUSH_DEFENCE = 7;

    /**
     * The magic defence bonus index.
     */
    public static final int MAGIC_DEFENCE = 8;

    /**
     * The ranged defence bonus index.
     */
    public static final int RANGED_DEFENCE = 9;

    /**
     * The strength bonus index.
     */
    public static final int STRENGTH = 10;

    /**
     * The prayer bonus index.
     */
    public static final int PRAYER = 11;

    /**
     * An immutable list of bonus names.
     */
    public static final ImmutableList<String> BONUS_NAMES = ImmutableList.of(
        "Stab", "Slash", "Crush", "Magic", "Range", "Stab",
        "Slash", "Crush", "Magic", "Range", "Strength", "Prayer"
    );

    /**
     * An error message.
     */
    private static final String ERROR_MSG = "Use set(index, Item) or add(Item) or remove(Item) instead.";

    /**
     * The player.
     */
    private final Player player;

    /**
     * The inventory.
     */
    private final Inventory inventory;

    /**
     * The equipment listener.
     */
    private final EquipmentListener equipmentListener;

    /**
     * An array of equipment bonuses.
     */
    private final int[] bonuses = new int[12];

    /**
     * Creates a new {@link Equipment}.
     *
     * @param player The player.
     */
    public Equipment(Player player) {
        super(14, StackPolicy.STANDARD, 1688);
        this.player = player;
        inventory = player.getInventory();

        EquipmentListener equipmentListener = new EquipmentListener(player);
        this.equipmentListener = equipmentListener;

        setListeners(new PlayerRefreshListener(player, ERROR_MSG),
                equipmentListener,
                new WeightListener(player));
    }

    @Override
    public boolean add(Item item) {
        int index = item.getEquipDef().getIndex();
        int amount = item.getAmount();

        // Increase amount for stackable items.
        if (item.getItemDef().isStackable() &&
                matches(computeIdForIndex(index), item::getId)) {
            amount += computeAmountForIndex(index);
        }

        // Equip the item.
        Item newItem = item.withAmount(amount);
        set(index, newItem);
        return true;
    }

    @Override
    public boolean remove(Item item) {
        int index = item.getEquipDef().getIndex();

        // Item does not exist.
        if (!matches(computeIdForIndex(index), item::getId)) {
            return false;
        }

        // Calculate new item amount after removal.
        int newAmount = computeAmountForIndex(index) - item.getAmount();
        if (newAmount <= 0) {
            // If it's below or equal to 0, remove the item.
            set(index, null);
        } else {
            // Otherwise set the new amount.
            set(index, item.withAmount(newAmount));
        }
        return true;
    }

    /**
     * @deprecated This always throws an exception. Use {@code set(int, Item)} or {@code add(Item)}
     * instead.
     */
    @Deprecated
    @Override
    public boolean add(int preferredIndex, Item item) {
        throw new UnsupportedOperationException(ERROR_MSG);
    }

    /**
     * @deprecated This always throws an exception. Use {@code set(int, Item)} or {@code remove(Item)}
     * instead.
     */
    @Deprecated
    @Override
    public boolean remove(int preferredIndex, Item item) {
        throw new UnsupportedOperationException(ERROR_MSG);
    }

    /**
     * Equips an item from the inventory.
     *
     * @param inventoryIndex The inventory index of the item.
     * Returns {@code true} if successful.
     */
    public boolean equip(int inventoryIndex) {

        // Validate index.
        Item inventoryItem = inventory.get(inventoryIndex);
        if (inventoryItem == null) {
            return false;
        }
        EquipmentDefinition equipDef = inventoryItem.getEquipDef();
        int equipIndex = equipDef.getIndex();

        // Check equipment requirements.
        boolean failedToMeet = ifPresent(equipDef.getFailedRequirement(player),
                req -> req.sendFailureMessage(player));
        if (failedToMeet) {
            return false;
        }

        // Unequip something if we have to.
        OptionalInt unequipIndex = OptionalInt.empty();
        if (equipIndex == WEAPON && equipDef.isTwoHanded()) {

            // Equipping 2h weapon, so unequip shield.
            unequipIndex = OptionalInt.of(SHIELD);
        } else if (equipIndex == Equipment.SHIELD &&
                occupied(WEAPON) &&
                get(WEAPON).getEquipDef().isTwoHanded()) {

            // Equipping shield, so unequip 2h weapon.
            unequipIndex = OptionalInt.of(WEAPON);
        }

        // Check if inventory has enough space.
        if (unequipIndex.isPresent()) {
            int remaining = inventory.computeRemainingSize();
            if (remaining == 0 && occupied(unequipIndex.getAsInt()) && occupied(equipIndex)) {
                inventory.fireCapacityExceededEvent();
                return false;
            }
        }

        // Equip item.
        inventory.set(inventoryIndex, null);
        unequipIndex.ifPresent(this::unequip);

        Item equipItem = get(equipIndex);
        if (equipItem == null || inventory.add(equipItem)) {
            set(equipIndex, inventoryItem);
        }
        return true;
    }

    /**
     * Unequips an item from the player's equipment.
     *
     * @param equipmentIndex The equipment index of the item.
     * @return {@code true} if successful.
     */
    public boolean unequip(int equipmentIndex) {
        // Validate index.
        Item equipmentItem = get(equipmentIndex);
        if (equipmentItem == null) {
            return false;
        }

        // Unequip item.
        if (player.getInventory().add(equipmentItem)) {
            set(equipmentIndex, null);
            return true;
        }
        return false;
    }

    /**
     * Flags the appearance block if required.
     *
     * @param index The index to flag.
     */
    private void flagAppearance(int index) {
        if (index != RING && index != AMMUNITION) {
            player.getFlags().flag(UpdateFlag.APPEARANCE);
        }
    }

    /**
     * Loads equipment bonuses for the Player by updating and displaying them.
     */
    public void loadBonuses() {
        for (Item item : this) {
            if (item == null) {
                continue;
            }
            // Update bonuses.
            equipmentListener.updateBonus(Optional.empty(), Optional.of(item));
        }
        // Write them all.
        equipmentListener.writeBonuses.set(0, 12);
        equipmentListener.writeBonuses();
    }
}
