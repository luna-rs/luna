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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;

/**
 * A model representing a single in-game shop where {@link Item}s may be bought and sold.
 * <p>
 * A shop is composed of:
 * <ul>
 *     <li>A backing {@link ItemContainer} that holds the current visible stock.</li>
 *     <li>An original stock snapshot ({@code amountMap}) used to determine restock eligibility and pricing.</li>
 *     <li>A {@link RestockPolicy} that controls if/when stock replenishment is triggered.</li>
 *     <li>A {@link BuyPolicy} that controls whether players may sell items to the shop.</li>
 *     <li>A {@link Currency} item id used to pay for purchases and receive proceeds from sales.</li>
 * </ul>
 * <p>
 * Pricing is dynamic and influenced by how overstocked or understocked the shop is relative to its expected stock.
 * The expected stock for a slot is derived from {@code amountMap} when the shop naturally stocks the item, or
 * {@link #GEN_DEFAULT_STOCK} for general-store style items.
 * <p>
 * This implementation also tracks which {@link Player}s are currently viewing the shop so that display updates
 * can be broadcast when stock changes.
 *
 * @author lare96
 * @author natis1
 */
public final class Shop {

    /**
     * Price change per extra unit stocked for uncommon/rare items in specialty shops.
     */
    private static final double SP_RARE_PRICE_CHANGE = 0.02;

    /**
     * Price change per unit stocked delta for common items in specialty shops.
     */
    private static final double SP_COMM_PRICE_CHANGE = 0.001;

    /**
     * Price change per unit stocked delta for items not naturally stocked by a shop (general-store behavior).
     */
    private static final double GEN_PRICE_CHANGE = 0.03;

    /**
     * The expected stock threshold at which an item is treated as "common" and uses the lower price change value.
     */
    private static final int SP_COMM_THRESHOLD = 20;

    /**
     * The expected default stock used for pricing items that are not naturally stocked by a shop.
     */
    private static final int GEN_DEFAULT_STOCK = 10;

    /**
     * The minimum specialty shop buy/sell price multiplier relative to base item value.
     */
    private static final double SP_MIN_PRICE = 0.1;

    /**
     * The minimum general-store buy price multiplier relative to base item value for non-stocked items.
     */
    private static final double GEN_MIN_PRICE = 0.3;

    /**
     * The maximum price multiplier relative to base item value.
     */
    private static final double MAX_PRICE = 1.3;

    /**
     * The starting sell price multiplier for specialty shops when stock matches expected stock.
     */
    private static final double SP_STARTING_SELL_PRICE = 0.55;

    /**
     * The starting sell price multiplier for general-store style items at default stock.
     */
    private static final double GEN_STARTING_SELL_PRICE = 0.40 - (GEN_DEFAULT_STOCK * GEN_PRICE_CHANGE);

    /**
     * Shared number formatter used when displaying price messages to players.
     */
    private static final NumberFormat PRICE_FORMAT = NumberFormat.getInstance(Locale.UK);

    /**
     * The world instance used for scheduling restock tasks.
     */
    private final World world;

    /**
     * The display name of the shop.
     */
    private final String name;

    /**
     * The backing container for shop stock.
     */
    private final ItemContainer items = new ItemContainer(40, StackPolicy.ALWAYS, 3900);

    /**
     * Restock behavior configuration for this shop. {@code null} signifies that this shop will not restock items.
     */
    private final RestockPolicy restockPolicy;

    /**
     * Controls whether (and what) players may sell to this shop.
     */
    private final BuyPolicy buyPolicy;

    /**
     * The currency used by this shop for buying and selling.
     */
    private final Currency currency;

    /**
     * The set of players currently viewing this shop interface.
     */
    private final Set<Player> viewing = new HashSet<>();

    /**
     * Snapshot of original stock amounts for each shop slot.
     * <p>
     * A present value indicates the item is naturally stocked by this shop and may be restocked according to policy.
     * {@link OptionalInt#empty()} indicates the slot was originally empty and should be treated as non-stocked for
     * restocking behavior.
     */
    private final OptionalInt[] amountMap;

