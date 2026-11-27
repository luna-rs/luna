package api.bot.script

import api.predef.*
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.memory.BotDiaryMemory.Standing

/**
 * Where the bot's account stands now: its experience and levels in all skills together and the guide worth of
 * everything it owns. The diary measures a session between two of these.
 */
fun Bot.standing(): Standing {
    var experience = 0L
    var levels = 0
    for (skill in skills) {
        experience += skill.experience.toLong()
        levels += skill.staticLevel
    }
    val wealth = itemTracker.entrySet().sumOf {
        val price = world.economy.getRealPrice(it.element)
        if (price.isFinite() && price > 0.0) price * it.count else 0.0
    }
    return Standing(experience, levels, wealth.toLong())
}
