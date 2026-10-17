package game.bot.webwalk

import api.bot.script.DynamicBotScript
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
    "If no bot has that name, a temporary one is made where you stand. You are moved to the bot every $FOLLOW_TICKS ticks.")

/**
 * A command that walks one bot to a destination with the web-walker, to test it, and moves the player to the bot every
 * [FOLLOW_TICKS] ticks so that the walk is easy to follow. See [BOT_WEBWALK_USAGE].
 *
 * If no bot with that name is online, a temporary one without a brain is made at the position of the player, so that only
 * the walk is happening. No other bot is touched, and the web-walker is not used by anything else.
 */
cmd("botwebwalk", RIGHTS_DEV) {
    val toBank = args.size == 2 && args[1].equals("bank", true)
    val destination = if (args.size < 2 || toBank) null else parseDestination(plr, args.drop(1))
    if (destination == null && !toBank) {
        if (args.size >= 2) {
            plr.sendMessage("I don't know where '${args.drop(1).joinToString(" ")}' is.")
        }
        BOT_WEBWALK_USAGE.forEach { plr.sendMessage(it) }
        return@cmd
    }
    val name = args[0]

    val online = world.players.firstOrNull { it is Bot && it.username.equals(name, true) } as Bot?
    if (online != null) {
        startWebWalk(plr, online, destination)
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
        world.scheduleOnce(3) { startWebWalk(plr, bot, destination) }
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
 * ticks until it is done. The trip is to [destination], or to the nearest bank if that is `null`.
 */
fun startWebWalk(plr: Player, bot: Bot, destination: Position?) {
    val plan = try {
        if (destination == null) WebWalker.planToNearestBank(bot) else WebWalker.plan(bot, destination)
    } catch (e: Exception) {
        plr.sendMessage("Could not plan a trip: $e")
        logger.error("Could not plan a web walk!", e)
        return
    }
    val target = destination?.toString() ?: "the nearest bank"
    if (plan == null) {
        plr.sendMessage("${bot.username} has no way from ${bot.position} to $target.")
        return
    }
    plr.sendMessage("${bot.username}: ${plan.legs.size} legs, cost ${"%.1f".format(plan.cost)}, from ${bot.position} to $target.")
    plan.legs.groupBy { it.type }.forEach { (type, legs) -> plr.sendMessage("  $type x${legs.size}") }
    plan.legs.mapNotNull { it.teleport?.id }.takeIf { it.isNotEmpty() }?.let { plr.sendMessage("  teleports: ${it.joinToString()}") }

    var finished = false
    bot.scriptStack.pushHead(object : DynamicBotScript(bot) {
        override suspend fun run(): Boolean {
            val result = if (destination == null) WebWalker.webWalkToNearestBank(bot) else handler.webWalk(destination)
            finished = true
            // The last teleport or climb may have been less than a follow ago, so the player catches up one last time.
            if (plr.state == EntityState.ACTIVE && bot.state == EntityState.ACTIVE) {
                plr.move(bot.position)
            }
            plr.sendMessage("${bot.username}: ${if (result.success) "done" else "FAILED"}. ${result.message}")
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