    /**
     * The task responsible for replenishing stock over time, depending on the {@link #restockPolicy}.
     */
    private RestockTask restockTask;

    /**
     * The set of slot indexes that have reached the restock threshold and require replenishment.
     */
    private final Set<Integer> restockItems = new HashSet<>();

    /**
     * Creates a new {@link Shop}.
     * <p>
     * The shop container is created immediately and configured with a {@link ShopListener} for stock initialization,
     * restock triggering, and display update broadcasting.
     * <p>
     * The {@code amountMap} is initialized to {@link OptionalInt#empty()} for all slots. The actual snapshot of
     * original stock values is established when the backing {@link ItemContainer} is initialized and the listener
     * receives its {@code onInit} callback.
     *
     * @param world The world instance used for scheduling restock tasks.
     * @param name The shop name displayed to players.
     * @param restockPolicy The restock policy.
     * @param buyPolicy The policy controlling what items may be sold to this shop.
     * @param currency The currency item used for transactions.
     */
    public Shop(World world, String name, RestockPolicy restockPolicy, BuyPolicy buyPolicy, Currency currency) {
        this.world = world;
        this.name = name;
        this.restockPolicy = restockPolicy;
        this.buyPolicy = buyPolicy;
        this.currency = currency;

        amountMap = new OptionalInt[40];
        Arrays.fill(amountMap, OptionalInt.empty());

        restockTask = new RestockTask(this);
        items.setListeners(new ShopListener(this));
    }

    /**
     * Sends a message to {@code player} describing how much this shop would pay for the inventory item located at
     * {@code index}.
     *
     * @param player The player requesting the sell value.
     * @param index The inventory slot index.
     */
    public void sendSellValue(Player player, int index) {
        Item item = player.getInventory().get(index);
        if (item != null && computeCanSell(item)) {
            int value = computeSellValue(item.getId(), 1);
            String itemName = item.getItemDef().getName();
            String currencyName = currency.computeName(value);

            player.sendMessage(itemName + ": shop will buy for " + PRICE_FORMAT.format(value) + " " + currencyName + ".");
        } else {
            player.sendMessage("You cannot sell that item here.");
        }
    }

    /**
     * Sends a message to {@code player} describing how much it currently costs to buy the shop item located at
     * {@code index}.
     * <p>
     * If the item slot is empty or out of stock, the player is informed the shop has run out.
     *
     * @param player The player requesting the buy value.
     * @param index The shop container slot index.
     */
    public void sendBuyValue(Player player, int index) {
        Item item = items.get(index);
        if (item != null && item.getAmount() > 0) {
            String itemName = item.getItemDef().getName();
            int value = computeBuyValue(item, index, 1);
            String currencyName = currency.computeName(value);

            player.sendMessage(itemName + ": currently costs " + PRICE_FORMAT.format(value) + " " + currencyName + ".");
        } else {
            player.sendMessage("The shop has run out of stock.");
        }
    }

    /**
     * Attempts to buy an item from this shop.
     *
     * @param player The player buying items.
     * @param index The shop slot index.
     * @param buyAmount The amount requested.
     * @return {@code true} if at least one item was purchased, otherwise {@code false}.
     */
    public boolean buy(Player player, int index, int buyAmount) {
        Inventory inventory = player.getInventory();
        Item shopItem = items.get(index);

        if (shopItem == null || shopItem.getAmount() < 1) {
            player.sendMessage("The shop has run out of stock.");
            return false;
        }

        if (buyAmount > shopItem.getAmount()) {
            // Buy amount is above store amount, clamp it.
            buyAmount = shopItem.getAmount();
        }

        int spacesNeeded = inventory.computeSpaceFor(shopItem.withAmount(buyAmount));
        int spacesAvailable = inventory.computeRemainingSize();
        if (spacesNeeded > spacesAvailable) {
            if (spacesAvailable == 0) {
                // No free space left.
                inventory.fireCapacityExceededEvent();
                return false;
            }
            // Buy amount needs more inventory space than we have available, clamp it.
            buyAmount = spacesAvailable;
        }

        if (buyAmount < 1) {
            return false;
        }

        int hasValue = inventory.computeAmountForId(currency.getId());
        int totalValue = computeBuyValue(shopItem, index, buyAmount);
        if (hasValue < totalValue) {
            // Total value is below what we have, find out the max we can purchase.
            while (buyAmount > 0 && hasValue < totalValue) {
                buyAmount--;
                totalValue = computeBuyValue(shopItem, index, buyAmount);
            }
            player.sendMessage("You do not have enough " + currency.getPluralName() + " to buy this item.");

            if (buyAmount == 0) {
                // If we don't have enough for anything, stop here.
                return false;
            }
        }

        Item buyItem = shopItem.withAmount(buyAmount);
        Item currencyItem = new Item(currency.getId(), totalValue);

        if (inventory.remove(currencyItem) && inventory.add(buyItem)) {
            if (amountMap[index].isEmpty()) {
                return items.remove(buyItem);
            } else {
                items.set(index, shopItem.addAmount(-buyAmount));
                return true;
            }
        }
        return false;
    }

