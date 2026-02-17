package io.luna.game.model.item;

import com.google.common.collect.ImmutableList;
import io.luna.game.event.impl.EquipmentChangeEvent;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.def.EquipmentDefinition.Requirement;
import io.luna.game.model.item.RefreshListener.PlayerRefreshListener;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.game.plugin.PluginManager;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

/**
 * An {@link ItemContainer} implementation representing a {@link Player}'s equipped items.
 * <p>
 * This container is a fixed-size equipment "paperdoll" of 14 slots (interface id {@code 1688}). Items are placed into
 * the correct slot using their {@link EquipmentDefinition#getIndex()}.
 * <p>
 * <b>Side effects:</b> Equipment changes trigger:
 * <ul>
 *     <li>bonus recalculation and interface updates (smart-written)</li>
 *     <li>appearance flagging for visible slots</li>
 *     <li>an {@link EquipmentChangeEvent} posted to plugins</li>
 *     <li>weight recalculation (via {@link WeightListener})</li>
 * </ul>
 * <p>
 * <b>Equipping model:</b> {@link #equip(int)} moves an item from inventory to equipment (and may unequip an item first).
 * Two-handed weapon rules are enforced.
 * <p>
 * <b>Bonus model:</b> Bonuses are stored in {@link #bonuses} and maintained incrementally by applying the delta between
 * the old item and the new item. The listener tracks which bonus indices changed and only writes those lines to the UI.
 *
 * @author lare96
 */
public final class Equipment extends ItemContainer {

    /**
     * An {@link ItemContainerListener} that:
     * <ul>
     *     <li>keeps {@link #bonuses} in sync with equipment contents</li>
     *     <li>smart-writes changed bonuses to the equipment bonus interface</li>
     *     <li>flags appearance for slots that affect the player's look</li>
     *     <li>posts {@link EquipmentChangeEvent} to the plugin system</li>
     * </ul>
     */
    private final class EquipmentListener implements ItemContainerListener {

        /**
         * The owning player.
         */
        private final Player player;

        /**
         * Tracks which bonus indices need to be written to the interface.
         * <p>
         * This is a "dirty set": indices are marked during updates and cleared after {@link #writeBonuses()}.
         */
        private final BitSet pending = new BitSet(12);

        /**
         * Creates a new {@link EquipmentListener}.
         *
         * @param player The owning player.
         */
        public EquipmentListener(Player player) {
            this.player = player;
        }

        /**
         * Handles a single-slot update.
         * <p>
         * If the item id changed, bonuses are updated immediately and written to the UI.
         * Appearance is flagged for visible equipment slots. An {@link EquipmentChangeEvent} is posted regardless.
         */
        @Override
        public void onSingleUpdate(int index, ItemContainer items, Item oldItem, Item newItem) {
            if (isIdUnequal(oldItem, newItem)) {
                updateBonus(oldItem, newItem);
                writeBonuses();
                flagAppearance(index);
            }
            sendEvent(index, oldItem, newItem);
        }

        /**
         * Handles a slot update that occurs as part of a bulk update.
         * <p>
         * Bonuses are updated per-slot, but UI writes are deferred until {@link #onBulkUpdateCompleted(ItemContainer)}.
         * Appearance flagging still happens immediately.
         */
        @Override
        public void onBulkUpdate(int index, ItemContainer items, Item oldItem, Item newItem) {
            if (isIdUnequal(oldItem, newItem)) {
                updateBonus(oldItem, newItem);
                flagAppearance(index);
            }
            sendEvent(index, oldItem, newItem);
        }

        /**
         * Called after a bulk update completes; flushes any pending bonus UI writes.
         */
        @Override
        public void onBulkUpdateCompleted(ItemContainer items) {
            writeBonuses();
        }

        /**
         * Posts an {@link EquipmentChangeEvent} to the player's {@link PluginManager}.
         *
         * @param index The equipment slot index that changed.
         * @param oldItem The old item (nullable).
         * @param newItem The new item (nullable).
         */
        private void sendEvent(int index, Item oldItem, Item newItem) {
            PluginManager plugins = player.getPlugins();
            plugins.post(new EquipmentChangeEvent(player, index, oldItem, newItem));
        }

