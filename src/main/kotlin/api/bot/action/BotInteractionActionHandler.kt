package api.bot.action

import api.bot.SuspendableCondition
import api.predef.*
import io.luna.game.model.Entity
import io.luna.game.model.Entity.EntityDistanceComparator
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import io.luna.net.msg.`in`.GroundItemClickMessageReader
import io.luna.net.msg.`in`.NpcClickMessageReader
import io.luna.net.msg.`in`.ObjectClickMessageReader
import io.luna.net.msg.`in`.PlayerClickMessageReader
import java.util.*
import kotlin.reflect.KClass

/**
 * A [BotActionHandler] implementation for interaction related actions.
 */
class BotInteractionActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    /**
     * An action that forces the [Bot] to interact with [target]. If the bot is out of viewing distance, it will walk
     * within viewing distance before the correct interaction is sent. This function will unsuspend when the bot
     * is interacting with the [target] or if the task cannot complete.
     *
     * One of the following packets will be sent from this function: [PlayerClickMessageReader],
     * [ObjectClickMessageReader], [NpcClickMessageReader], or [GroundItemClickMessageReader].
     *
     * @param option The interaction option.
     * @param target The target to interact with.
     * @return `false` if the interaction was unsuccessful.
     */
    suspend fun interact(option: Int, target: Entity): Boolean {
        bot.resetInteractingWith()
        bot.resetInteractionTask()
        bot.resetMobFollowTask()

        val walkingSuspendCond = SuspendableCondition({ bot.isViewableFrom(target) || bot.walking.isEmpty })
        bot.walking.walkUntilReached(target)
        if (!bot.isViewableFrom(target)) {
            if (!walkingSuspendCond.submit().await()) {
                return false
            }
        }
        val interactSuspendCond = SuspendableCondition({ bot.isInteractingWith(target) }, 30)
        when (target) {
            is Player -> bot.output.sendPlayerInteraction(option, target)
            is Npc -> bot.output.sendNpcInteraction(option, target)
            is GameObject -> bot.output.sendObjectInteraction(option, target)
            is GroundItem -> bot.output.sendGroundItemInteraction(option, target)
            else -> throw IllegalStateException("This entity cannot be interacted with.")
        }
        return interactSuspendCond.submit().await()
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