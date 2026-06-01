package engine.trade

import io.luna.game.model.item.ItemContainer

/**
 * An item container used for one side of a trade offer.
 *
 * Trade containers hold up to 28 items and use standard stacking rules, matching normal inventory-style item behavior.
 * The container is backed by the trade offer interface component.
 *
 * @author lare96
 */
open class TradeItemContainer : ItemContainer(28, StackPolicy.STANDARD, 3415)