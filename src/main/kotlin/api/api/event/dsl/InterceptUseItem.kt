package api.event.dsl

import api.event.Matcher
import api.predef.*
import io.luna.game.event.impl.UseItemEvent.*
import io.luna.game.model.mob.interact.InteractionPolicy

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
        matcher.set(id to itemId, { action(this) }, InteractionPolicy.UNSPECIFIED)
        matcher.set(itemId to id, { action(this) }, InteractionPolicy.UNSPECIFIED)
    }

    /**
     * Intercepts an [ItemOnObjectEvent].
     */
    fun onObject(objectId: Int, interaction: InteractionPolicySupplier, action: ItemOnObjectEvent.() -> Unit) {
        val matcher = Matcher.get<ItemOnObjectEvent, Pair<Int, Int>>()
        matcher.set(id to objectId, { action(this) }, interaction)
    }

    /**
     * Intercepts an [ItemOnObjectEvent].
     */
    fun onObject(objectId: Int, action: ItemOnObjectEvent.() -> Unit) =
        onObject(objectId, InteractionPolicy.STANDARD_SIZE, action)

    /**
     * Intercepts an [ItemOnNpcEvent].
     */
    fun onNpc(npcId: Int, interaction: InteractionPolicySupplier, action: ItemOnNpcEvent.() -> Unit) {
        val matcher = Matcher.get<ItemOnNpcEvent, Pair<Int, Int>>()
        matcher.set(id to npcId, { action(this) }, interaction)
    }

    /**
     * Intercepts an [ItemOnNpcEvent] using the standard interaction policy.
     */
    fun onNpc(npcId: Int, action: ItemOnNpcEvent.() -> Unit) =
        onNpc(npcId, InteractionPolicy.STANDARD_SIZE, action)

    /**
     * Intercepts an [ItemOnPlayerEvent].
     */
    fun onPlayer(interaction: InteractionPolicySupplier,
                 action: ItemOnPlayerEvent.() -> Unit) {
        val matcher = Matcher.get<ItemOnPlayerEvent, Int>()
        matcher.set(id, { action(this) }, interaction)
    }

    /**
     * Intercepts an [ItemOnPlayerEvent].
     */
    fun onPlayer(action: ItemOnPlayerEvent.() -> Unit) = onPlayer(InteractionPolicy.STANDARD_SIZE, action)

    /**
     * Intercepts an [ItemOnGroundItemEvent].
     */
    fun onGroundItem(itemId: Int, interaction: InteractionPolicySupplier,
                     action: ItemOnGroundItemEvent.() -> Unit) {
        val matcher = Matcher.get<ItemOnGroundItemEvent, Pair<Int, Int>>()
        matcher.set(id to itemId, { action(this) }, interaction)
    }

    /**
     * Intercepts an [ItemOnGroundItemEvent].
     */
    fun onGroundItem(itemId: Int, action: ItemOnGroundItemEvent.() -> Unit) =
        onGroundItem(itemId, InteractionPolicy.STANDARD_SIZE, action)
}