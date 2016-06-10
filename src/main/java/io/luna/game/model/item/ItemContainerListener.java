package io.luna.game.model.item;

/**
 * @author lare96 <http://github.org/lare96>
 */
public interface ItemContainerListener {
    default void itemsRemoved(ItemContainer collection) {
    }

    default void itemsAdded(ItemContainer collection) {
    }

    default void capacityExceeded(ItemContainer collection) {
    }
}
