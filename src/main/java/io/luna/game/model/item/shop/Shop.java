package io.luna.game.model.item.shop;

import io.luna.game.model.World;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.item.IndexedItem;
import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.Item;
import io.luna.game.model.item.ItemContainer;
import io.luna.game.model.item.ItemContainer.StackPolicy;
import io.luna.game.model.mob.Player;

import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;

/**
 * A model representing a single shop where items can be bought and sold.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class Shop {

    /**
     * The number formatter. Uses the UK locale.
     */
    private static final NumberFormat FORMAT = NumberFormat.getInstance(Locale.UK);

    /**
     * The world instance.
     */
    private final World world;

    /**
     * The shop name.
     */
    private final String name;

    /**
     * The shop items.
     */
    private final ItemContainer container = new ItemContainer(40, StackPolicy.ALWAYS, 3900);

    /**
     * The restock policy.
     */
    private final RestockPolicy restockPolicy;

    /**
     * The sell policy.
     */
    private final BuyPolicy buyPolicy;

    /**
     * The currency used to buy items.
     */
    private final Currency currency;

    /**
     * The players viewing this shop.
     */
    private final Set<Player> viewing = new HashSet<>();

    /**
     * A dictionary of original item amounts. {@link OptionalInt#empty()} indicates an empty slot.
     */
    private final OptionalInt[] amountMap = new OptionalInt[40];

    /**
     * The restock task.
     */
    private RestockTask restockTask;

    /**
     * Creates a new {@link Shop}.
     *
     * @param name The shop name.
     * @param restockPolicy The restock policy.
     * @param buyPolicy The sell policy.
     * @param currency The currency used to buy items.
     */
    public Shop(World world, String name, RestockPolicy restockPolicy, BuyPolicy buyPolicy, Currency currency) {
        this.world = world;
        this.name = name;
        this.restockPolicy = restockPolicy;
        this.buyPolicy = buyPolicy;
        this.currency = currency;
        restockTask = new RestockTask(this);
        container.setListeners(new ShopListener(this));
    }

    /**
     * Sends the sell value of the inventory item on {@code index} to the Player.
     *
     * @param player The player.
     * @param index The index of the item.
     */
    public void sendSellValue(Player player, int index) {
        var item = player.getInventory().get(index);
        
        if (item != null && computeCanSell(item)) {
            int value = computeSellValue(item, false);
            var itemName = item.getItemDef().getName();
            var currencyName = currency.computeName(value);
            player.sendMessage(itemName + ": shop will buy for " + FORMAT.format(value) + " " + currencyName + ".");
        } else {
            player.sendMessage("You cannot sell that item here.");
        }
    }

    /**
     * Sends the buy value of the shop item on {@code index} to the Player.
     *
     * @param player The player.
     * @param index The index of the item.
     */
    public void sendBuyValue(Player player, int index) {
        var item = container.get(index);
        
        if (item != null && item.getAmount() > 0) {
            int value = computeBuyValue(item, false);
            var itemName = item.getItemDef().getName();
            var currencyName = currency.computeName(value);
            player.sendMessage(itemName + ": currently costs " + FORMAT.format(value) + " " + currencyName + ".");
        } else {
            player.sendMessage("The shop has run out of stock.");
        }
    }

    /**
     * Buy an item from this shop.
     *
     * @param player The player.
     * @param index The item's index.
     * @param buyAmount The amount to buy.
     * @return {@code true} if the item was bought.
     */
    public boolean buy(Player player, int index, int buyAmount) {
        var inventory = player.getInventory();
        var shopItem = container.get(index);

        // Invalid index or nothing left in stock.
        if (shopItem == null || shopItem.getAmount() <= 0) {
            player.sendMessage("The shop has run out of stock.");
            return false;
        }

        // Adjust the buy amount if it's greater than the amount in stock.
        if (buyAmount > shopItem.getAmount()) {
            buyAmount = shopItem.getAmount();
        }

        // Adjust buy amount if it's greater than available inventory space.
        int spacesNeeded = inventory.computeSpaceFor(shopItem.withAmount(buyAmount));
        int spacesAvailable = inventory.computeRemainingSize();
        var itemDef = shopItem.getItemDef();
        
        if (spacesNeeded > spacesAvailable) {
            if (itemDef.isStackable() || spacesAvailable == 0) {
                inventory.fireCapacityExceededEvent();
                return false;
            }
            
            buyAmount = spacesAvailable;
        }

        // Determine if player has enough currency.
        int hasValue = inventory.computeAmountForId(currency.getId());
        int singleValue = computeBuyValue(shopItem, false);
        int totalValue = singleValue * buyAmount;
        
        if (hasValue < totalValue) {
            // They don't, buy as many as they can afford.
            buyAmount = hasValue / singleValue;
            totalValue = singleValue * buyAmount;
            
            player.sendMessage("You do not have enough " + currency.getPluralName() + " to buy this item.");

            if (buyAmount == 0) {
                // They can't even afford one!
                return false;
            }
        }

        // Buy the item.
        var buyItem = shopItem.withAmount(buyAmount);
        var currencyItem = new Item(currency.getId(), totalValue);
        
        if (inventory.remove(currencyItem) && inventory.add(buyItem)) {
            if (amountMap[index] == OptionalInt.empty()) {
                // Item was never originally in shop, remove it.
                return container.remove(buyItem);
            }
    
            // Decrement item on shop window.
            container.set(index, shopItem.changeAmount(-buyAmount));
            return true;
        }
        
        return false;
    }

    /**
     * Sell an item to this shop.
     *
     * @param player The player.
     * @param index The item's index.
     * @param sellAmount The amount to sell.
     * @return {@code true} if the item was sold.
     */
    public boolean sell(Player player, int index, int sellAmount) {
        var inventory = player.getInventory();
        var inventoryItem = inventory.get(index);

        // Invalid index or cannot sell item.
        if (inventoryItem == null || !computeCanSell(inventoryItem)) {
            player.sendMessage("You cannot sell that item here.");
            return false;
        }

        // Adjust the sell amount if it's greater than what's in the inventory.
        int inventoryAmount = inventory.computeAmountForId(inventoryItem.getId());
        
        if (sellAmount > inventoryAmount) {
            sellAmount = inventoryAmount;
        }

        // Check if the shop has the space to hold the item being sold.
        var sellItem = inventoryItem.withAmount(sellAmount);
        var exceedsCapacity = !container.hasSpaceFor(sellItem);
        
        if (exceedsCapacity) {
            player.sendMessage("This shop is currently full.");
            return false;
        }

        // Ensure that the currency can fit into the inventory.
        int totalValue = computeSellValue(sellItem, true);
        var currencyItem = new Item(currency.getId(), totalValue);
        
        if (!inventory.hasSpaceFor(currencyItem)) {
            inventory.fireCapacityExceededEvent();
            return false;
        }

        // Sell the item.
        if (inventory.remove(sellItem) && inventory.add(currencyItem)) {
            return container.add(sellItem);
        }
        
        return false;
    }

    /**
     * Computes the sell value of {@code item}.
     *
     * @param item The item.
     * @param total If the value should be multiplied by the item's amount.
     * @return The sell value.
     */
    private int computeSellValue(Item item, boolean total) {
        int value = (int) Math.floor(item.getItemDef().getValue() / 2D);
        value = Math.max(1, value);
        return total ? value * item.getAmount() : value;
    }

    /**
     * Computes the buy value of {@code item}.
     *
     * @param item The item.
     * @param total If the value should be multiplied by the item's amount.
     * @return The buy value.
     */
    private int computeBuyValue(Item item, boolean total) {
        int value = item.getItemDef().getValue();
        value = Math.max(1, value);
        return total ? value * item.getAmount() : value;
    }

    /**
     * Computes if {@code item} can be sold to this shop.
     *
     * @param item The item.
     * @return {@code true} if this item can be sold.
     */
    private boolean computeCanSell(Item item) {
        if (!Currency.IDENTIFIERS.contains(item.getId()) && item.getItemDef().isTradeable()) {
            switch (buyPolicy) {
                case ALL:
                    // All trading items can be sold.
                    return true;
                case NONE:
                    // No items can be sold.
                    return false;
                case EXISTING:
                    // Only existing items can be sold.
                    return container.computeIndexForId(item.getId()).isPresent();
            }
        }

        // Cannot sell non-trading items.
        return false;
    }

    /**
     * Initializes this shop by setting the backing array of items.
     *
     * @param shopItems The items to include in this shop.
     */
    public void init(IndexedItem[] shopItems) {
        container.init(shopItems);
    }

    /**
     * Schedules a restock task. Will create a new one if needed.
     */
    void restockItems() {
        if (restockPolicy != RestockPolicy.DISABLED) {
            switch (restockTask.getState()) {
                case RUNNING:
                    // Restock task is already running.
                    return;
                case CANCELLED:
                    // Instantiate new restock task.
                    restockTask = new RestockTask(this);
                    break;
            }
            
            world.schedule(restockTask);
        }
    }

    /**
     * @return The shop name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The shop items.
     */
    ItemContainer getContainer() {
        return container;
    }

    /**
     * @return The restock policy.
     */
    public RestockPolicy getRestockPolicy() {
        return restockPolicy;
    }

    /**
     * @return The sell policy.
     */
    public BuyPolicy getBuyPolicy() {
        return buyPolicy;
    }

    /**
     * @return The currency used to buy items.
     */
    public Currency getCurrency() {
        return currency;
    }

    /**
     * @return The players viewing this shop.
     */
    Set<Player> getViewing() {
        return viewing;
    }

    /**
     * @return A dictionary of original item amounts.
     */
    OptionalInt[] getAmountMap() {
        return amountMap;
    }
}