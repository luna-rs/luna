package api.bot.scripts

import api.bot.BotScript
import api.bot.Suspendable.delay
import api.bot.Suspendable.maybe
import api.bot.Suspendable.waitFor
import api.bot.SuspendableCondition
import api.bot.Zone
import api.bot.scripts.IdleBotScript.InputData
import api.predef.*
import api.predef.ext.*
import com.google.common.base.Stopwatch
import com.google.common.collect.ImmutableList
import io.luna.game.model.mob.MobFollowAction
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.script.BotScriptSnapshot
import io.luna.game.model.mob.bot.speech.BotSpeechContext
import io.luna.game.model.mob.bot.speech.BotSpeechType
import io.luna.util.RandomUtils
import io.luna.util.Rational
import world.player.combat.Combat.inWilderness
import world.player.settings.emote.Emote
import kotlin.time.Duration.Companion.hours
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
            delay(1.seconds, 3.seconds)
            if (bot.inWilderness()) {
                bot.status("Inside wilderness, moving to safety.")
                handler.movement.leaveWilderness()
                continue
            }
            handler.widgets.clickRunning(false)
            if (bot.inventory.isFull) {
                bot.status("Banking items.")
                handler.findBankAndDepositAll()
                continue
            }
            nextState()
            when (data.state) {
                State.STANDING -> doStanding()
                State.FOLLOW -> doFollow()
                State.WALKING -> doWalking()
                State.TELEPORT -> doTeleport()
                else -> delay(1.seconds, 5.seconds)
            }
            data.state = null
            delay(1.seconds, 5.seconds)
        }
    }

    override fun speechContext(): BotSpeechContext =
        when(data.state) {
            State.FOLLOW -> BotSpeechContext(BotSpeechType.BORED, Rational.VERY_COMMON)
            else -> BotSpeechContext(BotSpeechType.BORED, Rational.RARE)
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
                intelligence.speechStack.poke()
                bot.status("Might speak soon.")
            })
        else randomActions()
        delay(1.seconds, 10.seconds)
    }

    private suspend fun doFollow() {
        bot.status("Looking for someone to follow.")
        var following = false
        for (player in bot.localPlayers) {
            if (!player.walking.isEmpty) {
                bot.status("Trying to follow $player.")
                // Search through local players, follow a random moving player.
                if (handler.interactions.interact(3, player) && bot.actions.contains(MobFollowAction::class)) {
                    bot.status("Following $player.")
                    following = true
                    break
                }
            }
        }
        if (following) {
            maybe(COMMON) { intelligence.speechStack.pop() }

            // Follow them for a little or until you enter the wilderness.
            val followTime = rand(5, 120)
            waitFor(followTime.minutes) { bot.inWilderness() || !bot.actions.contains(MobFollowAction::class) }

            bot.status("No longer following anyone.")
            delay(1.seconds, 3.seconds)
        }
    }

    private suspend fun doWalking() {
        // Doesn't really matter if we reach or not.
        val zone = RandomUtils.random(Zone.SAFE_ZONES)
        bot.status("Walking to $zone.")
        handler.movement.travelTo(zone)
        delay(1.seconds)
    }

    private suspend fun doTeleport() {
        // Teleport back to ::home and wait for next state.
        bot.status("Teleporting home.")
        if (Zone.HOME.inside(bot)) {
            delay(5.seconds)
            return
        }
        output.sendCommand("home")
        delay(5.seconds, 10.seconds)
        randomActions()
    }


    /**
     * Make the bot perform one of many random idle actions.
     */
    private suspend fun randomActions() {

        // Maybe idle at bank.
        if (maybe(UNCOMMON) {
                val bank = handler.banking.findNearestBank()
                if (bank != null) {
                    bot.status("I'm idling at the bank.")
                    handler.interactions.interact(2, bank)
                    delay(1.minutes, 10.minutes)
                }
            })
            return

        // Otherwise, do some emotes.
        if (maybe(COMMON) {
                bot.status("I'm performing emotes.")
                val emote = RandomUtils.random(Emote.ALL)
                bot.animation(Animation(emote.id))
                delay(5.seconds, 15.seconds)
            })
            return

        // Otherwise, maybe stand around and say something at ::home.
        if (maybe(VERY_UNCOMMON) {
                bot.status("I'm definitely going to say something soon.")
                intelligence.speechStack.pop()
                delay(10.seconds, 30.seconds)
            })
            return
    }
}