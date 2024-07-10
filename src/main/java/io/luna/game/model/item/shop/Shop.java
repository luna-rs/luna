package io.luna.game.model.item.shop;

import com.google.common.base.Preconditions;
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
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;

/**
 * A model representing a single shop where items can be bought and sold.
 *
 * @author lare96
 * @author natis1
 */
public final class Shop {

    /**
     * By what amount does an uncommon item decrease in value when overstocked by 1.
     */
    private static final double SP_RARE_PRICE_CHANGE = 0.02;

    /**
     * By what amount does a common item change in value when over/understocked by 1.
     */
    private static final double SP_COMM_PRICE_CHANGE = 0.001;

    /**
     * By what amount does an item that doesn't belong to a shop (IE sold to a general store) change in value
     * when over/understocked by 1.
     */
    private static final double GEN_PRICE_CHANGE = 0.03;

    /**
     * How many items must a store stock by default for it to be considered a "common" item that uses the much lower
     * price change value.
     */
    private static final int SP_COMM_THRESHOLD = 20;

    /**
     * At what stock does a general store sell an item that doesn't belong at its regular item price.
     */
    private static final int GEN_DEFAULT_STOCK = 10;

    /**
     * What is the minimum price of an item at a specialized shop?
     * This is also the minimum sell price for the player, regardless of what GEN_MIN_PRICE says.
     */
    private static final double SP_MIN_PRICE = 0.1;

    /**
     * What is the minimum price that general stores can sell items that they don't stock to the player at?
     */
    private static final double GEN_MIN_PRICE = 0.3;

    /**
     * What is the maximum price of an item, anywhere, buying or selling, relative to its base price?
     */
    private static final double MAX_PRICE = 1.3;

    /**
     * What proportion of base price do items sell for when the stock of a store matches its default stock?
     */
    private static final double SP_STARTING_SELL_PRICE = 0.55;

    /**
     * What proportion of base price do general items start at? (Uses the price when the general store is fully stocked
     * which as far as RS2 is concerned, happens at 10 items by default.). To make it user friendly and easier to edit
     * the variable is worded this way rather than just directly set at 0.10.
     */
    private static final double GEN_STARTING_SELL_PRICE = 0.40 - (GEN_DEFAULT_STOCK * GEN_PRICE_CHANGE);

