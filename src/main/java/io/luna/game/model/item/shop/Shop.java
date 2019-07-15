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
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;

/**
 * A model representing a single shop where items can be bought and sold.
 *
 * @author lare96 <http://github.com/lare96>
 * @author natis1 <http://github.com/natis1>
 *
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
        Item item = player.getInventory().get(index);
        if (item != null && computeCanSell(item)) {
            int value = computeSellValue(item, false);
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
                container.set(index, shopItem.changeAmount(-buyAmount));
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
        int totalValue = computeSellValue(sellItem, true);
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
     * Computes the sell value of {@code item}.
     *
     * @param item The item.
     * @param total If the value should be multiplied by the item's amount.
     * @return The sell value.
     */
    private int computeSellValue(Item item, boolean total) {
        int stockDiff = 0;

        //Check if the shop already have this item in stock
        if(container.contains(item)) {

            //Find out how many of that item are currently in stock in the shop
            int currentStock = container.computeAmountForId(item.getId());

            //Find out how many of that item are in stock by default in the shop
            var index = container.computeIndexForId(item.getId());
            int defaultStock = index.isPresent() ? amountMap[index.getAsInt()].orElse(0): 0;

            //The price decrement doesn't fall below 75%(10 * 7.5%) of the initial price.
            if((currentStock - defaultStock) > 10) {
                stockDiff = 10;
            }
            else if((currentStock - defaultStock) < -10) {
                stockDiff = -10;
            }
            else {
                stockDiff = currentStock - defaultStock;
            }

        }

        double lowAlch = item.getItemDef().getValue() * 0.4;
        int value = 0;

        //Every overstocked item causes the selling price to decrease by 7.5% of the initial price
        for(int i = 0; i < (total ? item.getAmount() : 1); i++) {

            value += lowAlch - ((stockDiff * 0.075) * lowAlch);

            if(stockDiff < 10)
                stockDiff++;
        }
        return value <= 0 ? 1 : value;
    }

    /**
     * Computes the buy value of {@code item}.
     *
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
        assert amountBought >= 0;

        int value = item.getItemDef().getValue();
        int amountStocked = container.computeAmountForIndex(index);

        // TODO: consider maybe moving these somewhere. I'm not sure where. These are just "magic numbers"

        // How much does an item increase in price relative to its base price when understocked by 1 from a specialized store?
        final double SP_PRICE_CHANGE = 0.02;

        // What is the minimum buy price of an item at a specialized shop?
        final double SP_MIN_PRICE = 0.1;
        // What is the maximum buy price of an item at a specialized shop?
        final double SP_MAX_PRICE = 1.3;

        // How many items need to be understocked to reach max price and how many items need to be overstocked to reach minimum price?
        final int SP_ITEMS_TO_REACH_MAX_PRICE = (int) ((SP_MAX_PRICE - 1) / SP_PRICE_CHANGE); // 15 by default.
        final int SP_ITEMS_TO_REACH_MIN_PRICE = (int) ((1 - SP_MIN_PRICE) / SP_PRICE_CHANGE) - 1; // 44 by default. Should be less than the "actual" value of 45 by default.

        // How much does an item increase in price relative to its base price when "understocked" by 1 from a general store?
        // In this case the "default stock" is treated as 10 even though stock will adjust until there's 0 of the item.
        final double GEN_PRICE_CHANGE = 0.03;

        // What is the minimum buy price of an item at a general store?
        final double GEN_MIN_PRICE = 0.3;
        final int GEN_ITEMS_TO_REACH_MIN_PRICE = (int) ((1 - GEN_MIN_PRICE) / GEN_PRICE_CHANGE); // rounds to 23 by default. Should be less than the "actual" value of 23.33 by default


        /*
          If an item is naturally sold by the shop it will have a natural expected stock.

          These items are sold by the shop at much lower price than normal:
          100% value - 2% per extra item stocked, minimum of 10%, maximum 130%???

          whereas items that are not naturally sold are sold at a price of:
          130% - 3% per item stocked, minimum of 40%

          These values seem to be rounded to the nearest coin.
         */

        if(amountMap[index].isPresent()) {
            int expectedAmount = amountMap[index].orElse(0);
            // Assume that all items bought were bought at normal price, without any offset.
            double valueMod = amountBought;

            // For buying 1 item, this special case is slightly faster and avoids all the math below.
            if(amountBought == 1) {
                valueMod += (expectedAmount - amountStocked) * SP_PRICE_CHANGE;

                // Clamp between 130% and 10%
                if (valueMod > 1.3) {
                    valueMod = 1.3;
                } else if (valueMod < 0.1) {
                    valueMod = 0.1;
                }
                // Calculate true buy value and round it to nearest int.
                return (int) (value * valueMod + 0.5);
            }

            /*
              Excluding the boundaries (max priced and minimum priced items),
              the problem at this point boils down to something similar to the common puzzle of adding the first 100 integers.

              http://mathcentral.uregina.ca/QQ/database/QQ.02.06/jo1.html

              Thus we can implement that solution but for the item value.
             */

            // First, find the number of items purchased at the maximum possible price.
            // We check if any items are bought at maximum price
            int maxPriceItems = ( (expectedAmount - amountStocked + amountBought) * SP_PRICE_CHANGE >= (SP_MAX_PRICE - 1.0))
                    // and then set the number bought at that price
                    ? (expectedAmount - SP_ITEMS_TO_REACH_MAX_PRICE - amountStocked + amountBought) : 0;
            // This formula above only applies if you are not at the max price from the start. if you are, then all
            // items are bought at maximum price.

            if(maxPriceItems > amountBought) {
                maxPriceItems = amountBought;
            }

            // The number of items bought at minimum price.
            int minPriceItems = ((expectedAmount - amountStocked) * SP_PRICE_CHANGE <= (SP_MIN_PRICE - 1.0))
                    ? (amountStocked - expectedAmount - SP_ITEMS_TO_REACH_MIN_PRICE) : 0;
            if(minPriceItems > amountBought) {
                minPriceItems = amountBought;
            }


            // Add all the price deltas in maximum and minimum priced items
            valueMod += minPriceItems * -0.9 + maxPriceItems * 0.3;

            // Now, we can make the assumption that price is not clamped for the remaining items. This allows us to use
            // gauss's technique for finding the total value mod.
            int startingIndex = minPriceItems;
            int endingIndex = amountBought - maxPriceItems - 1;
            int netIndex = endingIndex - startingIndex + 1;
            double startingPriceMod = (expectedAmount - amountStocked + startingIndex) * SP_PRICE_CHANGE;
            double endingPriceMod = (expectedAmount - amountStocked + endingIndex) * SP_PRICE_CHANGE;
            double netPriceMod = (endingPriceMod + startingPriceMod);

            valueMod += netPriceMod * (double)(netIndex / 2) + (netIndex % 2 * 0.5 * netPriceMod);

            // round and return
            return (int) ((value * valueMod) + 0.5);
        } else {
            // We are in a general store and buying an item that is not normally present in a general store, and
            // should use the general store constants instead.

            // For some reason general stores have a "stock" of 10 items.
            int expectedAmount = 10;
            double valueMod = amountBought;
            if(amountBought == 1) {
                valueMod += (expectedAmount - amountStocked) * SP_PRICE_CHANGE;

                // Clamp values less than 30%
                if (valueMod < GEN_MIN_PRICE) {
                    valueMod = GEN_MIN_PRICE;
                }
                return (int) (value * valueMod + 0.5);
            }
            int minPriceItems = ( (expectedAmount - amountStocked) * GEN_PRICE_CHANGE <= (GEN_MIN_PRICE - 1.0))
                    ? (amountStocked - expectedAmount - GEN_ITEMS_TO_REACH_MIN_PRICE) : 0;
            if(minPriceItems > amountBought) {
                minPriceItems = amountBought;
            }
            valueMod += minPriceItems * (GEN_MIN_PRICE - 1.0);

            int startingIndex = minPriceItems;
            int endingIndex = amountBought - 1;
            int netIndex = endingIndex - startingIndex + 1;
            double startingPriceMod = (expectedAmount - amountStocked + startingIndex) * GEN_PRICE_CHANGE;
            double endingPriceMod = (expectedAmount - amountStocked + endingIndex) * GEN_PRICE_CHANGE;
            double netPriceMod = (endingPriceMod + startingPriceMod);

            valueMod += netPriceMod * (double)(netIndex / 2) + (netIndex % 2 * 0.5 * netPriceMod);

            return (int) ((value * valueMod) + 0.5);

        }
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
