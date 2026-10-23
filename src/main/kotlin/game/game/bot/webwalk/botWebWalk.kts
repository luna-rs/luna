package game.bot.webwalk

import api.bot.script.DynamicBotScript
import api.bot.webwalk.plan.BankAccess
import api.bot.webwalk.plan.WebWalkPlan
import api.bot.webwalk.walk.TripPlan
import api.bot.webwalk.walk.WebWalkResult
import api.bot.webwalk.walk.WebWalker
import api.bot.zone.SubZone
import api.bot.zone.Zone
import api.predef.*
import api.predef.ext.*
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.BotLogManager
import io.luna.game.model.mob.bot.brain.BotBrain
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator

/**
 * How often, in ticks, the player is moved to the bot while it walks.
 */
val FOLLOW_TICKS = 3

/**
 * The help for the command.
 */
val BOT_WEBWALK_USAGE = listOf(
    "::botwebwalk <bot> <x> <y> [z]  walks a bot to a position.",
    "::botwebwalk <bot> me  walks it to where you are.",
    "::botwebwalk <bot> <place>  walks it to a zone or sub-zone, such as varrock or wizards tower.",
    "::botwebwalk <bot> bank  walks it to the nearest bank.",
    "End with bank=none, bank=coins or bank=all (the default) to set how much of its bank the trip may withdraw.",
    "If no bot has that name, a temporary one is made where you stand. You are moved to the bot every $FOLLOW_TICKS ticks.")

/**
 * A command that walks one bot to a destination with the web-walker, to test it, and moves the player to the bot every
 * [FOLLOW_TICKS] ticks so that the walk is easy to follow. See [BOT_WEBWALK_USAGE].
 *
 * If no bot with that name is online, a temporary one without a brain is made at the position of the player, so that only
 * the walk is happening. No other bot is touched, and the web-walker is not used by anything else.
 */
cmd("botwebwalk", RIGHTS_DEV) {
    val access = args.lastOrNull()?.takeIf { it.startsWith("bank=", true) }?.let { word ->
        BankAccess.entries.firstOrNull { it.name.equals(word.substringAfter('='), true) }
    }
    val words = if (access != null) args.dropLast(1) else args.toList()
    val toBank = words.size == 2 && words[1].equals("bank", true)
    val destination = if (words.size < 2 || toBank) null else parseDestination(plr, words.drop(1))
    if (destination == null && !toBank) {
        if (words.size >= 2) {
            plr.sendMessage("I don't know where '${words.drop(1).joinToString(" ")}' is.")
        }
        BOT_WEBWALK_USAGE.forEach { plr.sendMessage(it) }
        return@cmd
    }
    val name = words[0]
    val bank = access ?: BankAccess.ALL

    val online = world.players.firstOrNull { it is Bot && it.username.equals(name, true) } as Bot?
    if (online != null) {
        startWebWalk(plr, online, destination, bank)
        return@cmd
    }

    plr.sendMessage("No bot named $name is online, making a temporary one here.")
    val bot = Bot.Builder(ctx).setTemporary().setUsername(name).setBrain(object : BotBrain() {
        override fun process(bot: Bot): BotCoordinator? = null
    }).build()
    bot.speechStack.setDisableAll(true)
    bot.logManager.setStreamType(BotLogManager.BotStreamType.ALL)
    bot.login().thenRun {
        bot.move(plr.position)
        world.scheduleOnce(3) { startWebWalk(plr, bot, destination, bank) }
    }
}

/**
 * Works out where the player means: `me`, `<x> <y> [z]`, or the name of a [Zone] or [SubZone] with its words separated by
 * spaces. Returns `null` if it is none of those.
 */
fun parseDestination(plr: Player, words: List<String>): Position? {
    val first = words.first()
    if (first.equals("me", true)) {
        return plr.position
    }
    if (first.toIntOrNull() != null) {
        val x = first.toInt()
        val y = words.getOrNull(1)?.toIntOrNull() ?: return null
        val z = words.getOrNull(2)?.toIntOrNull() ?: 0
        return if (x >= 0 && y >= 0 && z in 0..3) Position(x, y, z) else null
    }
    val key = words.joinToString("_").uppercase()
    return Zone.entries.firstOrNull { it.name == key }?.anchor ?: SubZone.entries.firstOrNull { it.name == key }?.inside
}

/**
 * Plans a trip for [bot], tells [plr] about it, sets the bot on its way, and moves [plr] to the bot every [FOLLOW_TICKS]
 * ticks until it is done. The trip is to [destination], or to the nearest bank if that is `null`. A trip to a destination
 * may go by the bank first, with as much of it as [bank] allows, and what it would withdraw is told too.
 */
