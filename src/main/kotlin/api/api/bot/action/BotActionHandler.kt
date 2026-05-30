package api.bot.action

import api.bot.SuspendableFuture
import api.bot.zone.HomeTravelStrategy
import api.bot.zone.SubZone
import api.bot.zone.WalkingTravelStrategy
import api.bot.zone.Zone
import api.predef.*
import engine.controllers.WildernessLocatableController.wildernessLevel
import io.luna.Luna
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.movement.NavigationResult
import kotlinx.coroutines.future.await

/**
 * Provides high-level action helpers for a server-controlled [Bot].
 *
 * This handler groups together the lower-level bot action handlers used by scripts, brains, reflexes, and other bot
 * behavior systems. Most operations in this class are intentionally multi-stage actions. For example, retrieving an item
 * may involve checking the inventory, unequipping equipment, travelling to a bank, opening the bank interface, depositing
 * items for space, and withdrawing the requested item.
 *
 * Many action helpers suspend because bot actions are driven through simulated client output and normal game-cycle
 * feedback. Actions that send packets or wait for game state should be called from bot scripts or other coroutine-backed
 * behavior systems that can safely suspend through [SuspendableFuture].
 *
 * @property bot The bot this action handler controls.
 *
 * @author lare96
 */
class BotActionHandler(val bot: Bot) {

    /**
     * Handles banking actions such as opening banks, depositing items, and withdrawing items.
     */
    val banking = BotBankingActionHandler(bot, this)

    /**
     * Handles shop interactions and shop-related item transfers.
     */
    val shop = BotShopActionHandler(bot, this)

    /**
     * Handles equipment actions such as equipping and unequipping items.
     */
    val equipment = BotEquipmentActionHandler(bot, this)

    /**
     * Handles inventory actions such as using, dropping, or otherwise manipulating carried items.
     */
    val inventory = BotInventoryActionHandler(bot, this)

    /**
     * Handles direct entity, object, NPC, and player interaction attempts.
     */
    val interactions = BotInteractionActionHandler(bot, this)

    /**
     * Handles widget, interface, and component-level interactions.
     */
    val widgets = BotWidgetActionHandler(bot, this)

    /**
     * Handles combat-specific actions such as fleeing, attacking, and combat recovery behavior.
     */
    val combat = BotCombatActionHandler(bot, this)

    // todo work on, docs
    val trading = BotTradingActionHandler(bot, this)

    val supplies = BotSuppliesActionHandler(bot, this)

    /**
     * Determines whether the bot owns the requested item in equipment, inventory, or bank storage.
     *
     * This is an ownership-style check across all common bot item containers, not just the inventory. It is useful when a
     * script only cares whether the bot has access to an item somewhere.
     *
     * @param item The item to search for.
     * @return `true` if the item exists in equipment, inventory, or bank storage.
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
     * Determines whether the bot owns at least one of the supplied items.
     *
     * Each candidate is checked across equipment, inventory, and bank storage through [has]. The method returns as soon as
     * the first owned item is found.
     *
     * @param items The candidate items to search for.
     * @return `true` if any supplied item exists in equipment, inventory, or bank storage.
     */
    fun hasAny(items: Iterable<Item>): Boolean {
        for (it in items) {
            if (has(it)) {
                return true
            }
        }
        return false
    }

    /**
     * Determines whether the bot owns every supplied item.
     *
     * Each item is checked across equipment, inventory, and bank storage through [has]. This method checks whether each
     * item entry can be found somewhere, but it does not currently reserve or count duplicate requirements atomically.
     *
     * @param items The required items to search for.
     * @return `true` if every supplied item exists in equipment, inventory, or bank storage.
     */
    fun hasAll(items: Iterable<Item>): Boolean {
        for (it in items) {
            if (!has(it)) {
                return false
            }
        }
        return true
    }

    /**
     * Determines whether the bot owns an item with the supplied id.
     *
     * The id is checked across equipment, inventory, and bank storage. Use this overload when item amount, metadata, or
     * other [Item] fields are not important.
     *
     * @param id The item id to search for.
     * @return `true` if an item with [id] exists in equipment, inventory, or bank storage.
     */
    fun has(id: Int): Boolean {
        if (bot.equipment.contains(id)) {
            return true
        } else if (bot.inventory.contains(id)) {
            return true
        } else if (bot.bank.contains(id)) {
            return true
        }
        return false
    }

    /**
     * Determines whether the bot owns at least one item with one of the supplied ids.
     *
     * @param ids The candidate item ids to search for.
     * @return `true` if any supplied id exists in equipment, inventory, or bank storage.
     */
    fun hasAnyIds(ids: Iterable<Int>): Boolean {
        for (id in ids) {
            if (has(id)) {
                return true
            }
        }
        return false
    }

