package io.luna.game.model.item.shop;

/**
 * An enumerated type representing the possible buy policies of a {@link Shop}.
 *
 * @author lare96 
 */
public enum BuyPolicy {

    /**
     * Shop will buy all items.
     */
    ALL,

    /**
     * Shop will only buy items it has in stock.
     */
    EXISTING,

    /**
     * Shop will not buy any items.
     */
    NONE
}