    /**
     * For transactions with more than one item purchased or sold at a time, some slight rounding error, which can be
     * no greater than the amount of items purchased minus 1, may occur because it doesn't calculate each item bought
     * individually. This means that 2 items sold for 0.7 coins each will yield 1 coin, instead of the 0 coins you
     * would get in vanilla behavior.
     * <p>
     * This behavior can be changed, resulting in a much less efficient price calculating formula, but which ultimately
     * is slightly more authentic (but perhaps less desirable) to the original game.
     */
    private static final boolean PERFECT_PRECISION_TRANSACTIONS = false;

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
     * A set of indexes that need restocking.
     */
    private final Set<Integer> needsRestock = new HashSet<>();

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
        Item item = player.getInventory().get(index);
        if (item != null && computeCanSell(item)) {
            int value = computeSellValue(item, 1);
            String itemName = item.getItemDef().getName();
            String currencyName = currency.computeName(value);

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
        Item item = container.get(index);
        if (item != null && item.getAmount() > 0) {
            String itemName = item.getItemDef().getName();
            int value = computeBuyValue(item, index, 1);
            String currencyName = currency.computeName(value);

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
        Inventory inventory = player.getInventory();
        Item shopItem = container.get(index);

        // Invalid index or nothing left in stock.
        if (shopItem == null || shopItem.getAmount() <= 0) {
            player.sendMessage("The shop has run out of stock.");
            return false;
        }
        ItemDefinition itemDef = shopItem.getItemDef();

        // Adjust the buy amount if it's greater than the amount in stock.
        if (buyAmount > shopItem.getAmount()) {
            buyAmount = shopItem.getAmount();
        }

        // Adjust buy amount if it's greater than available inventory space.
        int spacesNeeded = inventory.computeSpaceFor(shopItem.withAmount(buyAmount));
        int spacesAvailable = inventory.computeRemainingSize();
        if (spacesNeeded > spacesAvailable) {
            if (itemDef.isStackable() || spacesAvailable == 0) {
                inventory.fireCapacityExceededEvent();
                return false;
            }
            buyAmount = spacesAvailable;
        }

        // Determine if player has enough currency.
        int hasValue = inventory.computeAmountForId(currency.getId());
        int totalValue = computeBuyValue(shopItem, index, buyAmount);
        if (hasValue < totalValue) {
            // They don't, buy as many as they can afford.
            while (buyAmount > 0 && hasValue < totalValue) {
                buyAmount--;
                totalValue = computeBuyValue(shopItem, index, buyAmount);
            }
            player.sendMessage("You do not have enough " + currency.getPluralName() + " to buy this item.");

            if (buyAmount == 0) {
                // They can't even afford one!
                return false;
            }
        }

        // Buy the item.
        Item buyItem = shopItem.withAmount(buyAmount);
        Item currencyItem = new Item(currency.getId(), totalValue);
        if (inventory.remove(currencyItem) && inventory.add(buyItem)) {
            if (amountMap[index] == OptionalInt.empty()) {
                // Item was never originally in shop, remove it.
                return container.remove(buyItem);
            } else {
                // Decrement item on shop window.
                container.set(index, shopItem.addAmount(-buyAmount));
                return true;
            }
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
        Inventory inventory = player.getInventory();
        Item inventoryItem = inventory.get(index);

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
        Item sellItem = inventoryItem.withAmount(sellAmount);
        boolean exceedsCapacity = !container.hasSpaceFor(sellItem);
        if (exceedsCapacity) {
            player.sendMessage("This shop is currently full.");
            return false;
        }

        // Ensure that the currency can fit into the inventory.
        int totalValue = computeSellValue(sellItem, sellAmount);
        Item currencyItem = new Item(currency.getId(), totalValue);
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
     * Computes the buy value of {@code item}.
     * <p>
     * This is based on extensive black-box testing of modern OSRS's shops, and assuming that none of the code for shop prices
     * has changed since RS2. The principal difference between this function and most private servers is that this one
     * accounts for the different shop formulas used with items that the store normally stocks
     * (for example: selling a staff to Zaff, or a security book to a general store).
     *
     * @param item The item.
     * @param index The index of the item in the shop.
     * @param amountBought The amount of the item to be purchased.
     * @return The buy value.
     */
    private int computeBuyValue(Item item, int index, int amountBought) {
        Preconditions.checkArgument(amountBought >= 0);

        int totalMoney = 0;
        int value = item.getItemDef().getValue();
        int amountStocked = container.computeAmountForIndex(index);
        int itemsToReachMaxPrice, itemsToReachMinPrice, expectedAmount;
        double maxPrice, minPrice, priceChange;
        expectedAmount = amountMap[index].orElse(GEN_DEFAULT_STOCK);
        /*
          If an item is naturally sold by the shop it will have a natural expected stock.

          These items are sold by the shop at much lower price than normal:
          100% value - 2% per extra item stocked, minimum of 10%, maximum 130%???

          whereas items that are not naturally sold are sold at a price of:
          130% - 3% per item stocked, minimum of 40%

          These values seem to be rounded to the nearest coin.
         */
        if (amountMap[index].isPresent()) {
            maxPrice = MAX_PRICE;
            minPrice = SP_MIN_PRICE;
            priceChange = (expectedAmount >= SP_COMM_THRESHOLD) ? SP_COMM_PRICE_CHANGE : SP_RARE_PRICE_CHANGE;
            itemsToReachMaxPrice = (int) ((maxPrice - 1) / priceChange);
            // This needs to be 1 less than the actual amount rounded up
            // which can be efficiently done by subtracting a small epsilon and then flooring.
            // Otherwise the shop will unintentionally give you a 2% discount if you try
            // buying an item while the shop is overstocked by exactly 44 items.s
            itemsToReachMinPrice = (int) (((1 - minPrice) / priceChange) - 0.000001);
        } else {
            maxPrice = MAX_PRICE;
            minPrice = GEN_MIN_PRICE;
            priceChange = GEN_PRICE_CHANGE;
            itemsToReachMaxPrice = (int) ((maxPrice - 1) / priceChange);
            itemsToReachMinPrice = (int) (((1 - minPrice) / priceChange) - 0.000001);
        }

        // Assume that all items bought were bought at normal price, without any offset.
        double valueMod = amountBought;

        /*
         Excluding the boundaries (max priced and minimum priced items),
         the problem at this point boils down to something similar to the common puzzle of adding the first 100 integers.

         http://mathcentral.uregina.ca/QQ/database/QQ.02.06/jo1.html

         Thus we can implement that solution but for the item value.
        */

        // First, find the number of items purchased at the maximum possible price.
        // We check if any items are bought at maximum price
        int maxPriceItems = ((expectedAmount - amountStocked + amountBought) * priceChange >= (maxPrice - 1.0))
                // and then set the number bought at that price
                ? (expectedAmount - itemsToReachMaxPrice - amountStocked + amountBought) : 0;
        // This formula above only applies if you are not at the max price from the start. if you are, then all
        // items are bought at maximum price.

        if (maxPriceItems > amountBought) {
            maxPriceItems = amountBought;
        }

        // The number of items bought at minimum price.
        int minPriceItems = ((expectedAmount - amountStocked) * priceChange <= (minPrice - 1.0))
                ? (amountStocked - expectedAmount - itemsToReachMinPrice) : 0;
        if (minPriceItems > amountBought) {
            minPriceItems = amountBought;
        }
        int startingIndex = minPriceItems;
        int endingIndex = amountBought - maxPriceItems - 1;
        int netIndex = endingIndex - startingIndex + 1;

        if (PERFECT_PRECISION_TRANSACTIONS) {
            for (int i = 0; i < minPriceItems; i++) {
                totalMoney += (int) (minPrice * value);
            }
            for (int i = 0; i < maxPriceItems; i++) {
                totalMoney += (int) (maxPrice * value);
            }
            // Now iterate over each item
            for (int i = startingIndex; i <= endingIndex; i++) {
                totalMoney += (int) (((expectedAmount - amountStocked + i) * priceChange + 1.0) * value);
            }
        } else {

            // Add all the price deltas in maximum and minimum priced items
            valueMod += minPriceItems * (minPrice - 1.0) + maxPriceItems * (maxPrice - 1.0);
            // Now, we can make the assumption that price is not clamped for the remaining items. This allows us to use
            // gauss's technique for finding the total value mod.

            double startingPriceMod = (expectedAmount - amountStocked + startingIndex) * priceChange;
            double endingPriceMod = (expectedAmount - amountStocked + endingIndex) * priceChange;
            double netPriceMod = (endingPriceMod + startingPriceMod);
            valueMod += netPriceMod * (double) (netIndex / 2) + (netIndex % 2 * 0.5 * netPriceMod);
            // round and return
            totalMoney = (int) ((value * valueMod) + 0.5);
        }
        if (totalMoney < amountBought) {
            totalMoney = amountBought;
        }
        return totalMoney;
    }

    /**
     * Computes the sell value of {@code item}.
     * <p>
     * This is based on extensive black-box testing of modern OSRS's shops, and assuming that none of the code for
     * shop sell prices has changed too much.
     * <p>
     * In general stores sell values scale with every item sold, starting at 40% of base value,
     * and decreasing by 3% for every item that the shop already has.
     * <p>
     * In specialty stores. Sell values start at 55% of base value, and decrease by 2% for every item the shop
     * has overstocked.
     *
     * @param item The item.
     * @param amountSold The amount of item sold to the shop.
     * @return The sell value.
     */
    private int computeSellValue(Item item, int amountSold) {
        Preconditions.checkArgument(amountSold >= 0);

        int totalMoney = 0;
        int value = item.getItemDef().getValue();
        int itemsToReachMaxPrice, expectedAmount;
        double maxPrice, minPrice, priceChange, startingPrice;
        int storeIndex = container.computeIndexForId(item.getId()).orElse(-1);
        expectedAmount = (storeIndex != -1) ? amountMap[storeIndex].orElse(GEN_DEFAULT_STOCK) : GEN_DEFAULT_STOCK;
        int amountStocked = (storeIndex != -1) ? container.computeAmountForIndex(storeIndex) : 0;

        if (storeIndex != -1 && amountMap[storeIndex].isPresent()) {
            startingPrice = SP_STARTING_SELL_PRICE;
            maxPrice = MAX_PRICE;
            minPrice = SP_MIN_PRICE;
            priceChange = (expectedAmount >= SP_COMM_THRESHOLD) ? SP_COMM_PRICE_CHANGE : SP_RARE_PRICE_CHANGE;
            itemsToReachMaxPrice = (int) ((maxPrice - 1) / priceChange);
        } else {
            startingPrice = GEN_STARTING_SELL_PRICE;
            maxPrice = MAX_PRICE;
            minPrice = SP_MIN_PRICE;
            priceChange = GEN_PRICE_CHANGE;
            itemsToReachMaxPrice = (int) ((maxPrice - 1) / priceChange);
        }

        double valueMod = amountSold * startingPrice;

        // First, find the number of items sold at the maximum possible price.
        int maxPriceItems = ((expectedAmount - amountStocked) * priceChange >= (maxPrice - startingPrice))
                // and then set the number sold at that price
                ? (expectedAmount - itemsToReachMaxPrice - amountStocked) : 0;
        if (maxPriceItems > amountSold) {
            maxPriceItems = amountSold;
        }

        // The number of items bought at minimum price.
        int minPriceItems = ((expectedAmount - amountStocked - amountSold) * priceChange <= (minPrice - startingPrice))
                ? (amountSold + amountStocked - expectedAmount) : 0;
        if (minPriceItems > amountSold) {
            minPriceItems = amountSold;
        }
        int startingIndex = maxPriceItems;
        int endingIndex = amountSold - minPriceItems - 1;
        int netIndex = endingIndex - startingIndex + 1;

        // Add all the price deltas in maximum and minimum priced items
        // Even though this is less code, it takes about an order of magnitude more time to run.
        if (PERFECT_PRECISION_TRANSACTIONS) {
            for (int i = 0; i < minPriceItems; i++) {
                totalMoney += (int) (minPrice * value);
            }
            for (int i = 0; i < maxPriceItems; i++) {
                totalMoney += (int) (maxPrice * value);
            }
            // Now iterate over each item
            for (int i = startingIndex; i <= endingIndex; i++) {
                totalMoney += (int) (((expectedAmount - amountStocked - i) * priceChange + startingPrice) * value);
            }
            return totalMoney;
        } else {
            valueMod += minPriceItems * (minPrice - startingPrice) + maxPriceItems * (maxPrice - startingPrice);

            // Now, we can make the assumption that price is not clamped for the remaining items. This allows us to use
            // gauss's technique for finding the total value mod.
            double startingPriceMod = (expectedAmount - amountStocked - startingIndex) * priceChange;
            double endingPriceMod = (expectedAmount - amountStocked - endingIndex) * priceChange;
            double netPriceMod = (endingPriceMod + startingPriceMod);
            valueMod += netPriceMod * (double) (netIndex / 2) + (netIndex % 2 * 0.5 * netPriceMod);
            totalMoney = (int) (value * valueMod);
        }
        return totalMoney;
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
    public void init(List<IndexedItem> shopItems) {
        container.init(shopItems);
    }

    /**
     * Prompts this shop to start restocking. Will only create a new task if a previously running one was cancelled.
     * If a restock task is already running, this does nothing.
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

    /**
     * @return A set of indexes that need restocking.
     */
    public Set<Integer> getNeedsRestock() {
        return needsRestock;
    }
}
