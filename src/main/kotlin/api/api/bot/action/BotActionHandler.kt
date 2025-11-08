package api.bot.action

import api.bot.SuspendableFuture
import api.predef.*
import io.luna.game.model.Entity
import io.luna.game.model.Entity.EntityDistanceComparator
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler
import io.luna.game.model.`object`.GameObject
import java.util.*
import kotlin.reflect.KClass

/**
 * Provides access to collection of functions which [Bot] types can use to perform specialized multi-stage actions that
 * utilize packets from [BotOutputMessageHandler]. These types of actions must be used within [BotScript] types to
 * take advantage of being suspended by [SuspendableFuture].
 *
 * Almost every function will return [SuspendableFuture] which can be used to suspend the underlying coroutine
 * and/or send a signal to the future's channel to unsuspend it. Functions may also block coroutines with sub-tasks
 * before returning.
 */
class BotActionHandler(val bot: Bot) {

    /**
     * The banking action handler.
     */
    val banking = BotBankingActionHandler(bot, this)

    /**
     * The shop action handler.
     */
    val shop = BotShopActionHandler(bot, this)

    /**
     * The movement action handler.
     */
    val movement = BotMovementActionHandler(bot)

    /**
     * The equipment action handler.
     */
    val equipment = BotEquipmentActionHandler(bot, this)

    /**
     * The inventory action handler.
     */
    val inventory = BotInventoryActionHandler(bot, this)

    /**
     * The interactions action handler.
     */
    val interactions = BotInteractionActionHandler(bot, this)

    /**
     * The widgets action handler.
     */
    val widgets = BotWidgetActionHandler(bot, this)

    /**
     * Determines if [bot] has [item] in its equipment, inventory, or bank.
     */
    fun has(item: Item): Boolean {
        if (bot.equipment.contains(item)) {
            return true
        } else if (bot.inventory.contains(item)) {
            return true
        } else if (bot.bank.contains(item)) {
            return true
        }
        return false
    }

    /**
     * Determines if [bot] has any of [items] in its equipment, inventory, or bank.
     */
    fun hasAny(items: List<Item>): Boolean {
        for (it in items) {
            if (has(it)) {
                return true
            }
        }
        return false
    }

    /**
     * Determines if [bot] has all of [items] in its equipment, inventory, or bank.
     */
    fun hasAll(items: List<Item>): Boolean {
        for (it in items) {
            if (!has(it)) {
                return false
            }
        }
        return true
    }

    /**
     * Returns a set of all viewable entities matching [type] and [cond], sorted by closest -> furthest distance.
     *
     * @param type The type of entity.
     * @param cond The condition.
     */
    fun <T : Entity> findViewable(type: KClass<T>, cond: (T) -> Boolean): MutableSet<T> {
        val base = bot.position
        return world.chunks.find(bot.position,
                                 type.java,
                                 { TreeSet(EntityDistanceComparator(bot)) },
                                 { it.isWithinDistance(base, Position.VIEWING_DISTANCE) && cond(it) },
                                 Position.VIEWING_DISTANCE);
    }

    /**
     * Returns the nearest entity matching [type] and [cond]. Returns `null` if no entity was found.
     *
     * @param type The type of entity.
     * @param cond The condition.
     */
    fun <T : Entity> findNearest(type: KClass<T>, cond: (T) -> Boolean): T? {

        // Check if entity is within viewable distance first.
        val viewableResult = findViewable(type, cond)
        if (viewableResult.isNotEmpty()) {
            return viewableResult.firstOrNull()
        }

        // Do a more expensive check. We can optimize for specific entity types.
        val found = TreeSet<T>(EntityDistanceComparator(bot))
        val searchList: Iterable<Entity>? =
            when (type) {
                Player::class -> world.players
                Npc::class -> world.npcs
                GameObject::class -> world.objects
                GroundItem::class -> world.items
                else -> null
            }
        if (searchList != null) {
            // Do an optimized check, only search entities of matching type.
            for (entity in searchList) {
                @Suppress("UNCHECKED_CAST") // We know it's safe.
                if (cond(entity as T)) {
                    found.add(entity)
                }
            }
        } else {
            // Worst case scenario, find entities by searching through every chunk in the game world.
            for (repository in world.chunks) {
                val entityType = EntityType.CLASS_TO_TYPE[type.java] ?: throw IllegalStateException("Invalid type.")
                val search = repository.getAll<T>(entityType)
                for (entity in search) {
                    if (cond(entity)) {
                        found.add(entity)
                    }
                }
            }
        }
        return found.firstOrNull()
    }

    /**
     * Returns the nearest [Npc] matching [id] or `null` if nothing was found.
     */
    fun findNearestNpc(id: Int): Npc? = findNearest(Npc::class) { it.id == id }

    /**
     * Returns the nearest [Npc] matching [name] or `null` if nothing was found.
     */
    fun findNearestNpc(name: String): Npc? = findNearest(Npc::class) { it.definition.name == name }

    /**
     * Returns the nearest [GameObject] matching [id] or `null` if nothing was found.
     */
    fun findNearestObject(id: Int): GameObject? = findNearest(GameObject::class) { it.id == id }

    /**
     * Returns the nearest [GameObject] matching [name] or `null` if nothing was found.
     */
    fun findNearestObject(name: String): GameObject? = findNearest(GameObject::class) { it.definition.name == name }

    /**
     * Returns the nearest [GroundItem] matching [id] or `null` if nothing was found.
     */
    fun findNearestItem(id: Int): GroundItem? = findNearest(GroundItem::class) { it.id == id }

    /**
     * Returns the nearest [GroundItem] matching [name] or `null` if nothing was found.
     */
    fun findNearestItem(name: String): GroundItem? = findNearest(GroundItem::class) { it.def().name == name }
}