    /**
     * Attempts to sell an item from a player's inventory into this shop.
     * <p>
     * The sale flow generally performs:
     * <ol>
     *     <li>Validation that the item exists and may be sold under {@link #buyPolicy}.</li>
     *     <li>Clamping the sell amount to the available inventory quantity.</li>
     *     <li>Checking that the shop has capacity to accept the item.</li>
     *     <li>Checking that the resulting currency can fit in the player's inventory.</li>
     *     <li>Removing the sold item and adding currency to the player.</li>
     *     <li>Adding the sold item into the shop container.</li>
     * </ol>
     *
     * @param player The player selling items.
     * @param index The inventory slot index.
     * @param sellAmount The amount requested.
     * @return {@code true} if the sale completed, otherwise {@code false}.
     */
    public boolean sell(Player player, int index, int sellAmount) {
        Inventory inventory = player.getInventory();
        Item inventoryItem = inventory.get(index);

        if (inventoryItem == null || !computeCanSell(inventoryItem)) {
            player.sendMessage("You cannot sell that item here.");
            return false;
        }

        int inventoryAmount = inventory.computeAmountForId(inventoryItem.getId());
        if (sellAmount > inventoryAmount) {
            sellAmount = inventoryAmount;
        }

        Item sellItem = inventoryItem.withAmount(sellAmount);
        if (!items.hasSpaceFor(sellItem)) {
            player.sendMessage("This shop is currently full.");
            return false;
        }

        int totalValue = computeSellValue(sellItem.getId(), sellAmount);
        Item currencyItem = new Item(currency.getId(), totalValue);
        if (!inventory.hasSpaceFor(currencyItem)) {
            inventory.fireCapacityExceededEvent();
            return false;
        }

        if (inventory.remove(sellItem) && inventory.add(currencyItem)) {
            return items.add(sellItem);
        }
        return false;
    }

