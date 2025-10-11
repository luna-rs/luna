package api.bot.action

import api.bot.Suspendable.naturalDelay
import api.bot.Suspendable.waitFor
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
    val movement = BotMovementActionHandler(bot, this)

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
     * Determines if [bot] has any of [items], and tries to add the first one found to the inventory.
     *
     * @param items The items to look for.
     * @return The first item found from [items].
     */
    suspend fun retrieveAny(items: List<Item>): Item? {
        bot.log("Trying to retrieve at least 1 of ${items.size} items.")
        // Try inventory and equipment first.
        for (it in items) {
            bot.log("Trying to retrieve ${name(it)}.")
            if (bot.inventory.contains(it)) {
                // We already have one of the items in our inventory.
                return it
            }
            if (bot.equipment.contains(it)) {
                if (!bot.inventory.hasSpaceFor(it)) {
                    bot.log("${name(it)} found equipped, no space to unequip.")
                    travelToBankDepositAll()
                    if (!bot.inventory.hasSpaceFor(it)) {
                        bot.log("Still no space.")
                        continue
                    }
                }
                return if (equipment.unequip(it.equipDef.index).await()) it else {
                    bot.log("Could not unequip item.")
                    continue
                }
            }
        }
        // Then look at bank.
        for (it in items) {
            val withdrawItem = listOf(it)
            if (bot.bank.contains(it) && travelToBankWithdraw(withdrawItem)) {
                return it
            }
        }
        bot.log("Couldn't find any items.")
        return null
    }

    /**
     * Determines if [bot] has all of [items], and if so tries to add all of them to the inventory.
     *
     * @param items The items to look for.
     * @return `true` if the bot retrieved all the items.
     */
    suspend fun retrieveAll(items: List<Item>): Boolean {
        bot.log("Trying to retrieve all ${items.size} items.")
        if (!bot.inventory.hasSpaceForAll(items)) {
            bot.log("No space for the items.")
            travelToBankDepositAll()
            if (!bot.inventory.hasSpaceForAll(items)) {
                bot.log("Still no space.")
                return false
            }
        }

        // Try inventory and equipment first.
        val remaining = items.toMutableList()
        var it = remaining.iterator()
        while (it.hasNext()) {
            val item = it.next()
            bot.log("Trying to retrieve ${name(item)}.")
            if (bot.inventory.contains(item)) {
                // We already have one of the items in our inventory.
                it.remove()
            } else if (bot.equipment.contains(item) && equipment.unequip(item.equipDef.index).await()) {
                it.remove()
            }
        }

        // Then look at bank.
        it = remaining.iterator()
        while (it.hasNext()) {
            val item = it.next()
            val withdraw = listOf(item)
            bot.log("Trying to retrieve ${name(item)}.")
            if (bot.bank.contains(item) && travelToBankWithdraw(withdraw)) {
                it.remove()
            }
        }
        return remaining.isEmpty()
    }

    /**
     * Travels to the nearest bank and attempts to open it. Uses [BotMovementActionHandler.travelToBank] internally.
     *
     * @return `true` if the bank was opened.
     */
    suspend fun travelToBankOpen(): Boolean {
        if (bot.bank.isOpen) {
            return true
        }
        val bank = movement.travelToBank()
        if (bank != null) {
            bot.naturalDelay()
            bot.log("Opening bank.")
            if (interactions.interact(2, bank)) {
                return waitFor { bot.bank.isOpen }
            } else {
                bot.log("Could not interact with $bank.")
                return false
            }
        }
        return false
    }

    /**
     * Travels to the nearest bank, opens it, and attempts to withdraw [items]. Uses [travelToBankOpen] internally.
     *
     * **Will initially deposit all items if there isn't enough space to hold [items].**
     *
     * @return `true` if all items were withdrawn.
     */
    suspend fun travelToBankWithdraw(items: List<Item>): Boolean {
        if (travelToBankOpen()) {
            if (!bot.inventory.hasSpaceForAll(items)) {
                banking.depositAll()
            }
            var success = true
            for (it in items) {
                if (!banking.withdraw(it)) {
                    success = false
                }
            }
            return success
        }
        return false
    }

    /**
     * Travels to the nearest bank, opens it, and attempts to deposit [items]. Uses [travelToBankOpen] internally.
     *
     * @return `true` if all items were withdrawn.
     */
    suspend fun travelToBankDeposit(items: List<Item>): Boolean {
        if (travelToBankOpen()) {
            var success = true
            for (it in items) {
                if (!banking.deposit(it)) {
                    success = false
                }
            }
            return success
        }
        return false
    }

    /**
     * Travels to the nearest bank, opens it, and attempts to deposit all items. Uses [travelToBankOpen] internally.
     *
     * @return `true` if all items were deposited.
     */
    suspend fun travelToBankDepositAll(): Boolean {
        if (travelToBankOpen()) {
            return banking.depositAll()
        }
        return false
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