        /**
         * Returns {@code true} if {@code oldItem} and {@code newItem} have different ids, including {@code null} changes.
         * <p>
         * This is used to avoid recomputing bonuses when only the amount changes (e.g., stackable ammo count).
         *
         * @param oldItem The old item (nullable).
         * @param newItem The new item (nullable).
         * @return {@code true} if the ids differ or either value is {@code null} while the other is not.
         */
        private boolean isIdUnequal(Item oldItem, Item newItem) {
            if (oldItem == null && newItem == null) {
                return false;
            }
            return oldItem == null || newItem == null || oldItem.getId() != newItem.getId();
        }

        /**
         * Applies the bonus delta from {@code oldItem} → {@code newItem} to {@link #bonuses}.
         * <p>
         * For each bonus index:
         * <ul>
         *     <li>subtract the old item's bonus</li>
         *     <li>add the new item's bonus</li>
         *     <li>mark the bonus index dirty if either side is non-zero</li>
         * </ul>
         *
         * @param oldItem The item being removed/replaced (nullable).
         * @param newItem The item being equipped/replaced (nullable).
         */
        private void updateBonus(Item oldItem, Item newItem) {
            IntUnaryOperator oldBonusFunction, newBonusFunction;

            if (oldItem != null) {
                EquipmentDefinition equipmentDefinition = oldItem.getEquipDef();
                oldBonusFunction = equipmentDefinition::getBonus;
            } else {
                oldBonusFunction = id -> 0;
            }

            if (newItem != null) {
                EquipmentDefinition equipmentDefinition = newItem.getEquipDef();
                newBonusFunction = equipmentDefinition::getBonus;
            } else {
                newBonusFunction = id -> 0;
            }

            for (int index = 0; index < bonuses.length; index++) {
                int old = oldBonusFunction.applyAsInt(index);
                int replace = newBonusFunction.applyAsInt(index);

                /*
                 * Mark as dirty if any non-zero value might affect what is displayed.
                 */
                if (old != 0 || replace != 0) {
                    pending.set(index);
                }

                /*
                 * Apply old (-) and new (+) bonuses.
                 */
                bonuses[index] = bonuses[index] - old + replace;
            }
        }

        /**
         * Writes any dirty bonus lines to the equipment bonus interface.
         * <p>
         * This method performs a "smart write": only indices marked in {@link #pending} are written.
         * After writing, the dirty set is cleared.
         */
        private void writeBonuses() {
            StringBuilder sb = new StringBuilder();
            for (int index = 0; index < bonuses.length; index++) {
                if (pending.get(index)) {
                    String name = BONUS_NAMES.get(index);
                    int value = bonuses[index];
                    boolean positive = value >= 0;

                    /*
                     * Widget mapping note:
                     * Equipment bonuses are displayed in the classic 1675+ interface range, with an offset adjustment
                     * for the last two rows in some clients.
                     */
                    int widget = 1675 + index + (index == 10 || index == 11 ? 1 : 0);

                    sb.append(name).append(": ")
                            .append(positive ? "+" : "")
                            .append(value);

                    player.sendText(sb.toString(), widget);
                    sb.setLength(0);
                }
            }
            pending.clear();
        }
    }

    /**
     * Equipment slot: head.
     */
    public static final int HEAD = 0;

    /**
     * Equipment slot: cape.
     */
    public static final int CAPE = 1;

    /**
     * Equipment slot: amulet.
     */
    public static final int AMULET = 2;

    /**
     * Equipment slot: weapon.
     */
    public static final int WEAPON = 3;

    /**
     * Equipment slot: chest.
     */
    public static final int CHEST = 4;

    /**
     * Equipment slot: shield.
     */
    public static final int SHIELD = 5;

    /**
     * Equipment slot: legs.
     */
    public static final int LEGS = 7;

    /**
     * Equipment slot: hands.
     */
    public static final int HANDS = 9;

    /**
     * Equipment slot: boots.
     */
    public static final int BOOTS = 10;

    /**
     * Equipment slot: ring.
     */
    public static final int RING = 12;

    /**
     * Equipment slot: ammunition.
     */
    public static final int AMMUNITION = 13;

    /**
     * Bonus index: stab attack.
     */
    public static final int STAB_ATTACK = 0;