    /**
     * Computes the total currency cost of purchasing {@code amountBought} units of {@code item} from this shop.
     * <p>
     * Pricing is dynamic and depends on current stock relative to expected stock:
     * <ul>
     *     <li>Items naturally stocked by this shop (slot present in {@code amountMap}) use specialty pricing rules.</li>
     *     <li>Items not naturally stocked use general-store style pricing with {@link #GEN_DEFAULT_STOCK} as expected stock.</li>
     * </ul>
     * <p>
     *
     * @param item The shop item.
     * @param index The slot index in the shop container.
     * @param amountBought The quantity being purchased.
     * @return The total purchase cost in units of {@link #currency}.
     * @throws IllegalArgumentException If {@code amountBought} is negative.
     */
    private int computeBuyValue(Item item, int index, int amountBought) {
        Preconditions.checkArgument(amountBought >= 0);

        int totalMoney = 0;
        int value = item.getItemDef().getValue();
        int amountStocked = items.computeAmountForIndex(index);

        int itemsToReachMaxPrice;
        int itemsToReachMinPrice;
        int expectedAmount;

        double maxPrice;
        double minPrice;
        double priceChange;

        expectedAmount = amountMap[index].orElse(GEN_DEFAULT_STOCK);

        if (amountMap[index].isPresent()) {
            maxPrice = MAX_PRICE;
            minPrice = SP_MIN_PRICE;
            priceChange = (expectedAmount >= SP_COMM_THRESHOLD) ? SP_COMM_PRICE_CHANGE : SP_RARE_PRICE_CHANGE;
            itemsToReachMaxPrice = (int) ((maxPrice - 1) / priceChange);
            itemsToReachMinPrice = (int) (((1 - minPrice) / priceChange) - 0.000001);
        } else {
            maxPrice = MAX_PRICE;
            minPrice = GEN_MIN_PRICE;
            priceChange = GEN_PRICE_CHANGE;
            itemsToReachMaxPrice = (int) ((maxPrice - 1) / priceChange);
            itemsToReachMinPrice = (int) (((1 - minPrice) / priceChange) - 0.000001);
        }

        double valueMod = amountBought;

        int maxPriceItems = ((expectedAmount - amountStocked + amountBought) * priceChange >= (maxPrice - 1.0))
                ? (expectedAmount - itemsToReachMaxPrice - amountStocked + amountBought) : 0;

        if (maxPriceItems > amountBought) {
            maxPriceItems = amountBought;
        }

        int minPriceItems = ((expectedAmount - amountStocked) * priceChange <= (minPrice - 1.0))
                ? (amountStocked - expectedAmount - itemsToReachMinPrice) : 0;

        if (minPriceItems > amountBought) {
            minPriceItems = amountBought;
        }

        int startingIndex = minPriceItems;
        int endingIndex = amountBought - maxPriceItems - 1;
        int netIndex = endingIndex - startingIndex + 1;

        valueMod += minPriceItems * (minPrice - 1.0) + maxPriceItems * (maxPrice - 1.0);

        double startingPriceMod = (expectedAmount - amountStocked + startingIndex) * priceChange;
        double endingPriceMod = (expectedAmount - amountStocked + endingIndex) * priceChange;
        double netPriceMod = (endingPriceMod + startingPriceMod);

        valueMod += netPriceMod * (double) (netIndex / 2) + (netIndex % 2 * 0.5 * netPriceMod);
        totalMoney = (int) ((value * valueMod) + 0.5);

        if (totalMoney < amountBought) {
            totalMoney = amountBought;
        }

        return totalMoney;
    }

    /**
     * Computes the total currency received for selling {@code amountSold} units of an item into this shop.
     * <p>
     * The sell value depends on whether the item is naturally stocked by this shop:
     * <ul>
     *     <li>For specialty-stocked items, the starting multiplier is {@link #SP_STARTING_SELL_PRICE}.</li>
     *     <li>For general/non-stock items, the starting multiplier is derived from {@link #GEN_STARTING_SELL_PRICE}.</li>
     * </ul>
     *
     * @param id The item id being sold.
     * @param amountSold The quantity being sold.
     * @return The total sell value in units of {@link #currency}.
     * @throws IllegalArgumentException If {@code amountSold} is negative.
     */
    private int computeSellValue(int id, int amountSold) {
        Preconditions.checkArgument(amountSold >= 0);

        int totalMoney = 0;
        int value = ItemDefinition.ALL.retrieve(id).getValue();

        int itemsToReachMaxPrice;
        int expectedAmount;

        double maxPrice;
        double minPrice;
        double priceChange;
        double startingPrice;

        int storeIndex = items.computeIndexForId(id).orElse(-1);
        expectedAmount = (storeIndex != -1) ? amountMap[storeIndex].orElse(GEN_DEFAULT_STOCK) : GEN_DEFAULT_STOCK;
        int amountStocked = (storeIndex != -1) ? items.computeAmountForIndex(storeIndex) : 0;

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

        int maxPriceItems = ((expectedAmount - amountStocked) * priceChange >= (maxPrice - startingPrice))
                ? (expectedAmount - itemsToReachMaxPrice - amountStocked) : 0;

        if (maxPriceItems > amountSold) {
            maxPriceItems = amountSold;
        }

        int minPriceItems = ((expectedAmount - amountStocked - amountSold) * priceChange <= (minPrice - startingPrice))
                ? (amountSold + amountStocked - expectedAmount) : 0;

        if (minPriceItems > amountSold) {
            minPriceItems = amountSold;
        }

        int startingIndex = maxPriceItems;
        int endingIndex = amountSold - minPriceItems - 1;
        int netIndex = endingIndex - startingIndex + 1;


        valueMod += minPriceItems * (minPrice - startingPrice) + maxPriceItems * (maxPrice - startingPrice);

        double startingPriceMod = (expectedAmount - amountStocked - startingIndex) * priceChange;
        double endingPriceMod = (expectedAmount - amountStocked - endingIndex) * priceChange;
        double netPriceMod = (endingPriceMod + startingPriceMod);

        valueMod += netPriceMod * (double) (netIndex / 2) + (netIndex % 2 * 0.5 * netPriceMod);
        totalMoney = (int) (value * valueMod);

        return totalMoney;
    }