    /**
     * Determines whether the bot owns every supplied item id.
     *
     * This method checks whether each id can be found somewhere across equipment, inventory, or bank storage. It does not
     * currently reserve or count duplicate id requirements atomically.
     *
     * @param ids The required item ids to search for.
     * @return `true` if every supplied id exists in equipment, inventory, or bank storage.
     */
    fun hasAllIds(ids: Iterable<Int>): Boolean {
        for (id in ids) {
            if (!has(id)) {
                return false
            }
        }
        return true
    }

    /**
     * Retrieves a single requested item into the bot's inventory when possible.
     *
     * This method first ensures there is enough inventory space, depositing all carried items if required. It will
     * immediately return `true` if the item is in already in the inventory. If the item is equipped, it will try to
     * unequip it into the inventory.
     *
     * @param id The requested item ID to retrieve.
     * @return `true` if the requested item was moved into the inventory.
     */
    suspend fun retrieve(id: Int): Boolean {
        return retrieveAny(listOf(Item(id))) != null
    }

    /**
     * Retrieves the first available item from a set of candidate items.
     *
     * The search order is inventory, equipment, then bank. If the item is already in the inventory, it is returned
     * immediately. If the item is equipped, the bot attempts to unequip it, depositing inventory items first if needed.
     * If the item is only in the bank, the bot travels to a bank, opens it, and withdraws the first matching candidate.
     *
     * @param items The candidate items to retrieve.
     * @return The first successfully retrieved item, or `null` if none could be found or retrieved.
     */
    suspend fun retrieveAny(items: Iterable<Item>): Item? {
        bot.log("Trying to retrieve at least 1 item.")
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
                    banking.travelToBankDepositAll()
                    if (!bot.inventory.hasSpaceFor(it)) {
                        bot.log("Still no space.")
                        continue
                    }
                }
                return if (equipment.unequip(it.equipDef.index)) it else {
                    bot.log("Could not unequip item.")
                    continue
                }
            }
        }
        // Then look at bank.
        for (it in items) {
            val withdrawItem = listOf(it)
            if (bot.bank.contains(it) && banking.travelToBankWithdraw(withdrawItem)) {
                return it
            }
        }
        bot.log("Couldn't find any items.")
        return null
    }

    /**
     * Retrieves all requested items into the bot's inventory when possible.
     *
     * This method first ensures there is enough inventory space, depositing all carried items if required. It then removes
     * already-held inventory items from the remaining requirement list, attempts to unequip equipped requirements, and
     * finally travels to a bank to withdraw anything still missing.
     *
     * @param items The required items to retrieve.
     * @return `true` if every requested item was moved into the inventory.
     */
    suspend fun retrieveAll(items: Iterable<Item>): Boolean {
        bot.log("Trying to retrieve all items.")
        if (!bot.inventory.hasSpaceForAll(items)) {
            bot.log("No space for the items.")
            banking.travelToBankDepositAll()
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
            } else if (bot.equipment.contains(item) && equipment.unequip(item.equipDef.index)) {
                it.remove()
            }
        }

        // Then look at bank.
        it = remaining.iterator()
        while (it.hasNext()) {
            val item = it.next()
            val withdraw = listOf(item)
            bot.log("Trying to retrieve ${name(item)}.")
            if (bot.bank.contains(item) && banking.travelToBankWithdraw(withdraw)) {
                it.remove()
            }
        }
        return remaining.isEmpty()
    }

    // TODO@0.5.0 Better integrate both zone systems, remove boilerplate (use an interface).
    /**
     * Travels to a target zone using walking, teleportation, or any configured zone travel strategy.
     *
     * The bot returns immediately if it is already inside the zone. If the bot is deep enough in the wilderness and the
     * destination is safe, it first attempts to flee the wilderness before using normal travel strategies. Nearby targets
     * are attempted by walking before the zone's configured travel strategy list is evaluated.
     *
     * @param zone The zone to travel to.
     * @return `true` if the bot reached or was already inside [zone], otherwise `false`.
     */
    suspend fun travelTo(zone: Zone, preferWalking: Boolean = false): Boolean {
        val stateBefore = bot.reflex.isDisableCombatReflex
        try {
            bot.reflex.isDisableCombatReflex = true
            // We're already inside the zone we want to go to.
            if (bot.zone == zone) {
                return true
            }

            // We're below level 20 in the wilderness and going home.
            if (bot.wildernessLevel < 20 && zone == Zone.VARROCK) {
                return HomeTravelStrategy.travel(bot, this, Luna.settings().game().startingPosition())
            }

            // Leave current sub-zone if needed.
            if (bot.subZone != null) {
                val parent = bot.subZone.parent(bot)
                val outside = bot.subZone.outside(bot)
                if (!bot.subZone.leave(bot, parent, outside)) {
                    bot.log("Could not leave sub-zone{${bot.subZone}}.")
                    return false
                }
            }

            // Leave the wilderness first.
            bot.log("Travelling to $zone")
            if (bot.wildernessLevel >= 20 && zone.safe) {
                bot.log("Leaving wilderness first.")
                if (!combat.fleeWilderness()) {
                    return false
                }
            }
            val dest = zone.anchor
            val distance = bot.position.computeLongestDistance(dest)
            if (distance < 64 || (preferWalking && distance < 256)) { // todo random chance
                // Just try and run there before using all strategies.
                if (WalkingTravelStrategy.travel(bot, this, dest)) {
                    return true
                }
            }
            for (strategy in zone.travel) {
                bot.log("Attempting strategy ${strategy.javaClass.simpleName}.")
                if (strategy.canTravel(bot, this, dest)) {
                    if (strategy.travel(bot, this, dest)) {
                        return true
                    }
                }
            }
            bot.log("No travel strategies were successful.")
            return false
        } finally {
            bot.reflex.isDisableCombatReflex = stateBefore
        }
    }

    /**
     * Travels this bot into the requested sub-zone.
     *
     * This handles sub-zone travel in three stages:
     *
     * 1. If the bot is already inside [zone], the request succeeds immediately.
     * 2. If the bot is currently inside another sub-zone, it attempts to leave that sub-zone first.
     * 3. It travels to the requested sub-zone's parent zone, then either enters through the configured
     *    outside/inside transition anchors or paths directly to the inside anchor.
     *
     * Sub-zones with an [SubZone.outside] anchor are treated as transition-based areas. The bot first paths
     * to the outside anchor, calls [SubZone.enter], and then optionally paths toward [SubZone.inside] if the
     * sub-zone state did not update immediately.
     *
     * Sub-zones without an outside anchor are treated as normal walkable areas. The bot simply navigates to
     * the inside anchor and succeeds if navigation reaches the target or the bot's sub-zone updates during
     * movement.
     *
     * @param zone The sub-zone to travel into.
     * @return `true` if the bot successfully reaches or enters [zone], otherwise `false`.
     */
    suspend fun travelTo(zone: SubZone): Boolean {
        val stateBefore = bot.reflex.isDisableCombatReflex
        try {
            bot.reflex.isDisableCombatReflex = true
            // We are already in this sub-zone.
            if (bot.subZone == zone) {
                return true
            }

            // We're below level 20 in the wilderness and going home.
            if (bot.wildernessLevel < 20 && zone == SubZone.HOME) {
                return HomeTravelStrategy.travel(bot, this, Luna.settings().game().startingPosition())
            }
            val parent = zone.parent(bot)
            val outside = zone.outside(bot)


            // If we're in a sub-zone, try and leave first.
            if (bot.subZone != null) {
                val oldParent = bot.subZone.parent(bot)
                val oldOutside = bot.subZone.outside(bot)
                if (!bot.subZone.leave(bot, oldParent, oldOutside)) {
                    bot.log("Could not leave sub-zone {${bot.subZone}}.")
                    return false
                }
            }

            // Then, try and travel to the parent zone.
            if (bot.zone != parent && !travelTo(parent)) {
                bot.log("Could not travel to parent {$parent} of $zone.")
                return false
            }

            // Travel to the outside anchor from the parent zone, if available.
            if (outside != null) {
                if (bot.navigator.navigate(outside, true).await() == NavigationResult.NO_VALID_PATH) {
                    bot.log("Could not travel to $zone outside anchor.")
                    return false
                }

                // Try and enter the sub-zone.
                if (!zone.enter(bot, parent, outside)) {
                    bot.log("Could not enter $zone.")
                    return false
                }

                // Try and path to the inside anchor if the transition did not update sub-zone state immediately.
                if (bot.subZone != zone) {
                    bot.navigator.navigate(zone.inside, true).await()
                }
                return bot.subZone == zone
            }
            // Sub-zone has no transitions, path directly to the inside anchor.
            return bot.navigator.navigate(zone.inside, true).await() == NavigationResult.REACHED || bot.subZone == zone
        } finally {
            bot.reflex.isDisableCombatReflex = stateBefore
        }
    }
}
