package api.bot.scripts

import api.bot.BotScript
import api.bot.SuspendableCondition
import api.bot.Zone
import api.bot.scripts.IdleBotScript.IdleInput
import api.predef.*
import com.google.common.base.Stopwatch
import com.google.common.collect.ImmutableList
import io.luna.game.model.mob.MobFollowAction
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.ai.BotSpeechContext
import io.luna.game.model.mob.bot.ai.BotSpeechType
import io.luna.util.RandomUtils
import io.luna.util.Rational
import kotlinx.coroutines.delay
import world.player.combat.Combat.inWilderness

/**
 * A [BotScript] that makes a [Bot] idle for a random period based on [duration]. An idle bot will stand around,
 * walk around, and occasionally teleport to ::home.
 *
 * @author lare96
 */
class IdleBotScript(bot: Bot, var data: IdleInput) : BotScript<IdleInput>(bot) {

    /**
     * The persistence data.
     */
    data class IdleInput(val durationMinutes: Long = rand().nextLong(30, 120))

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

    /**
     * The current idle state.
     */
    private var state: State? = null

    override suspend fun run() {

        // Determines the next delay after the state-machine completes.
        var nextDelay: Long

        // Run script until our idle timer completes.
        timer.start()
        while (timer.elapsed().toMinutes() < data.durationMinutes) {
            nextDelay = 10_000L
            if (bot.inWilderness()) {
                handler.movement.travelTo(Zone.EDGEVILLE)
                continue
            }
            state = RandomUtils.random(State.ALL) // Select a random state.
            when (state) {
                // Do nothing.
                State.STANDING -> {
                    if (RandomUtils.rollSuccess(Rational.COMMON)) {
                        // Make the bot more likely to speak soon.
                        intelligence.speech.poke()
                    }
                    delay(rand().nextLong(1000, 10_000))
                }

                State.FOLLOW -> {
                    var following = false
                    for (player in bot.localPlayers) {
                        if (!player.walking.isEmpty) {
                            // Search through local players, follow a random moving player.
                            if (handler.interactions.interact(3, player) &&
                                bot.actions.contains(MobFollowAction::class.java)
                            ) {
                                following = true
                                break
                            }
                        }
                    }
                    if (following) {
                        // Follow them for a little or until you enter the wilderness.
                        SuspendableCondition({ bot.inWilderness() || !bot.actions.contains(MobFollowAction::class.java) },
                                             timeoutSeconds = rand().nextLong(320, 640)).submit().await()
                        nextDelay = 500L
                    }
                }

                // Start walking to a random zone, then wait for next state.
                State.WALKING -> {
                    // Doesn't really matter if we reach or not.
                    handler.movement.travelTo(RandomUtils.random(Zone.SAFE_ZONES))
                    delay(1000)
                }

                // Teleport back to ::home and wait for next state.
                State.TELEPORT -> {
                    if (Zone.HOME.inside(bot)) {
                        delay(5000)
                        return
                    }
                    output.sendCommand("home")
                    delay(rand().nextLong(5000, 10_000))

                    // Maybe idle at bank.
                    if (RandomUtils.rollSuccess(Rational.UNCOMMON)) {
                        val bank = handler.banking.findNearestBank()
                        if (bank != null) {
                            handler.interactions.interact(2, bank)
                            delay(rand().nextLong(30_000, 120_000))
                            return
                        }
                    }

                    // Otherwise, maybe stand around and say something at ::home.
                    if (RandomUtils.rollSuccess(Rational.UNCOMMON)) {
                        intelligence.speech.pop()
                        delay(rand().nextLong(10_000, 60_000))
                        return
                    }
                }

                else -> {
                    delay(rand().nextLong(1000, 10_000))
                }
            }
            delay(nextDelay)
        }
    }

    override fun speechContext(): BotSpeechContext =
        BotSpeechContext(BotSpeechType.BORED, Rational.VERY_RARE)

    override fun load(snapshot: IdleInput) {
        // Load our previous snapshot so we can resume the script.
        data = snapshot
    }

    override fun snapshot(): IdleInput {
        // Save the remaining idle time needed.
        val remaining = data.durationMinutes - timer.elapsed().toMinutes()
        return if (remaining < 1) IdleInput(1) else IdleInput(remaining)
    }
}