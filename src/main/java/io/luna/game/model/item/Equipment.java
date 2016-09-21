package io.luna.game.model.item;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.luna.game.event.impl.EquipmentChangeEvent;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.def.EquipmentDefinition.EquipmentRequirement;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.Skill;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.game.plugin.PluginManager;
import io.luna.net.msg.out.GameChatboxMessageWriter;
import io.luna.net.msg.out.WidgetTextMessageWriter;

import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalInt;
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
            sendItemGroup(items);
            sendEvent(oldItem, newItem, index);
        }

        @Override
        public void onBulkUpdate(ItemContainer items, Optional<Item> oldItem, Optional<Item> newItem, int index) {
            updateBonus(oldItem, newItem);
            sendEvent(oldItem, newItem, index);
        }

        @Override
        public void onBulkUpdateCompleted(ItemContainer items) {
            sendItemGroup(items);
            writeBonuses();
        }

        /**
         * Posts an equipment change event.
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
     * A set of indexes not requiring an appearance update.
     */
    private static final ImmutableSet<Integer> NO_APPEARANCE = ImmutableSet.of(RING, AMMUNITION);

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
        Optional<Integer> currentId = computeIdForIndex(index).filter(id -> id == item.getId());

        if (item.getItemDef().isStackable() && currentId.isPresent()) {
            int oldAmount = computeAmountForIndex(index);
            int newAmount = oldAmount + item.getAmount();

            set(index, item.createWithAmount(newAmount));
        } else {
            set(index, item);
        }
        return true;
    }

    @Override
    public boolean remove(Item item) {
        int index = item.getEquipDef().getIndex();
        Optional<Integer> currentId = computeIdForIndex(index).filter(id -> id == item.getId());

        if (!currentId.isPresent()) {
            return false;
        }

        if (item.getItemDef().isStackable()) {
            int oldAmount = computeAmountForIndex(index);
            int newAmount = oldAmount - item.getAmount();

            set(index, newAmount > 0 ? item.createWithAmount(newAmount) : null);
        } else {
            set(index, null);
        }
        return true;
    }

    /**
     * This always throws an exception. Use {@code set(int, Item)} or {@code add(Item)} instead.
     */
    @Override
    public boolean add(Item item, int preferredIndex) {
        throw new UnsupportedOperationException(ERROR_MSG);
    }

    /**
     * This always throws an exception. Use {@code set(int, Item)} or {@code remove(Item)} instead.
     */
    @Override
    public boolean remove(Item item, int preferredIndex) {
        throw new UnsupportedOperationException(ERROR_MSG);
    }

    /**
     * Equips an item from the inventory. Returns {@code true} if successful.
     */
    public boolean equip(int inventoryIndex) {
        Item inventoryItem = inventory.get(inventoryIndex);

        if (inventoryItem == null) {
            return false;
        }

        EquipmentDefinition equipDef = inventoryItem.getEquipDef();
        int toIndex = equipDef.getIndex();
        if (!canEquip(inventoryItem)) {
            return false;
        }

        OptionalInt unequipIndex = OptionalInt.empty();
        if (toIndex == WEAPON) { /* Equip 2h weapon -> Unequip shield */
            unequipIndex = equipDef.isTwoHanded() ? OptionalInt.of(SHIELD) : OptionalInt.empty();
        } else if (toIndex == Equipment.SHIELD) { /* Equip shield -> Unequip 2h weapon */
            boolean weaponTwoHanded = computeIdForIndex(WEAPON).
                map(EquipmentDefinition::get).
                map(EquipmentDefinition::isTwoHanded).
                orElse(false);
            unequipIndex = weaponTwoHanded ? OptionalInt.of(WEAPON) : OptionalInt.empty();
        }

        if (unequipIndex.isPresent()) {
            int remaining = inventory.computeRemainingSize();
            if (remaining == 0 && allOccupied(unequipIndex.getAsInt(), toIndex)) {
                sendMessage("You do not have enough space in your inventory.");
                return false;
            }
        }

        inventory.set(inventoryIndex, null);
        unequip(toIndex);
        unequipIndex.ifPresent(this::unequip);
        set(toIndex, inventoryItem);

        flagAppearance(toIndex);
        return true;
    }

    /**
     * Unequips an item to the inventory. Returns {@code true} if successful.
     */
    public boolean unequip(int equipmentIndex) {
        Item equipmentItem = get(equipmentIndex);

        if (equipmentItem == null) {
            return false;
        }

        Inventory inventory = player.getInventory();
        if (inventory.add(equipmentItem)) {
            set(equipmentIndex, null);
            flagAppearance(equipmentIndex);
            return true;
        }
        sendMessage("You do not have enough space in your inventory.");
        return false;
    }

    /**
     * Flags the appearance block if required by {@code equipmentIndex}.
     */
    private void flagAppearance(int equipmentIndex) {
        if (!NO_APPEARANCE.contains(equipmentIndex)) {
            player.getUpdateFlags().flag(UpdateFlag.APPEARANCE);
        }
    }

    /**
     * Determines if {@code item} can be equipped.
     */
    private boolean canEquip(Item item) {
        EquipmentDefinition def = item.getEquipDef();

        for (EquipmentRequirement req : def.getRequirements()) {
            Skill skill = player.skill(req.getId());

            if (skill.getLevel() < req.getLevel()) {
                sendMessage("You need a " + skill.name() + " level of " + req.getLevel() + " to equip this.");
                return false;
            }
        }
        return true;
    }

    /**
     * Updates bonuses for two potential items.
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
     * Updates the bonuses for all items.
     */
    private void updateAllBonuses() {
        Arrays.fill(bonuses, 0);
        forEach(it -> updateBonus(Optional.empty(), Optional.of(it)));
    }

    /**
     * Writes the bonuses to the equipment interface.
     */
    private void writeBonuses() {
        StringBuilder sb = new StringBuilder();

        for (int index = 0; index < bonuses.length; index++) {
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
     * Refreshes the equipment display.
     */
    private void forceRefresh() {
        player.queue(constructRefresh(EQUIPMENT_DISPLAY_ID));
    }

    /**
     * Sends a message to the chatbox.
     */
    private void sendMessage(String str) {
        player.queue(new GameChatboxMessageWriter(str));
    }

    /**
     * @return An array of equipment bonuses.
     */
    public int[] getBonuses() {
        return bonuses;
    }
}
