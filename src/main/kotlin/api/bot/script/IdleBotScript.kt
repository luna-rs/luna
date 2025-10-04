package api.bot.script

import api.bot.BotScript
import api.bot.Suspendable.delay
import api.bot.Suspendable.maybe
import api.bot.Suspendable.naturalDecisionDelay
import api.bot.Suspendable.naturalDelay
import api.bot.Suspendable.waitFor
import api.bot.script.IdleBotScript.InputData
import api.bot.zone.Zone
import api.predef.*
import api.predef.ext.*
import com.google.common.base.Stopwatch
import com.google.common.collect.ImmutableList
import io.luna.game.model.mob.MobFollowAction
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.script.BotScriptSnapshot
import io.luna.util.RandomUtils
import world.player.combat.Combat.inWilderness
import world.player.settings.emote.Emote
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
            if (bot.inWilderness()) {
                bot.log("Inside wilderness, moving to safety.")
                handler.movement.leaveWilderness()
                continue
            }
            handler.widgets.clickRunning(false)
            naturalDelay()
            if (bot.inventory.isFull) {
                bot.log("Banking items.")
                handler.travelToBankDepositAll()
                continue
            }
            nextState()
            when (data.state) {
                State.STANDING -> doStanding()
                State.FOLLOW -> doFollow()
                State.WALKING -> doWalking()
                State.TELEPORT -> doTeleport()
                else -> {} // Do nothing.

            }
            data.state = null
            naturalDecisionDelay()
        }
    }

    override fun load(snapshot: BotScriptSnapshot<InputData>) {
        // Load our previous snapshot so we can resume the script.
        data = snapshot.data
    }

    override fun snapshot(): InputData {
        // Save the remaining idle time needed.
        val remaining = data.durationMinutes - timer.elapsed().toMinutes()
        return if (remaining < 1) InputData(1, data.state) else InputData(remaining, data.state)
    }

    private fun nextState() {
        if (data.state == null) {
            data.state = RandomUtils.random(State.ALL)
        }
    }

    private suspend fun doStanding() {
        if (maybe(COMMON) {
                // Make the bot more likely to speak soon.
                intelligence.speechStack.pushFiller()
            })
            return
        randomActions()
    }

    private suspend fun doFollow() {
        // if another idle bot in social mode, follow each other (dance)
        bot.log("Looking for someone to follow.")
        var following = false
        for (player in bot.localPlayers) {
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
            maybe(COMMON) { intelligence.speechStack.pushFiller() }

            // Follow them for a little or until you enter the wilderness.
            val followTime = rand(5, 120)
            waitFor(followTime.minutes) { bot.inWilderness() || !bot.actions.contains(MobFollowAction::class) }

            bot.log("No longer following anyone.")
        }
    }

    private suspend fun doWalking() {
        // Doesn't really matter if we reach or not.
        val zone = RandomUtils.random(Zone.SAFE_ZONES)
        handler.movement.travelTo(zone)
    }

    private suspend fun doTeleport() {
        // Teleport back to ::home and wait for next state.
        bot.log("Teleporting home.")
        if (Zone.HOME.inside(bot)) {
            delay(5.seconds)
            return
        }
        output.sendCommand("home")
        naturalDecisionDelay()
        randomActions()
    }


    /**
     * Make the bot perform one of many random idle actions.
     */
    private suspend fun randomActions() {

        // Maybe idle at bank.
        if (maybe(UNCOMMON) {
                if (handler.travelToBankOpen()) {
                    bot.log("I'm idling at the bank.")
                    delay(1.minutes, 10.minutes)
                }
            })
            return

        // Otherwise, do some emotes.
        if (maybe(COMMON) {
                bot.log("I'm performing emotes.")
                val emote = RandomUtils.random(Emote.ALL)
                bot.animation(Animation(emote.id))
                naturalDecisionDelay()
            })
            return

        // Otherwise, maybe stand around and say something at ::home.
        if (maybe(VERY_UNCOMMON) {
                intelligence.speechStack.pushFiller()
                naturalDecisionDelay()
            })
            return
    }
}