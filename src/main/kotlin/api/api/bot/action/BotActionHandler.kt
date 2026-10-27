package api.bot.action

import api.bot.Suspendable.waitFor
import api.bot.SuspendableFuture
import io.luna.game.model.mob.bot.Bot
import api.bot.webwalk.plan.BankAccess
import api.bot.webwalk.walk.BankWithdrawal
import api.bot.webwalk.walk.WebWalkResult
import api.bot.webwalk.walk.WebWalker
import api.bot.zone.HomeTravelStrategy
import api.bot.zone.SubZone
import api.bot.zone.WalkingTravelStrategy
import api.bot.zone.Zone
import api.predef.*
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import engine.controllers.WildernessLocatableController.wildernessLevel
import game.skill.magic.Magic.teleport
import io.luna.Luna
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.`object`.GameObject

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

    /**
     * Handles trade requests, final confirmation clicks, and closing trade windows through normal client output.
     */
    val trading = BotTradingActionHandler(bot, this)

    /**
     * Exchanges owned supplies with native farming tool-leprechaun storage.
     */
    val farming = BotFarmingActionHandler(bot, this)

    /**
     * Determines whether the bot owns the requested quantity across all tracked item containers.
     *
     * Uses [Bot.itemTracker] to combine quantities with the same item id across equipment, inventory, and bank storage.
     * The requested amount may be split between containers. This checks ownership without moving or reserving items.
     *
     * @param item The required item id and total quantity.
     * @return `true` if the tracked total meets the requested amount.
     */
    fun has(item: Item): Boolean {
        return bot.itemTracker.count(item.id) >= item.amount
    }

    /**
     * Determines whether the bot owns the requested quantity of at least one supplied item.
     *
     * Each candidate uses the combined tracked quantity through [has]. The method returns as soon as one candidate's
     * requested amount is met.
     *
     * @param items The candidate items to search for.
     * @return `true` if the tracked total meets any candidate's requested amount.
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
     * Determines whether the bot owns the requested quantity of every supplied item.
     *
     * Each entry independently checks the combined tracked quantity through [has]. Repeated item ids are checked
     * independently; their requested amounts are not added together, and no items are reserved.
     *
     * @param items The required items to search for.
     * @return `true` if the tracked total meets every entry's requested amount.
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
     * Uses [Bot.itemTracker] to check for a positive combined quantity across equipment, inventory, and bank storage.
     * Use [has] with an [Item] to require a specific total amount.
     *
     * @param id The item id to search for.
     * @return `true` if the tracked quantity for [id] is positive.
     */
    fun has(id: Int): Boolean {
        return bot.itemTracker.contains(id)
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

    /**
     * Travels to a target zone using the web-walker, or any configured zone travel strategy.
     *
     * The bot returns immediately if it is already inside the zone. If the bot is deep enough in the wilderness and the
     * destination is safe, it first attempts to flee the wilderness before using normal travel strategies. The
     * web-walker is attempted before the zone's configured travel strategy list is evaluated, which is only used if
     * the web-walker can't get the bot there.
     *
     * @param zone The zone to travel to.
     * @param bank The most of the bank that the web-walker may withdraw for the trip, see [webWalkTo].
     * @return `true` if the bot reached or was already inside [zone], otherwise `false`.
     */
    suspend fun travelTo(zone: Zone, bank: BankAccess = BankAccess.ALL): Boolean {
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
            if (bot.subZones.isNotEmpty()) {
                val subZone = bot.subZones.first()
                val oldParent = subZone.parent(bot)
                val oldOutside = subZone.outside(bot)
                if (!subZone.leave(bot, oldParent, oldOutside)) {
                    bot.log("Could not leave sub-zone {${subZone}}.")
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
            // Let the web-walker plan the cheapest trip first, which covers walking, doors, ladders and teleports.
            if (webWalkTo(zone, attempts = WEB_WALK_ATTEMPTS, bank = bank) || bot.isViewableFrom(dest)) {
                return true
            }
            for (strategy in zone.travel) {
                if (strategy is WalkingTravelStrategy) {
                    // The web-walker already tried to walk there.
                    continue
                }
                bot.log("Attempting strategy ${strategy.javaClass.simpleName}.")
                if (strategy.canTravel(bot, this, dest)) {
                    if (strategy.travel(bot, this, dest)) {
                        return true
                    }
                }
            }
            bot.log("No travel strategies were successful, falling back to zone teleportation.")
            val goldItem = Item(995, if (zone.price < 1) 250 else zone.price) // Deduct some gold, if we can.
            if (!bot.inventory.contains(goldItem)) {
                bot.bank.remove(goldItem)
            } else {
                bot.inventory.remove(goldItem)
            }
            bot.teleport(dest)
            return waitFor { bot.isViewableFrom(dest) }
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
     *    outside/inside transition anchors or walks directly to the inside anchor.
     *
     * Sub-zones with an [SubZone.outside] anchor are treated as transition-based areas. The bot first walks
     * to the outside anchor, calls [SubZone.enter], and then optionally walks toward [SubZone.inside] if the
     * sub-zone state did not update immediately. All walking is done with the web-walker.
     *
     * Sub-zones without an outside anchor are treated as normal walkable areas. The bot simply web-walks to
     * the inside anchor and succeeds if it gets there or the bot's sub-zone updates during movement.
     *
     * @param zone The sub-zone to travel into.
     * @param bank The most of the bank that the web-walker may withdraw for the trip, see [webWalkTo].
     * @return `true` if the bot successfully reaches or enters [zone], otherwise `false`.
     */
    suspend fun travelTo(zone: SubZone, bank: BankAccess = BankAccess.ALL): Boolean {
        suspend fun defaultTravelTo(): Boolean {
            val stateBefore = bot.reflex.isDisableCombatReflex
            try {
                bot.reflex.isDisableCombatReflex = true
                // We are already in this sub-zone.
                if (zone in bot.subZones) {
                    return true
                }

                // We're below level 20 in the wilderness and going home.
                if (bot.wildernessLevel < 20 && zone == SubZone.HOME) {
                    return HomeTravelStrategy.travel(bot, this, Luna.settings().game().startingPosition())
                }
                val parent = zone.parent(bot)
                val outside = zone.outside(bot)


                // If we're in a sub-zone, try and leave first.
                if (bot.subZones.isNotEmpty()) {
                    // TODO This could incorrectly call the wrong subzone, but webwalking will fix this issue anyway.
                    val subZone = bot.subZones.first()
                    val oldParent = subZone.parent(bot)
                    val oldOutside = subZone.outside(bot)
                    if (!subZone.leave(bot, oldParent, oldOutside)) {
                        bot.log("Could not leave sub-zone {${subZone}}.")
                        return false
                    }
                }

                // Then, try and travel to the parent zone.
                if (bot.zone != parent && !travelTo(parent, bank)) {
                    bot.log("Could not travel to parent {$parent} of $zone.")
                    return false
                }

                // Travel to the outside anchor from the parent zone, if available.
                if (outside != null) {
                    if (!webWalkTo(outside, attempts = WEB_WALK_ATTEMPTS, bank = bank)) {
                        bot.log("Could not travel to $zone outside anchor.")
                        return false
                    }

                    // Try and enter the sub-zone.
                    if (!zone.enter(bot, parent, outside)) {
                        bot.log("Could not enter $zone.")
                        return false
                    }

                    // Try and path to the inside anchor if the transition did not update sub-zone state immediately.
                    if (zone !in bot.subZones) {
                        webWalkTo(zone, attempts = WEB_WALK_ATTEMPTS, bank = bank)
                    }
                    return zone in bot.subZones
                }
                // Sub-zone has no transitions, walk directly to the inside anchor.
                return webWalkTo(zone, attempts = WEB_WALK_ATTEMPTS, bank = bank) || zone in bot.subZones
            } finally {
                bot.reflex.isDisableCombatReflex = stateBefore
            }
        }

        if (!defaultTravelTo()) {
            // We cannot travel to the sub-zone by default means. Attempt to teleport the bot wherever it needs to go.
            bot.teleport(zone.inside)
            zone.parent(bot).price.apply { bot.bank.remove(Item(995, this)) }
            return waitFor { bot.isViewableFrom(zone.inside) }
        }
        return bot.isViewableFrom(zone.inside)
    }

    /**
     * Walks this bot to a position with the web-walker, in one call from a script:
     * ```
     * if (!handler.webWalkTo(Position(3213, 3428))) return true
     * ```
     *
     * The web-walker plans the cheapest trip for what the bot carries and can do right now, and takes it through doors,
     * gates, up and down ladders and stairs, and by teleport. The coins, runes and teleport jewellery in its bank count too:
     * the bot may stop at the nearest bank first to withdraw them, when that makes the trip cheaper (see
     * [WebWalker.planTrip]). [travelTo] walks with it too, but it also leaves the wilderness first and falls back to paying
     * for a zone teleport, which this never does. Use [webWalk] to learn why a walk failed, which is also in the log of the
     * bot.
     *
     * @param dest Where to go.
     * @param radius How close to [dest], in tiles on the same plane, is close enough. The default is the exact tile, which
     * fails if the tile can't be stood on.
     * @param attempts How many times to try, planning again each time. The default is to fail fast.
     * @param bank The most of the bank that the trip may withdraw. [BankAccess.NONE] never goes to the bank.
     * @return `true` if the bot is within [radius] of [dest].
     */
    suspend fun webWalkTo(dest: Position, radius: Int = 0, attempts: Int = 1, bank: BankAccess = BankAccess.ALL): Boolean =
        webWalk(dest, radius, attempts, bank).success

    /**
     * Walks this bot to the anchor of a zone with the web-walker. See [webWalkTo].
     *
     * @param zone The zone to go to.
     * @param radius How close to the anchor, in tiles, is close enough. The default is [WEB_WALK_AREA_RADIUS], because the
     * anchor of a zone is a place in it and not a tile that has to be stood on.
     * @param attempts How many times to try, planning again each time.
     * @param bank The most of the bank that the trip may withdraw.
     * @return `true` if the bot is within [radius] of the anchor.
     */
    suspend fun webWalkTo(zone: Zone, radius: Int = WEB_WALK_AREA_RADIUS, attempts: Int = 1,
                          bank: BankAccess = BankAccess.ALL): Boolean =
        webWalkTo(zone.anchor, radius, attempts, bank)

    /**
     * Walks this bot to the inside anchor of a sub-zone with the web-walker. See [webWalkTo].
     *
     * Only the walking is done: a sub-zone that has to be entered some other way than by walking (a lever, a boat, a
     * ditch, anything in [SubZone.enter]) is not entered, and the walk fails if the web has no way there.
     *
     * @param zone The sub-zone to go to.
     * @param radius How close to the inside anchor, in tiles, is close enough. The default is [WEB_WALK_AREA_RADIUS].
     * @param attempts How many times to try, planning again each time.
     * @param bank The most of the bank that the trip may withdraw.
     * @return `true` if the bot is within [radius] of the inside anchor.
     */
    suspend fun webWalkTo(zone: SubZone, radius: Int = WEB_WALK_AREA_RADIUS, attempts: Int = 1,
                          bank: BankAccess = BankAccess.ALL): Boolean =
        webWalkTo(zone.inside, radius, attempts, bank)

    /**
     * Walks this bot to a position with the web-walker, like [webWalkTo], but returns how it went and not only whether it
     * worked.
     *
     * @param dest Where to go.
     * @param radius How close to [dest], in tiles on the same plane, is close enough.
     * @param attempts How many times to try, planning again each time.
     * @param bank The most of the bank that the trip may withdraw.
     * @return The result, with the trip that was planned, how far it got, why it stopped, and what it withdrew.
     */
    suspend fun webWalk(dest: Position, radius: Int = 0, attempts: Int = 1, bank: BankAccess = BankAccess.ALL): WebWalkResult =
        WebWalker.webWalk(bot, dest, radius, attempts, bank)

    /** Routes to one of the caller's verified stands, using carried resources without a bank detour. */
    suspend fun webWalkToAny(destinations: Collection<Position>): Boolean =
        WebWalker.webWalkToAny(bot, destinations).success

    /**
     * Walks this bot to the nearest bank with the web-walker, in one call from a script:
     * ```
     * val bank = handler.webWalkToNearestBank() ?: return true
     * handler.interactions.interact(2, bank)
     * ```
     *
     * The nearest bank is the one that the cheapest trip ends at, for what the bot carries and can do right now, so a bot
     * that has a teleport and the runes for it may take that. The bot stops in front of a booth, on the side of the counter
     * that customers use. [BotBankingActionHandler.travelToNearestBank] uses this too, and falls back to going home.
     *
     * @param radius How close to the tile in front of the booth, in tiles, is close enough. The default is the exact tile.
     * @param attempts How many times to try, planning again each time. The default is to fail fast.
     * @return The bank booth that the bot walked to, or `null` if it couldn't get to a bank. The log of the bot says why.
     */
    suspend fun webWalkToNearestBank(radius: Int = 0, attempts: Int = 1): GameObject? {
        val bank = WebWalker.webWalkToNearestBank(bot, radius, attempts).takeIf { it.success }?.bank ?: return null
        return BankWithdrawal.booth(bank)
    }

    companion object {

        /**
         * How close, in tiles, to the anchor of a zone or sub-zone is close enough when walking there with the web-walker.
         */
        const val WEB_WALK_AREA_RADIUS = 2

        /**
         * How many times zone and sub-zone travel tries the web-walker, which plans again each time. A second attempt
         * covers a bot that was held up on the way, such as at a door that somebody else was using.
         */
        const val WEB_WALK_ATTEMPTS = 2
    }
}
