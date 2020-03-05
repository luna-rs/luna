package api.event

import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.event.impl.ItemOnObjectEvent

/**
 * A model that intercepts [ItemOnItemEvent]s and [ItemOnObjectEvent]s.
 *
 * @author lare96
 */
class InterceptUseItem(private val id: Int) {

    /**
     * Intercepts an [ItemOnItemEvent]. **Will match both ways!**
     */
    fun onItem(itemId: Int, action: ItemOnItemEvent.() -> Unit) {
        val matcher = Matcher.get<ItemOnItemEvent, Pair<Int, Int>>()
        matcher[id to itemId] = { action(this) }
        matcher[itemId to id] = { action(this) }
    }

    /**
     * Intercepts an [ItemOnObjectEvent].
     */
    fun onObject(objectId: Int, action: ItemOnObjectEvent.() -> Unit) {
        val matcher = Matcher.get<ItemOnObjectEvent, Pair<Int, Int>>()
        matcher[id to objectId] = { action(this) }
    }
}