    /**
     * Bonus index: slash attack.
     */
    public static final int SLASH_ATTACK = 1;

    /**
     * Bonus index: crush attack.
     */
    public static final int CRUSH_ATTACK = 2;

    /**
     * Bonus index: magic attack.
     */
    public static final int MAGIC_ATTACK = 3;

    /**
     * Bonus index: ranged attack.
     */
    public static final int RANGED_ATTACK = 4;

    /**
     * Bonus index: stab defence.
     */
    public static final int STAB_DEFENCE = 5;

    /**
     * Bonus index: slash defence.
     */
    public static final int SLASH_DEFENCE = 6;

    /**
     * Bonus index: crush defence.
     */
    public static final int CRUSH_DEFENCE = 7;

    /**
     * Bonus index: magic defence.
     */
    public static final int MAGIC_DEFENCE = 8;

    /**
     * Bonus index: ranged defence.
     */
    public static final int RANGED_DEFENCE = 9;

    /**
     * Bonus index: strength.
     */
    public static final int STRENGTH = 10;

    /**
     * Bonus index: prayer.
     */
    public static final int PRAYER = 11;

    /**
     * UI labels for each bonus index, in the same order as {@link #bonuses}.
     */
    public static final ImmutableList<String> BONUS_NAMES = ImmutableList.of(
            "Stab", "Slash", "Crush", "Magic", "Range",
            "Stab", "Slash", "Crush", "Magic", "Range",
            "Strength", "Prayer"
    );

    /**
     * Error used by deprecated index-based overloads that are intentionally unsupported for equipment.
     */
    private static final String ERROR_MSG = "Use set(index, Item) or add(Item) or remove(Item) instead.";

    /**
     * The owning player.
     */
    private final Player player;

    /**
     * The player's inventory (used for equip/unequip transfers).
     */
    private final Inventory inventory;

    /**
     * The listener instance used for bonus tracking and events.
     */
    private final EquipmentListener equipmentListener;

    /**
     * Current equipment bonus totals for this player.
     * <p>
     * The ordering matches {@link #BONUS_NAMES} and the bonus index constants.
     */
    private final int[] bonuses = new int[12];

    /**
     * Creates a new {@link Equipment} container.
     *
     * @param player The owning player.
     */
    public Equipment(Player player) {
        super(14, StackPolicy.STANDARD, 1688);
        this.player = player;
        inventory = player.getInventory();

        EquipmentListener equipmentListener = new EquipmentListener(player);
        this.equipmentListener = equipmentListener;

        setListeners(new PlayerRefreshListener(player, ERROR_MSG), equipmentListener, new WeightListener(player));
    }

    /**
     * Adds an item to equipment by placing it into its configured equipment slot.
     * <p>
     * For stackable equipment slots (commonly ammunition), this will increase the stack amount if the same id is already
     * equipped in that slot.
     *
     * @param item The item to equip into its slot.
     * @return {@code true} if the item was equipped (or stacked) successfully.
     */
    @Override
    public boolean add(Item item) {
        int index = item.getEquipDef().getIndex();
        int amount = item.getAmount();

        if (item.getItemDef().isStackable() && computeIdForIndex(index) == item.getId()) {
            int newAmount = amount + computeAmountForIndex(index);
            if (newAmount < 0) {
                /*
                 * Overflow guard.
                 */
                return false;
            }
            amount = newAmount;
        }

        set(index, item.withAmount(amount));
        return true;
    }

    /**
     * Removes an item from equipment by decrementing/removing it from its configured equipment slot.
     * <p>
     * For non-stackable equipment this removes the item entirely. For stackable equipment this decrements the stack,
     * removing it completely when the resulting amount becomes {@code <= 0}.
     *
     * @param item The item to remove (amount is treated as the decrement for stackables).
     * @return {@code true} if removed; {@code false} if the slot does not contain the item id.
     */
    @Override
    public boolean remove(Item item) {
        int index = item.getEquipDef().getIndex();

        if (computeIdForIndex(index) != item.getId()) {
            return false;
        }

        int newAmount = computeAmountForIndex(index) - item.getAmount();
        if (newAmount <= 0) {
            set(index, null);
        } else {
            set(index, item.withAmount(newAmount));
        }
        return true;
    }

