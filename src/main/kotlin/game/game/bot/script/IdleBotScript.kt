package game.bot.script

import api.bot.BotScript
import api.bot.Suspendable.delay
import api.bot.Suspendable.maybe
import api.bot.Suspendable.naturalDecisionDelay
import api.bot.Suspendable.naturalDelay
import api.bot.Suspendable.waitFor
import api.controller.Controllers.inWilderness
import api.predef.*
import api.predef.ext.*
import com.google.common.base.Stopwatch
import com.google.common.collect.ImmutableList
import engine.interaction.follow.MobFollowAction
import game.bot.script.IdleBotScript.InputData
import engine.widget.settings.Emote
import io.luna.Luna
import io.luna.game.model.Position
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.script.BotScriptSnapshot
import io.luna.util.RandomUtils
import kotlin.time.Duration.Companion.minutes

/**
 * A [BotScript] that makes a [Bot] idle for a random period based on [duration]. An idle bot will stand around,
 * walk around, and occasionally teleport to ::home.
 *
 * @author lare96
 */
class IdleBotScript(bot: Bot, var data: InputData) : BotScript<InputData>(bot) {

    /**
     * The persistence data.
     */
    data class InputData(val durationMinutes: Long = rand().nextLong(30, 120),
                         var state: State? = null)

    /**
     * All possible idle states a bot can be in.
     */
    enum class State {

        /**
         * The bot will just stand around.
         */
        STANDING,

        /**
         * The bot will start walking to a random zone.
         */
        WALKING,

        /**
         * The bot will start to follow a nearby player.
         */
        FOLLOW,

        /**
         * The bot will teleport to ::home.
         */
        TELEPORT;

        companion object {

            /**
             * A list of all states.
             */
            val ALL = ImmutableList.copyOf(values())
        }
    }

    /**
     * The timer for being idle.
     */
    private val timer = Stopwatch.createUnstarted()

    override suspend fun run() {
        // Run script until our idle timer completes.
        timer.start()
        while (timer.elapsed().toMinutes() < data.durationMinutes) {
            try {
                handler.widgets.clickRunning(false)
                bot.naturalDelay()
                nextState()
                when (data.state) {
                    State.STANDING -> randomActions()
                    State.FOLLOW -> doFollow()
                    State.TELEPORT -> doTeleport()
                    else -> {} // Do nothing.

                }
            } finally {
                data.state = null
            }
            bot.naturalDecisionDelay()
        }
        timer.reset()
    }

    override fun snapshot(): InputData {
        // Save the remaining idle time needed.
        val remaining = data.durationMinutes - timer.elapsed().toMinutes()
        return if (remaining < 1) InputData(1, data.state) else InputData(remaining, data.state)
    }

    /**
     * Sets the next state.
     */
    private fun nextState() {
        if (data.state == null) {
            data.state = RandomUtils.random(State.ALL)
        }
    }

    /**
     * Makes the bot follow a random nearby player or bot.
     */
    private suspend fun doFollow() {
        bot.log("Looking for someone to follow.")
        var following = false
        for (player in bot.localHumans) {
            if (!player.walking.isEmpty) {
                bot.log("Trying to follow $player.")
                // Search through local players, follow a random moving player.
                if (handler.interactions.interact(3, player) && bot.actions.contains(MobFollowAction::class)) {
                    bot.log("Following $player.")
                    following = true
                    break
                }
            }
        }
        if (following) {
            maybe(COMMON) { bot.output.chat("Can I follow you around?") }

            // Follow them for a little or until you enter the wilderness.
            val followTime = rand(5, 120)
            waitFor(followTime.minutes) { bot.inWilderness() || !bot.actions.contains(MobFollowAction::class) }

            bot.log("No longer following anyone.")
        }
        bot.naturalDecisionDelay()
    }

    /**
     * Makes the bot teleport home and do [randomActions].
     */
    private suspend fun doTeleport() {
        // Teleport back to ::home and wait for next state.
        if (bot.position.isWithinDistance(Luna.settings().game().startingPosition(), Position.VIEWING_DISTANCE)) {
            return
        }
        bot.log("Teleporting home.")
        output.sendCommand("home")
        bot.naturalDecisionDelay()
        randomActions()
    }

    /**
     * Make the bot perform one of many random idle actions.
     */
    private suspend fun randomActions() {

        // Maybe idle.
        if (maybe(UNCOMMON) {
                bot.log("I'm idling.")
                delay(1.minutes, 10.minutes)
            })
            return

        // Otherwise, do some emotes.
        if (maybe(COMMON) {
                bot.log("I'm performing emotes.")
                val emote = RandomUtils.random(Emote.ALL)
                bot.animation(Animation(emote.id))
                bot.naturalDecisionDelay()
            })
            return

        // Otherwise, maybe stand around and say something.
        if (maybe(VERY_UNCOMMON) {
                bot.output.chat("I'm bored!")
                bot.naturalDecisionDelay()
            })
            return
    }
}