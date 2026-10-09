package engine.bot.coordinator

import api.predef.*
import game.bot.scripts.combat.PkBotScript
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator
import java.util.WeakHashMap
import kotlin.time.Duration.Companion.minutes

/** Selects bounded melee PK sessions only for bots that already own the required equipment and food. */
object PkingCoordinator : BotCoordinator {
    private val nextSession = WeakHashMap<Bot, Long>()

    override fun accept(bot: Bot) {
        val now = System.currentTimeMillis()
        if (now < (nextSession[bot] ?: 0) || !PkBotScript.isEligible(bot) || bot.combat.inCombat()) return
        nextSession[bot] = now + 5.minutes.inWholeMilliseconds
        bot.scriptStack.push(PkBotScript(bot, rand(15, 30).minutes))
    }
}