    /**
     * @deprecated Equipment does not support index-directed insertion. Use {@link #set(int, Item)} or
     * {@link #add(Item)} instead.
     */
    @Deprecated
    @Override
    public boolean add(int preferredIndex, Item item) {
        throw new UnsupportedOperationException(ERROR_MSG);
    }

    /**
     * @deprecated Equipment does not support index-directed removal. Use {@link #set(int, Item)} or
     * {@link #remove(Item)} instead.
     */
    @Deprecated
    @Override
    public boolean remove(int preferredIndex, Item item) {
        throw new UnsupportedOperationException(ERROR_MSG);
    }

    /**
     * Equips the item at {@code inventoryIndex} from the player's inventory.
     * <p>
     * This method:
     * <ul>
     *     <li>validates the inventory slot and ensures the item is equippable</li>
     *     <li>checks equipment requirements and sends failure messages if needed</li>
     *     <li>enforces the two-handed weapon rule with the shield slot</li>
     *     <li>moves any displaced item back into the inventory (if space permits)</li>
     * </ul>
     *
     * @param inventoryIndex The inventory slot index.
     * @return {@code true} if the item was equipped successfully.
     */
    public boolean equip(int inventoryIndex) {
        Item inventoryItem = inventory.get(inventoryIndex);
        if (inventoryItem == null) {
            return false;
        }

        EquipmentDefinition equipDef = inventoryItem.getEquipDef();
        if (equipDef == null) {
            /*
             * Not equippable.
             */
            return false;
        }

        int equipIndex = equipDef.getIndex();

        Optional<Requirement> failedReq = equipDef.getFailedRequirement(player);
        if (failedReq.isPresent()) {
            failedReq.get().sendFailureMessage(player);
            return false;
        }

        int unequipIndex = -1;
        if (equipIndex == WEAPON && equipDef.isTwoHanded()) {
            unequipIndex = SHIELD;
        } else if (equipIndex == SHIELD && occupied(WEAPON)) {
            EquipmentDefinition weaponDef = get(WEAPON).getEquipDef();
            if (weaponDef != null && weaponDef.isTwoHanded()) {
                unequipIndex = WEAPON;
            }
        }

        /*
         * If we must unequip something, ensure we have space if this operation would result in two items needing
         * to go back into inventory (the displaced equipIndex item and the forced-unequip item).
         */
        if (unequipIndex != -1) {
            int remaining = inventory.computeRemainingSize();
            if (remaining == 0 && occupied(unequipIndex) && occupied(equipIndex)) {
                inventory.onCapacityExceeded();
                return false;
            }
        }

        inventory.set(inventoryIndex, null);
        if (unequipIndex != -1) {
            unequip(unequipIndex);
        }

        Item currentlyEquipped = get(equipIndex);
        if (currentlyEquipped != null) {
            inventory.add(currentlyEquipped);
        }
        set(equipIndex, inventoryItem);
        return true;
    }

    /**
     * Unequips the item at {@code equipmentIndex} into the player's inventory.
     *
     * @param equipmentIndex The equipment slot index.
     * @return {@code true} if an item was unequipped successfully.
     */
    public boolean unequip(int equipmentIndex) {
        Item equipmentItem = get(equipmentIndex);
        if (equipmentItem == null) {
            return false;
        }

        if (player.getInventory().add(equipmentItem)) {
            set(equipmentIndex, null);
            return true;
        }
        return false;
    }

    /**
     * Flags the appearance update block if the given equipment slot affects visual appearance.
     * <p>
     * Rings and ammunition typically do not affect the player model in this revision.
     *
     * @param index The equipment slot index.
     */
    private void flagAppearance(int index) {
        if (index != RING && index != AMMUNITION) {
            player.getFlags().flag(UpdateFlag.APPEARANCE);
        }
    }

    /**
     * Recomputes and writes all equipment bonuses to the bonus interface.
     * <p>
     * Intended for login/refresh scenarios where the equipment container already contains items and the bonus interface
     * must be populated.
     */
    public void loadBonuses() {
        Arrays.fill(bonuses, 0);
        for (Item item : this) {
            if (item == null) {
                continue;
            }
            equipmentListener.updateBonus(null, item);
        }

        equipmentListener.pending.set(0, 12);
        equipmentListener.writeBonuses();
    }
}