fun startWebWalk(plr: Player, bot: Bot, destination: Position?, bank: BankAccess) {
    if (destination == null) {
        val plan = try {
            WebWalker.planToNearestBank(bot)
        } catch (e: Exception) {
            plr.sendMessage("Could not plan a trip: $e")
            logger.error("Could not plan a web walk!", e)
            return
        }
        if (plan == null) {
            plr.sendMessage("${bot.username} has no way from ${bot.position} to the nearest bank.")
            return
        }
        tell(plr, "${bot.username}: from ${bot.position} to the nearest bank,", plan)
        walk(plr, bot) { WebWalker.webWalkToNearestBank(bot) }
        return
    }

    val start = bot.position
    WebWalker.planTrip(bot, destination, bank).whenCompleteAsync({ trip, e ->
        if (e != null) {
            plr.sendMessage("Could not plan a trip: $e")
            logger.error("Could not plan a web walk!", e)
            return@whenCompleteAsync
        }
        when (trip) {
            is TripPlan.NoTrip -> {
                plr.sendMessage("${bot.username}: ${trip.reason}")
                return@whenCompleteAsync
            }
            is TripPlan.Direct -> {
                tell(plr, "${bot.username}: from $start to $destination,", trip.plan)
                trip.turnedDown?.let { plr.sendMessage("  not by the bank ${it.bank.id}: ${compare(it)}") }
            }
            is TripPlan.ViaBank -> {
                plr.sendMessage("${bot.username}: from $start to $destination by the bank ${trip.bank.id} (${trip.access}), " +
                                        compare(trip))
                val withdraws = trip.withdrawal.joinToString { "${it.amount} x ${itemName(it.id)}" }
                plr.sendMessage("  withdraws: ${withdraws.ifEmpty { "nothing" }}")
                tell(plr, "  to the bank:", trip.toBank)
                tell(plr, "  from the bank:", trip.fromBank)
            }
        }
        walk(plr, bot) { bot.actionHandler.webWalk(destination, bank = bank) }
    }, gameService.gameExecutor)
}

/**
 * Tells [plr] about a [plan]: its legs, cost and teleports, after [title].
 */
fun tell(plr: Player, title: String, plan: WebWalkPlan) {
    plr.sendMessage("$title ${plan.legs.size} legs, cost ${"%.1f".format(plan.cost)}.")
    plan.legs.groupBy { it.type }.forEach { (type, legs) -> plr.sendMessage("    $type x${legs.size}") }
    plan.legs.mapNotNull { it.teleport?.id }.takeIf { it.isNotEmpty() }?.let { plr.sendMessage("    teleports: ${it.joinToString()}") }
}

/**
 * Describes how a trip by the bank compares with the trip without it.
 */
fun compare(trip: TripPlan.ViaBank): String {
    val direct = trip.comparedWith?.let { "%.1f".format(it.cost) } ?: "none"
    return "cost ${"%.1f".format(trip.cost)} by the bank vs $direct direct."
}

/**
 * Sets [bot] on its way with [walk], tells [plr] how it went, and moves [plr] to the bot every [FOLLOW_TICKS] ticks until it
 * is done.
 */
fun walk(plr: Player, bot: Bot, walk: suspend () -> WebWalkResult) {
    var finished = false
    bot.scriptStack.pushHead(object : DynamicBotScript(bot) {
        override suspend fun run(): Boolean {
            val result = walk()
            finished = true
            // The last teleport or climb may have been less than a follow ago, so the player catches up one last time.
            if (plr.state == EntityState.ACTIVE && bot.state == EntityState.ACTIVE) {
                plr.move(bot.position)
            }
            plr.sendMessage("${bot.username}: ${if (result.success) "done" else "FAILED"}. ${result.message}")
            if (result.withdrawn.isNotEmpty()) {
                plr.sendMessage("  withdrew: ${result.withdrawn.joinToString { "${it.amount} x ${itemName(it.id)}" }}")
            }
            return true
        }
    })
    follow(plr, bot) { finished }
}

/**
 * Moves [plr] to [bot] now and every [FOLLOW_TICKS] ticks after, until [finished], or the player or the bot is gone.
 */
fun follow(plr: Player, bot: Bot, finished: () -> Boolean) {
    plr.move(bot.position)
    world.schedule(FOLLOW_TICKS) {
        if (finished() || plr.state != EntityState.ACTIVE || bot.state != EntityState.ACTIVE) {
            it.cancel()
            return@schedule
        }
        if (plr.position != bot.position) {
            plr.move(bot.position)
        }
    }
}
