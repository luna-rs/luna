package api.event.dsl

import api.event.Matcher
import io.luna.game.event.impl.UseItemEvent.*

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

    /**
     * Intercepts an [ItemOnNpcEvent].
     */
    fun onNpc(npcId: Int, action: ItemOnNpcEvent.() -> Unit) {
        val matcher = Matcher.get<ItemOnNpcEvent, Pair<Int, Int>>()
        matcher[id to npcId] = { action(this) }
    }

    /**
     * Intercepts an [ItemOnPlayerEvent].
     */
    fun onPlayer(action: ItemOnPlayerEvent.() -> Unit) {
        val matcher = Matcher.get<ItemOnPlayerEvent, Int>()
        matcher[id] = { action(this) }
    }

    /**
     * Intercepts an [ItemOnGroundItemEvent].
     */
    fun onGroundItem(itemId: Int, action: ItemOnGroundItemEvent.() -> Unit) {
        val matcher = Matcher.get<ItemOnGroundItemEvent, Pair<Int, Int>>()
        matcher[id to itemId] = { action(this) }
    }
}