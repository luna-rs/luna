package io.luna.game.model.item;

import com.google.common.collect.ImmutableList;
import io.luna.game.event.impl.EquipmentChangeEvent;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.def.EquipmentDefinition.Requirement;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.game.plugin.PluginManager;
import io.luna.net.msg.out.WidgetTextMessageWriter;

import java.util.Optional;
import java.util.stream.IntStream;

/**
 * An item container model representing a player's equipment.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class Equipment extends ItemContainer {

    /**
     * An adapter listening for equipment changes.
     */
    private final class EquipmentListener extends ItemContainerAdapter {

        /**
         * Creates a new {@link EquipmentListener}.
         */
        public EquipmentListener() {
            super(player);
        }

        @Override
        public int getWidgetId() {
            return EQUIPMENT_DISPLAY_ID;
        }

        @Override
        public String getCapacityExceededMsg() {
            throw new IllegalStateException(ERROR_MSG);
        }

        @Override
        public void onSingleUpdate(ItemContainer items, Optional<Item> oldItem, Optional<Item> newItem, int index) {
            super.onSingleUpdate(items, oldItem, newItem, index);

            updateBonus(oldItem, newItem);
            writeBonuses();
            sendEvent(oldItem, newItem, index);
        }

        @Override
        public void onBulkUpdate(ItemContainer items, Optional<Item> oldItem, Optional<Item> newItem, int index) {
            super.onBulkUpdate(items, oldItem, newItem, index);

            updateBonus(oldItem, newItem);
            sendEvent(oldItem, newItem, index);
        }

        @Override
        public void onBulkUpdateCompleted(ItemContainer items) {
            super.onBulkUpdateCompleted(items);

            writeBonuses();
        }

        /**
         * Posts an equipment change event.
         *
         * @param oldItem The old item on the index.
         * @param newItem The new item on the index.
         * @param index The index the change occurred on.
         */
        private void sendEvent(Optional<Item> oldItem, Optional<Item> newItem, int index) {
            PluginManager plugins = player.getPlugins();
            plugins.post(new EquipmentChangeEvent(player, index, oldItem, newItem));
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
     * The stab attack index.
     */
    public static final int STAB_ATTACK = 0;

    /**
     * The slash attack index.
     */
    public static final int SLASH_ATTACK = 1;

    /**
     * The crush attack index.
     */
    public static final int CRUSH_ATTACK = 2;

    /**
     * The magic attack index.
     */
    public static final int MAGIC_ATTACK = 3;

    /**
     * The ranged attack index.
     */
    public static final int RANGED_ATTACK = 4;

    /**
     * The stab defence index.
     */
    public static final int STAB_DEFENCE = 5;

    /**
     * The slash defence index.
     */
    public static final int SLASH_DEFENCE = 6;

    /**
     * The crush defence index.
     */
    public static final int CRUSH_DEFENCE = 7;

    /**
     * The magic defence index.
     */
    public static final int MAGIC_DEFENCE = 8;

    /**
     * The ranged defence index.
     */
    public static final int RANGED_DEFENCE = 9;

    /**
     * The strength index.
     */
    public static final int STRENGTH = 10;

    /**
     * The prayer index.
     */
    public static final int PRAYER = 11;

    /**
     * A list of bonus names.
     */
    public static final ImmutableList<String> BONUS_NAMES = ImmutableList
            .of("Stab", "Slash", "Crush", "Magic", "Range", "Stab", "Slash", "Crush", "Magic", "Range", "Strength",
                    "Prayer");

    /**
     * The size.
     */
    public static final int SIZE = 14;

    /**
     * An error message.
     */
    private static final String ERROR_MSG = "Use { set(index, Item) or add(Item) or remove(Item) } instead";

    /**
     * The equipment item display.
     */
    public static final int EQUIPMENT_DISPLAY_ID = 1688;

    /**
     * The player.
     */
    private final Player player;

    /**
     * The inventory.
     */
    private final Inventory inventory;

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
        super(SIZE, StackPolicy.STANDARD);
        this.player = player;
        inventory = player.getInventory();

        addListener(new EquipmentListener());
        addListener(new ItemWeightListener(player));
    }

    @Override
    public boolean add(Item item) {
        int index = item.getEquipDef().getIndex();
        Optional<Integer> currentId = getIdForIndex(index).filter(id -> id == item.getId());

        if (item.getItemDef().isStackable() && currentId.isPresent()) {
            int oldAmount = computeAmountForIndex(index);
            int newAmount = oldAmount + item.getAmount();

            set(index, item.withAmount(newAmount));
        } else {
            set(index, item);
        }
        return true;
    }

    @Override
    public boolean remove(Item item) {
        int index = item.getEquipDef().getIndex();
        Optional<Integer> currentId = getIdForIndex(index).filter(id -> id == item.getId());

        if (!currentId.isPresent()) {
            return false;
        }

        if (item.getItemDef().isStackable()) {
            int oldAmount = computeAmountForIndex(index);
            int newAmount = oldAmount - item.getAmount();

            set(index, newAmount > 0 ? item.withAmount(newAmount) : null);
        } else {
            set(index, null);
        }
        return true;
    }

    /**
     * @deprecated This always throws an exception. Use {@code set(int, Item)} or {@code add(Item)}
     * instead.
     */
    @Deprecated
    @Override
    public boolean add(Item item, int preferredIndex) {
        throw new UnsupportedOperationException(ERROR_MSG);
    }

    /**
     * @deprecated This always throws an exception. Use {@code set(int, Item)} or {@code remove(Item)}
     *  instead.
     */
    @Deprecated
    @Override
    public boolean remove(Item item, int preferredIndex) {
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
        int toIndex = equipDef.getIndex();

        // Check equipment requirements.
        Optional<Requirement> didNotMeet = equipDef.getFailedRequirement(player);
        if(didNotMeet.isPresent()) {
            didNotMeet.get().sendFailureMessage(player);
            return false;
        }

        // Unequip something if we have to.
        Optional<Integer> unequipIndex = Optional.empty();
        if (toIndex == WEAPON) {
            // Equipping 2h weapon, so unequip shield.
            unequipIndex = equipDef.isTwoHanded() ? Optional.of(SHIELD) : Optional.empty();
        } else if (toIndex == Equipment.SHIELD) {
            // Equipping shield, so unequip 2h weapon.
            boolean weaponTwoHanded = getIdForIndex(WEAPON).
                    flatMap(EquipmentDefinition.ALL::get).
                    map(EquipmentDefinition::isTwoHanded).
                    orElse(false);
            unequipIndex = weaponTwoHanded ? Optional.of(WEAPON) : Optional.empty();
        }

        if (unequipIndex.isPresent()) {
            int remaining = inventory.computeRemainingSize();
            if (remaining == 0 && allOccupied(unequipIndex.get(), toIndex)) {
                player.sendMessage("You do not have enough space in your inventory.");
                return false;
            }
        }

        // Equip item.
        inventory.set(inventoryIndex, null);
        unequip(toIndex);
        unequipIndex.ifPresent(this::unequip);
        set(toIndex, inventoryItem);

        flagAppearance(toIndex);
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
        Inventory inventory = player.getInventory();
        if (inventory.add(equipmentItem)) {
            set(equipmentIndex, null);
            flagAppearance(equipmentIndex);
            return true;
        }
        player.sendMessage("You do not have enough space in your inventory.");
        return false;
    }

    /**
     * Flags the appearance block if required.
     *
     * @param index The index we're flagging for.
     */
    private void flagAppearance(int index) {
        if (index != RING && index != AMMUNITION) {
            player.getUpdateFlags().flag(UpdateFlag.APPEARANCE);
        }
    }

    /**
     * Updates bonuses for two potential items.
     *
     * @param oldItem The old item.
     * @param newItem The new item.
     */
    private void updateBonus(Optional<Item> oldItem, Optional<Item> newItem) {
        Optional<Integer> oldId = oldItem.map(Item::getId);
        Optional<Integer> newId = newItem.map(Item::getId);
        if (oldId.equals(newId)) {
            return;
        }

        IntStream indexes = IntStream.range(0, bonuses.length);

        oldItem.map(Item::getEquipDef).
                map(EquipmentDefinition::getBonuses).
                ifPresent(it -> indexes.forEach(index -> bonuses[index] -= it.get(index)));

        newItem.map(Item::getEquipDef).
                map(EquipmentDefinition::getBonuses).
                ifPresent(it -> indexes.forEach(index -> bonuses[index] += it.get(index)));
    }

    /**
     * Writes the bonuses to the equipment interface.
     */
    private void writeBonuses() {
        StringBuilder sb = new StringBuilder();

        for (int index = 0; index < bonuses.length; index++) {
            // TODO check if there's something in the protocol for this.
            String text = sb.append(BONUS_NAMES.get(index)).
                    append(": ").
                    append(bonuses[index] >= 0 ? "+" : "").
                    append(bonuses[index]).toString();
            int line = 1675 + index + (index == 10 || index == 11 ? 1 : 0);

            player.queue(new WidgetTextMessageWriter(text, line));
            sb.setLength(0);
        }
    }

    /**
     * @return An array of equipment bonuses.
     */
    public int[] getBonuses() {
        return bonuses;
    }
}
