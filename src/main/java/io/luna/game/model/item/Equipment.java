package io.luna.game.model.item;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.def.EquipmentDefinition.EquipmentRequirement;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.Skill;
import io.luna.game.model.mobile.SkillSet;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.msg.out.GameChatboxMessageWriter;
import io.luna.net.msg.out.WidgetTextMessageWriter;
import io.luna.util.StringUtils;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;

/**
 * An {@link ItemContainer} implementation that manages equipment for a {@link Player}.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class Equipment extends ItemContainer {

    /**
     * An {@link ItemContainerAdapter} implementation that listens for changes to equipment.
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
            throw new IllegalStateException(EXCEPTION_MESSAGE);
        }

        @Override
        public void itemUpdated(ItemContainer container, Optional<Item> oldItem, Optional<Item> newItem, int index) {
            sendItemsToWidget(container);
            updateBonus(oldItem, newItem);
            writeBonuses();
        }

        @Override
        public void bulkItemsUpdated(ItemContainer container) {
            sendItemsToWidget(container);
            updateAllBonuses();
            writeBonuses();
        }
    }

    /**
     * The head equipment index identifier.
     */
    public static final int HEAD = 0;

    /**
     * The cape equipment index identifier.
     */
    public static final int CAPE = 1;

    /**
     * The amulet equipment index identifier.
     */
    public static final int AMULET = 2;

    /**
     * The weapon equipment index identifier.
     */
    public static final int WEAPON = 3;

    /**
     * The chest equipment index identifier.
     */
    public static final int CHEST = 4;

    /**
     * The shield equipment index identifier.
     */
    public static final int SHIELD = 5;

    /**
     * The legs equipment index identifier.
     */
    public static final int LEGS = 7;

    /**
     * The hands equipment index identifier.
     */
    public static final int HANDS = 9;

    /**
     * The feet equipment index identifier.
     */
    public static final int FEET = 10;

    /**
     * The ring equipment index identifier.
     */
    public static final int RING = 12;

    /**
     * The ammunition equipment index identifier.
     */
    public static final int AMMUNITION = 13;

    /**
     * The attack stab bonus index identifier.
     */
    public static final int STAB_ATTACK = 0;

    /**
     * The attack slash bonus index identifier.
     */
    public static final int SLASH_ATTACK = 1;

    /**
     * The attack crush bonus index identifier.
     */
    public static final int CRUSH_ATTACK = 2;

    /**
     * The attack magic bonus index identifier.
     */
    public static final int MAGIC_ATTACK = 3;

    /**
     * The attack ranged bonus index identifier.
     */
    public static final int RANGED_ATTACK = 4;

    /**
     * The defence stab bonus index identifier.
     */
    public static final int STAB_DEFENCE = 5;

    /**
     * The defence slash bonus index identifier.
     */
    public static final int SLASH_DEFENCE = 6;

    /**
     * The defence crush bonus index identifier.
     */
    public static final int CRUSH_DEFENCE = 7;

    /**
     * The defence magic bonus index identifier.
     */
    public static final int MAGIC_DEFENCE = 8;

    /**
     * The defence ranged bonus index identifier.
     */
    public static final int RANGED_DEFENCE = 9;

    /**
     * The strength bonus index identifier.
     */
    public static final int STRENGTH = 10;

    /**
     * The prayer bonus index identifier.
     */
    public static final int PRAYER = 11;

    /**
     * The names of all of the bonuses.
     */
    public static final ImmutableList<String> BONUS_NAMES = ImmutableList
        .of("Stab", "Slash", "Crush", "Magic", "Range", "Stab", "Slash", "Crush", "Magic", "Range", "Strength", "Prayer");

    /**
     * The size of all equipment instances.
     */
    public static final int SIZE = 14;

    /**
     * The error message printed when certain functions from the superclass are utilized.
     */
    private static final String EXCEPTION_MESSAGE = "Please use { equipment.set(index, Item) } instead";

    /**
     * An {@link ImmutableSet} containing equipment indexes that don't require appearance updates.
     */
    private static final ImmutableSet<Integer> NO_APPEARANCE = ImmutableSet.of(RING, AMMUNITION);

    /**
     * The equipment item display widget identifier.
     */
    public static final int EQUIPMENT_DISPLAY_ID = 1688;

    /**
     * The {@link Player} this instance is dedicated to.
     */
    private final Player player;

    /**
     * The bonuses of all the equipment in this container.
     */
    private final int[] bonuses = new int[12];

    /**
     * Creates a new {@link Equipment}.
     *
     * @param player The {@link Player} this instance is dedicated to.
     */
    public Equipment(Player player) {
        super(SIZE, StackPolicy.STANDARD);
        this.player = player;

        addListener(new EquipmentListener());
        addListener(new ItemWeightListener(player));
    }

    @Override
    public boolean add(Item item, int preferredIndex) {
        throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
    }

    @Override
    public boolean remove(Item item, int preferredIndex) {
        throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
    }

    /**
     * Equips an {@link Item} from the underlying player's {@link Inventory}.
     *
     * @param inventoryIndex The {@code Inventory} index to equip the {@code Item} from.
     * @return {@code true} if the item was equipped, {@code false} otherwise.
     */
    public boolean equip(int inventoryIndex) {
        Inventory inventory = player.getInventory();
        Item equipItem = inventory.get(inventoryIndex);

        if (equipItem == null) { // Item doesn't exist.
            return false;
        }

        EquipmentDefinition equipmentDef = equipItem.getEquipmentDef();
        int toIndex = equipmentDef.getIndex();
        if (!satisfiesRequirements(equipItem)) { // Check equipment requirements.
            return false;
        }

        OptionalInt unequipIndex = OptionalInt.empty();
        if (toIndex == WEAPON) { // If we're equipping a 2h sword, unequip shield.
            unequipIndex = equipmentDef.isTwoHanded() ? OptionalInt.of(SHIELD) : OptionalInt.empty();
        } else if (toIndex == Equipment.SHIELD) { // If we're equipping a shield while wearing a 2h sword, unequip sword.
            boolean weaponTwoHanded = computeIdForIndex(WEAPON).
                map(EquipmentDefinition::getDefinition).
                map(EquipmentDefinition::isTwoHanded).
                orElse(false);
            unequipIndex = weaponTwoHanded ? OptionalInt.of(WEAPON) : OptionalInt.empty();
        }

        if (unequipIndex.isPresent()) { // Do we have enough inventory space for potential unequipping?
            int remaining = inventory.computeRemainingSize();
            if (remaining == 0 && allIndexesOccupied(unequipIndex.getAsInt(), toIndex)) {
                player.queue(new GameChatboxMessageWriter("You do not have enough space in your inventory."));
                return false;
            }
        }

        inventory.set(inventoryIndex, null);
        unequip(toIndex);
        unequipIndex.ifPresent(this::unequip);
        set(toIndex, equipItem);

        appearanceForIndex(toIndex);
        return true;
    }

    /**
     * Unequips an {@link Item} from the underlying player's {@code Equipment}.
     *
     * @param equipmentIndex The {@code Equipment} index to unequip the {@code Item} from.
     * @return {@code true} if the item was unequipped, {@code false} otherwise.
     */
    public boolean unequip(int equipmentIndex) {
        Item unequip = get(equipmentIndex);

        if (unequip == null) { // Item doesn't exist.
            return false;
        }

        Inventory inventory = player.getInventory();
        if (inventory.add(unequip)) {
            set(equipmentIndex, null);
            appearanceForIndex(equipmentIndex);
            return true;
        }
        player.queue(new GameChatboxMessageWriter("You do not have enough space in your inventory."));
        return false;
    }

    /**
     * Flags the {@code APPEARANCE} update block, only if the equipment piece on {@code equipmentIndex} requires an
     * appearance update.
     */
    private void appearanceForIndex(int equipmentIndex) {
        if (!NO_APPEARANCE.contains(equipmentIndex)) {
            player.getUpdateFlags().flag(UpdateFlag.APPEARANCE);
        }
    }

    /**
     * Determines if the {@link SkillSet} of the player satisfies the equipment requirements for {@code item}. Sends the
     * player a message indicating failure if the requirements are not satisfied.
     *
     * @param item The item to determine this for.
     * @return {@code true} if the requirements are satisfied, {@code false} otherwise.
     */
    private boolean satisfiesRequirements(Item item) {
        Optional<EquipmentRequirement> unsatisfied = item.getEquipmentDef().
            getRequirements().
            stream().
            filter(it -> !it.satisfies(player.getSkills())).
            findFirst();

        if (unsatisfied.isPresent()) {
            EquipmentRequirement requirement = unsatisfied.get();

            String name = StringUtils.appendIndefiniteArticle(Skill.getName(requirement.getId()));
            int level = requirement.getLevel();
            player.queue(new GameChatboxMessageWriter("You need " + name + " level of " + level + " to equip this."));
            return false;
        }
        return true;
    }

    /**
     * Updates the bonuses array for single equipment index.
     */
    private void updateBonus(Optional<Item> oldItem, Optional<Item> newItem) {
        Optional<Integer> oldId = oldItem.map(Item::getId);
        Optional<Integer> newId = newItem.map(Item::getId);
        if (oldId.equals(newId)) {
            return;
        }

        IntStream indexes = IntStream.range(0, bonuses.length);
        applyBonuses(oldItem).
            ifPresent(it -> indexes.forEach(index -> bonuses[index] -= it.get(index)));
        applyBonuses(newItem).
            ifPresent(it -> indexes.forEach(index -> bonuses[index] += it.get(index)));
    }

    /**
     * Takes an {@code Optional<Item>} and returns a {@code ImmutableList<Integer>} from it. Used under-the-hood to reduce
     * boilerplate.
     */
    private Optional<ImmutableList<Integer>> applyBonuses(Optional<Item> item) {
        return item.map(Item::getEquipmentDef).map(EquipmentDefinition::getBonuses);
    }

    /**
     * Updates the bonuses array for all of the equipment indexes.
     */
    private void updateAllBonuses() {
        Arrays.fill(bonuses, 0);
        stream().filter(Objects::nonNull).
            forEach(it -> updateBonus(Optional.empty(), Optional.of(it)));
    }

    /**
     * Writes a specific the bonus value on the equipment interface.
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
     * Forces a refresh of {@code Equipment} items to the {@code EQUIPMENT_DISPLAY_ID} widget.
     */
    private void forceRefresh() {
        player.queue(constructRefresh(EQUIPMENT_DISPLAY_ID));
    }

    /**
     * @return The bonuses of all the equipment in this container.
     */
    public int[] getBonuses() {
        return bonuses;
    }
}
