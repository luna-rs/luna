package io.luna.game.model.item.shop;

/**
 * An enum representing the possible item selling policies of a {@link Shop}.
 * <p>
 * This policy controls whether players are allowed to sell items into a shop and, if allowed, whether the shop
 * accepts any tradeable item or only items it already stocks.
 *
 * @author lare96
 */
public enum BuyPolicy {

    /**
     * The shop will buy any tradeable item (excluding currencies and other disallowed items).
     */
    ALL,

    /**
     * The shop will only buy items that are already present in its stock.
     */
    EXISTING,

    /**
     * The shop will not buy any items from players.
     */
    NONE
}
