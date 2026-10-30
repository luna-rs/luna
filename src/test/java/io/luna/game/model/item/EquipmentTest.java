package io.luna.game.model.item;

import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.def.EquipmentDefinition.Requirement;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mob.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies inventory transfers when equipping existing item stacks.
 *
 * @author lare96
 */
final class EquipmentTest {

    private Inventory inventory;
    private Equipment equipment;

    @BeforeEach
    void setUp() {
        Player player = mock(Player.class);
        inventory = spy(new Inventory(player));
        inventory.setListeners();
        doAnswer(call -> ((Item) call.getArgument(0)).getItemDef().isStackable())
                .when(inventory).stackable(any(Item.class));
        when(player.getInventory()).thenReturn(inventory);
        equipment = new Equipment(player);
        equipment.setListeners();
    }

    @ParameterizedTest
    @ValueSource(ints = {Equipment.AMMUNITION, Equipment.WEAPON})
    void matchingStackableItemsMergeInTheirEquipmentSlot(int equipmentIndex) {
        equipment.set(equipmentIndex, item(882, 100, true, equipmentIndex));
        inventory.set(7, item(882, 50, true, equipmentIndex));

        assertTrue(equipment.equip(7));

        assertEquals(new Item(882, 150), equipment.get(equipmentIndex));
        assertNull(inventory.get(7));
        assertEquals(0, inventory.size());
    }

    @Test
    void matchingStackCanReachIntegerMaximumExactly() {
        equipment.set(Equipment.AMMUNITION, item(882, Integer.MAX_VALUE - 50, true, Equipment.AMMUNITION));
        inventory.set(7, item(882, 50, true, Equipment.AMMUNITION));

        assertTrue(equipment.equip(7));

        assertEquals(new Item(882, Integer.MAX_VALUE), equipment.get(Equipment.AMMUNITION));
        assertNull(inventory.get(7));
        assertEquals(0, inventory.size());
    }

    @Test
    void overflowRemainsInTheOriginalInventorySlot() {
        equipment.set(Equipment.AMMUNITION, item(882, Integer.MAX_VALUE - 5, true, Equipment.AMMUNITION));
        inventory.set(7, item(882, 10, true, Equipment.AMMUNITION));

        assertTrue(equipment.equip(7));

        assertEquals(new Item(882, Integer.MAX_VALUE), equipment.get(Equipment.AMMUNITION));
        assertEquals(new Item(882, 5), inventory.get(7));
        assertEquals(1, inventory.size());
        assertEquals((long) Integer.MAX_VALUE + 5,
                (long) equipment.get(Equipment.AMMUNITION).getAmount() + inventory.get(7).getAmount());
    }

    @Test
    void maximumInventoryStackPreservesTheCombinedQuantity() {
        equipment.set(Equipment.AMMUNITION, item(882, 100, true, Equipment.AMMUNITION));
        inventory.set(7, item(882, Integer.MAX_VALUE, true, Equipment.AMMUNITION));

        assertTrue(equipment.equip(7));

        assertEquals(new Item(882, Integer.MAX_VALUE), equipment.get(Equipment.AMMUNITION));
        assertEquals(new Item(882, 100), inventory.get(7));
        assertEquals((long) Integer.MAX_VALUE + 100,
                (long) equipment.get(Equipment.AMMUNITION).getAmount() + inventory.get(7).getAmount());
    }

    @Test
    void fullEquipmentStackLeavesInventoryUnchanged() {
        Item equipped = item(882, Integer.MAX_VALUE, true, Equipment.AMMUNITION);
        Item carried = item(882, 10, true, Equipment.AMMUNITION);
        equipment.set(Equipment.AMMUNITION, equipped);
        inventory.set(7, carried);

        assertFalse(equipment.equip(7));

        assertSame(equipped, equipment.get(Equipment.AMMUNITION));
        assertSame(carried, inventory.get(7));
        assertEquals(1, inventory.size());
    }

    @Test
    void matchingStackMergesWithAFullInventory() {
        equipment.set(Equipment.AMMUNITION, item(882, 100, true, Equipment.AMMUNITION));
        Item filler = new Item(995);
        for (int index = 0; index < 28; index++) {
            inventory.set(index, filler);
        }
        inventory.set(7, item(882, 50, true, Equipment.AMMUNITION));

        assertTrue(equipment.equip(7));

        assertEquals(new Item(882, 150), equipment.get(Equipment.AMMUNITION));
        assertNull(inventory.get(7));
        assertEquals(27, inventory.size());
        for (int index = 0; index < 28; index++) {
            if (index != 7) {
                assertSame(filler, inventory.get(index));
            }
        }
    }

