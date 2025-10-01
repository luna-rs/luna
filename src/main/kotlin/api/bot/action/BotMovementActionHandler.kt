package api.bot.action

import api.bot.Suspendable.delay
import api.bot.Suspendable.waitFor
import api.bot.SuspendableCondition
import api.bot.SuspendableFuture
import api.bot.zone.WalkingTravelStrategy
import api.bot.zone.Zone
import io.luna.game.model.Locatable
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import world.player.item.banking.regularBank.Banking
import world.player.wilderness.WildernessAreaController.wildernessLevel
import kotlin.time.Duration.Companion.seconds

/**
 * A [BotActionHandler] implementation for movement related actions.
 */
class BotMovementActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    /**
     * An action that forces the [Bot] to walk to [target]. The returned future will unsuspend once the bot is [radius]
     * squares from the target position, the bot stops moving, or a timeout occurs.
     *
     * @param target The position to walk to.
     */
    fun walk(target: Position, radius: Int = 0): SuspendableFuture {
        // Assuming each square takes 5 seconds to walk just to be safe.
        val timeout = bot.position.computeLongestDistance(target) * 5L;
        val cond = SuspendableCondition({ bot.isWithinDistance(target, radius) }, timeout)
        bot.walking.walk(target)
        return cond.submit()
    }

    /**
     * An action that forces the [Bot] to walk to [target]. The returned future will unsuspend once the bot has reached
     * the target, stops moving, or a timeout occurs.
     *
     * @param target The target to walk to.
     */
    fun walkUntilReached(target: Locatable): SuspendableFuture {
        bot.log("Walking until $target is reached.")
        val location = target.location()
        val timeout = bot.position.computeLongestDistance(location) * 5L;
        val cond = SuspendableCondition({ bot.position.isViewable(target.location()) }, timeout)
        return if (bot.walking.walkUntilReached(target))
            cond.submit() else SuspendableFuture().signal(false)
    }

    /**
     * Forces the bot to leave the wilderness area as soon as possible. If the wilderness level is below 20 they will
     * use ::home, otherwise they will run in the direction of edgeville.
     *
     * @return `true` if the bot is no longer in the wilderness.
     */
    suspend fun leaveWilderness(): Boolean {
        if (bot.wildernessLevel > 0) {
            bot.log("Leaving wilderness.")
            if (bot.wildernessLevel >= 20) {
                bot.log("Can't teleport, running to Edgeville.")
                // TODO better way, what if player is at KBD, etc.
                WalkingTravelStrategy.travel(bot, handler, Zone.EDGEVILLE.anchor)
                waitFor { bot.wildernessLevel < 20 }
                delay()
                if (bot.wildernessLevel == 0) {
                    return true
                }
            }
            bot.log("Going home.")
            bot.output.sendCommand("home")
            return waitFor(5.seconds) { Zone.HOME.inside(bot) }
        }
        return true
    }

    /**
     * Travels to the nearest bank. If the [bot] was able to then it returns the bank as a [GameObject].
     */
    suspend fun travelToBank(): GameObject? {
        bot.log("Travelling to nearest bank.")
        if (Zone.HOME.inside(bot)) {
            // We're home, use a home bank.
            return handler.banking.homeBank()
        }

        bot.log("Looking nearby.")
        val banks = handler.findViewable(GameObject::class) { Banking.bankingObjects.contains(it.id) }
        if (banks.size > 0) {
            // Try to travel to nearby banks.
            bot.log("Found ${banks.size}.")
            for (it in banks) {
                if (walkUntilReached(it).await()) {
                    return it
                }
                bot.log("Bank $it inaccessible.")
                delay()
            }
        }
        // Travel home, then use home bank.
        bot.log("Trying to travel home for a bank.")
        if (travelTo(Zone.HOME)) {
            return handler.banking.homeBank()
        }
        bot.log("Cannot travel or find a bank.")
        return null
    }

    /**
     * A blocking action that forces the [Bot] to travel to [zone] by any means. Returns `false` if the bot cannot
     * access the zone by teleportation (due to lack of levels/items) or pathing.
     *
     * @param The zone to travel to.
     * @return `true` if the bot travelled successfully.
     */
    suspend fun travelTo(zone: Zone): Boolean {
        // TODO If bot in minigame, return false.

        // We're already inside the zone we want to go to.
        if (zone.inside(bot)) {
            return true
        }

        // Leave the wilderness first.
        bot.log("Travelling to $zone")
        if (bot.wildernessLevel >= 20 && zone.safe) {
            bot.log("Leaving wilderness first.")
            if (!leaveWilderness()) {
                return false
            }
        }
        val dest = zone.anchor
        for (strategy in zone.travel) {
            bot.log("Attempting strategy ${strategy.javaClass.simpleName}.")
            if (strategy.canTravel(bot, handler, dest)) {
                return strategy.travel(bot, handler, dest)
            }
        }
        bot.log("No strategies found.")
        return false
    }
}