    /**
     * Determines whether a given {@link Item} can be sold to this shop.
     *
     * @param item The item being checked.
     * @return {@code true} if the item can be sold to this shop.
     */
    public boolean computeCanSell(Item item) {
        return computeCanSell(item.getId());
    }

    /**
     * Determines whether a given item id can be sold to this shop.
     * <p>
     * Items may not be sold if they are:
     * <ul>
     *     <li>A currency identifier.</li>
     *     <li>Not tradeable according to {@link ItemDefinition#isTradeable()}.</li>
     *     <li>Disallowed by {@link #buyPolicy}.</li>
     * </ul>
     *
     * @param id The item id being checked.
     * @return {@code true} if the item can be sold to this shop.
     */
    public boolean computeCanSell(int id) {
        if (!Currency.IDENTIFIERS.contains(id) && ItemDefinition.ALL.retrieve(id).isTradeable()) {
            switch (buyPolicy) {
                case ALL:
                    return true;
                case NONE:
                    return false;
                case EXISTING:
                    return items.computeIndexForId(id).isPresent();
            }
        }
        return false;
    }

    /**
     * Initializes the shop with a list of starting stock items.
     * <p>
     * This loads the given items into the backing {@link ItemContainer}. The {@link ShopListener}
     * will snapshot original amounts (into {@link #amountMap}) when the container is initialized.
     *
     * @param shopItems The initial items for this shop.
     */
    public void init(List<IndexedItem> shopItems) {
        items.load(shopItems);
    }

    /**
     * Triggers restocking behavior based on {@link #restockPolicy}.
     * <p>
     * A new restock task is only created if the previous task has been cancelled. If a task is already
     * running, this method performs no action.
     */
    void restockItems() {
        if (restockPolicy != null) {
            switch (restockTask.getState()) {
                case RUNNING:
                    return;
                case CANCELLED:
                    restockTask = new RestockTask(this);
                    break;
            }
            world.schedule(restockTask);
        }
    }

    /**
     * @return The shop display name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The backing container holding current shop stock.
     */
    public ItemContainer getItems() {
        return items;
    }

    /**
     * @return Restock behavior configuration for this shop. {@code null} signifies that this shop will not restock items.
     */
    public RestockPolicy getRestockPolicy() {
        return restockPolicy;
    }

    /**
     * @return The policy controlling what items players may sell to this shop.
     */
    public BuyPolicy getBuyPolicy() {
        return buyPolicy;
    }

    /**
     * @return The currency used by this shop for purchases and sales.
     */
    public Currency getCurrency() {
        return currency;
    }

    /**
     * Retrieves the set of players currently viewing this shop.
     * <p>
     * Package-private to avoid exposing mutable internal state to outside packages.
     *
     * @return The viewing set.
     */
    Set<Player> getViewing() {
        return viewing;
    }

    /**
     * Retrieves the original stock snapshot map for shop slots.
     * <p>
     * Package-private to avoid exposing mutable internal state to outside packages.
     *
     * @return The original stock snapshot.
     */
    OptionalInt[] getAmountMap() {
        return amountMap;
    }

    /**
     * Retrieves the set of slot indexes that have reached the restock threshold and require replenishment.
     *
     * @return The restock-needed slot index set.
     */
    public Set<Integer> getRestockItems() {
        return restockItems;
    }
}