    @Test
    void differentStackableItemsStillSwap() {
        Item equipped = item(882, 100, true, Equipment.AMMUNITION);
        Item carried = item(884, 50, true, Equipment.AMMUNITION);
        equipment.set(Equipment.AMMUNITION, equipped);
        inventory.set(7, carried);

        assertTrue(equipment.equip(7));

        assertSame(carried, equipment.get(Equipment.AMMUNITION));
        assertEquals(1, inventory.size());
        assertEquals(equipped, inventory.get(inventory.computeIndexForId(882)));
        assertFalse(inventory.contains(884));
    }

    @Test
    void matchingNonstackableItemsStillSwap() {
        Item equipped = item(1277, 1, false, Equipment.WEAPON);
        Item carried = item(1277, 1, false, Equipment.WEAPON);
        equipment.set(Equipment.WEAPON, equipped);
        inventory.set(7, carried);

        assertTrue(equipment.equip(7));

        assertSame(carried, equipment.get(Equipment.WEAPON));
        assertEquals(1, inventory.size());
        assertSame(equipped, inventory.get(inventory.computeIndexForId(1277)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void matchingDynamicItemsKeepTheirStateAndStillSwap(boolean dynamicInInventory) {
        Item regular = item(882, 100, true, Equipment.AMMUNITION);
        DynamicItem dynamic = spy(new DynamicItem(882));
        doReturn(regular.getItemDef()).when(dynamic).getItemDef();
        doReturn(regular.getEquipDef()).when(dynamic).getEquipDef();
        Item equipped = dynamicInInventory ? regular : dynamic;
        Item carried = dynamicInInventory ? dynamic : regular;
        equipment.set(Equipment.AMMUNITION, equipped);
        inventory.set(7, carried);

        assertTrue(equipment.equip(7));

        assertSame(carried, equipment.get(Equipment.AMMUNITION));
        assertEquals(1, inventory.size());
        assertSame(equipped, inventory.get(inventory.computeIndexForId(882)));
        assertEquals(1, dynamic.getAmount());
        assertEquals(100, regular.getAmount());
    }

    @Test
    void matchingStackStillRequiresEquipmentRequirements() {
        Item equipped = item(882, 100, true, Equipment.AMMUNITION);
        Item carried = item(882, 50, true, Equipment.AMMUNITION);
        Requirement requirement = mock(Requirement.class);
        when(carried.getEquipDef().getFailedRequirement(any())).thenReturn(Optional.of(requirement));
        equipment.set(Equipment.AMMUNITION, equipped);
        inventory.set(7, carried);

        assertFalse(equipment.equip(7));

        assertSame(equipped, equipment.get(Equipment.AMMUNITION));
        assertSame(carried, inventory.get(7));
        verify(requirement).sendFailureMessage(any());
    }

    private Item item(int id, int amount, boolean stackable, int equipmentIndex) {
        ItemDefinition definition = mock(ItemDefinition.class);
        when(definition.isStackable()).thenReturn(stackable);
        EquipmentDefinition equipmentDefinition = mock(EquipmentDefinition.class);
        when(equipmentDefinition.getIndex()).thenReturn(equipmentIndex);
        when(equipmentDefinition.getFailedRequirement(any())).thenReturn(Optional.empty());
        return new DefinedItem(id, amount, definition, equipmentDefinition);
    }

    /**
     * Supplies item definitions locally without mutating the shared definition repositories.
     *
     * @author lare96
     */
    private static final class DefinedItem extends Item {

        private final ItemDefinition definition;
        private final EquipmentDefinition equipmentDefinition;

        private DefinedItem(int id, int amount, ItemDefinition definition, EquipmentDefinition equipmentDefinition) {
            super(id, amount);
            this.definition = definition;
            this.equipmentDefinition = equipmentDefinition;
        }

        @Override
        public ItemDefinition getItemDef() {
            return definition;
        }

        @Override
        public EquipmentDefinition getEquipDef() {
            return equipmentDefinition;
        }

        @Override
        public Item withAmount(int amount) {
            return amount == getAmount() ? this : new DefinedItem(getId(), amount, definition, equipmentDefinition);
        }
    }
}
