package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.Suspendable.waitFor
import api.bot.script.BotScript.ActionSafety
import game.content.randomEvents.CandlelightRandomEvent
import game.content.randomEvents.RandomEvents
import io.luna.game.model.mob.bot.Bot
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Solves the visible native Candlelight puzzle with ordinary Pete interactions and lighter-panel button packets.
 * The existing random-event reflex keeps ordinary work paused through the reaction delay and mandatory church puzzle.
 * Low intelligence and dexterity can misread a burnt candle, invoking the source gust and restart consequences.
 *
 * @author lare96
 */
internal object CandlelightBotSupport {
    /**
     * Performs one verified native action without granting rewards, moving the bot directly or changing puzzle state.
     * Exact native dialogue-page ownership permits speech continuation while the active reflex blocks ordinary reflexes.
     */
    suspend fun step(bot: Bot, event: CandlelightRandomEvent, random: Random) {
        fun safe() = RandomEvents.encounter(bot) === event && event.awaitingResponse && ActionSafety.NON_COMBAT.allows(bot)
        if (!safe()) return
        if (event.ownsDialoguePage()) {
            bot.output.sendContinueDialogue()
            delay(1.seconds)
            return
        }
        if (!event.panelOpen()) {
            if (!bot.overlays.hasWindow()) bot.actionHandler.interactions.interact(1, event.npc) { safe() && !bot.overlays.hasWindow() }
            delay(1.seconds)
            return
        }
        val unlit = CandlelightRandomEvent.PILLARS.indices.filter { event.state(it) == CandlelightRandomEvent.UNLIT }
        if (!event.taperActive() || unlit.isEmpty()) {
            bot.output.clickButton(CandlelightRandomEvent.CLOSE)
            waitFor(2.seconds) { !event.panelOpen() || !safe() }
            return
        }
        val mistake = (1.0 - bot.personality.intelligence.coerceIn(0.0, 1.0)) *
            (1.0 - bot.personality.dexterity.coerceIn(0.0, 1.0)) * 0.15
        val burnt = CandlelightRandomEvent.PILLARS.indices.filter { event.state(it) == CandlelightRandomEvent.BURNT }
        val index = if (burnt.isNotEmpty() && random.nextDouble() < mistake) burnt.random(random) else unlit.first()
        val target = CandlelightRandomEvent.PILLARS[index].translate(3, 0)
        val before = bot.position
        val button = when {
            before.x > target.x -> CandlelightRandomEvent.UP
            before.x < target.x -> CandlelightRandomEvent.DOWN
            before.y > target.y -> CandlelightRandomEvent.LEFT
            before.y < target.y -> CandlelightRandomEvent.RIGHT
            else -> CandlelightRandomEvent.LIGHT
        }
        val state = event.state(index)
        if (!safe() || !event.panelOpen()) return
        bot.output.clickButton(button)
        waitFor(2.seconds) { !safe() || bot.position != before || event.state(index) != state || !event.taperActive() }
        delay(1.seconds)
    }
}
