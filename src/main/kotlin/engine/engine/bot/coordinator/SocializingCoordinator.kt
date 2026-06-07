package engine.bot.coordinator

import api.bot.script.DynamicBotScript
import api.bot.zone.Zone
import api.predef.*
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator

/**
 * Coordinates temporary social behaviour for bots that are not committed to a specific skill, combat task, or
 * economic activity.
 *
 * Socializing bots are meant to make the world feel more alive by wandering between populated areas, idling around
 * banks, chatting with other bots, reacting to players, attending drop parties, or becoming available for social-only
 * interactions such as scams.
 *
 * @author lare96
 */
object SocializingCoordinator : BotCoordinator {

    /**
     * Assigns a temporary social script to the bot.
     *
     * The current implementation is intentionally simple: it pushes a short-lived script that travels to a random zone
     * and then completes. This gives the coordinator a working baseline until richer social behaviours are implemented.
     *
     * Future social scripts may include:
     *
     * - Random navigation between cities or popular gathering areas.
     * - Dancing or emote-based interaction with nearby social bots.
     * - Conversation with nearby bots using conversation pools.
     * - Reactions to impressive nearby players while not already in a conversation.
     * - Social-only scamming or being the victim of a social scam.
     * - Drop party attendance or kind-bot item donation behaviour.
     * - Cleaning junk out of bank.
     */
    override fun accept(bot: Bot) {
        bot.scriptStack.push(object : DynamicBotScript(bot) {
            override suspend fun run(): Boolean {
                repeat(rand(1, 5)) {
                    bot.log("Travelling to a random zone.")
                    handler.travelTo(Zone.entries.random())
                    bot.log("Travel completed. Exiting temporary social script.")
                }
                return true
            }
        })
    }

    /*

    package api.bot.scripts

import api.bot.script.BotScript
import io.luna.game.model.mob.bot.Bot

/**
 * A [BotScript] that makes a [Bot] idle for a random period based on [duration]. An idle bot will stand around,
 * walk around, and occasionally teleport to ::home.
 *
 * @author lare96
 */
class IdleBotScript(bot: Bot, val durationMinutes: Int) /*: DynamicBotScript(bot) {

    // TODO chance to start a trading bot script, a scamming bot script, chance to be scammed, chance to react to impressive player
    //  otherwise just bank stand
    // todo generate a list of viewable players and go through the list slowly (one player every 1-2s) then scan for different events
    // SCAMMER, TRADING, IMPRESSED
    // todo chance to follow players that we LOVE while bankstanding. if they arent already following someone
    // if they are idle too, check if someone is following you. if you like them back, follow them as well and dance for 5 minutes to 1 hr

// TODO delete and migrate into social coordinator

    companion object {



    }

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
        FOLLOW;

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

    override suspend fun init(resumed: Boolean): Boolean {
        if (!timer.isRunning) {
            timer.start()
        }
        return false
    }

    override fun paused() {
        if (timer.isRunning) {
            timer.stop()
        }
    }

    override suspend fun run(): Boolean {
        if (timer.elapsed().toMinutes() >= durationMinutes) {
            // Stop script once our idle timer completes.
            return true
        }
        // todo@0.5.0 check for scammers nearby and attempt to 'fall' for it. or potentially start scamming
        try {
            if (bot.inWilderness()) {
                bot.log("Inside wilderness, moving to safety.")
                handler.combat.fleeWilderness()
                return false
            }
            bot.walking.isRunning = true
            bot.naturalDelay()
            if (bot.inventory.isFull) {
                bot.log("Banking items.")
                handler.banking.travelToBankDepositAll()
                return false
            }
            nextState()
            when (state) {
                State.STANDING -> doStanding()
                State.FOLLOW -> doFollow()
                State.WALKING -> doWalking()
                else -> {} // Do nothing.

            }
        } finally {
            state = null
        }
        bot.naturalDecisionDelay()
        return false
    }

    private fun nextState() {
        if (state == null) {
            state = RandomUtils.random(State.ALL)
        }
    }

    private suspend fun doStanding() {
        handler.travelTo(SubZone.HOME)
        // Either act like we're banking, or stand around one of the various anchor points.
        if (randBoolean()) {
            handler.interactions.interact(2, handler.banking.homeBank())
        } else {
            bot.navigator.navigate(HOME_ANCHOR_POINTS.random(), true).await()
        }
        delay(5.seconds, 10.seconds)
        bot.overlays.closeWindows()
        if (maybe(COMMON) {
                // Make the bot more likely to speak soon.
                bot.speechStack.pushFiller()
            })
            return
        var loops = 5 + if (bot.personality.isSocial) 2 else 0
        while (--loops >= 0) {
            // todo@0.5.0 check here for potential scams nearby
            randomActions()
            delay(1.minutes, 3.minutes)
        }

    }

    private suspend fun doFollow() {
        // TODO@1.0 If another idle bot in social mode, follow each other (dance).
        bot.log("Looking for someone to follow.")
        var following: Player? = null
        for (player in world.locator.findViewablePlayers(bot)) {
            if (bot != player && !player.walking.isEmpty) {
                bot.log("Trying to follow $player.")
                // Search through local players, follow a random moving player.
                if (handler.interactions.interact(3, player) && bot.navigator.currentTarget == player) {
                    bot.log("Following $player.")
                    following = player
                    break
                }
            }
        }
        if (following != null) {
            // Follow them for a little or until you enter the wilderness.
            val followTime = rand(5, 60)
            waitFor(followTime.minutes) { bot.inWilderness() || bot.navigator.currentTarget != following }
            bot.log("No longer following anyone.")
        }
        if (!bot.inWilderness()) {
            // Yes, two delays. He's thinking...
            bot.naturalDecisionDelay()
            bot.naturalDecisionDelay()
        }
    }

    private suspend fun doWalking() {
        // Doesn't really matter if we reach or not.
        val zone = RandomUtils.random(Zone.SAFE_ZONES)
        handler.travelTo(zone)
    }

    /**
     * Make the bot perform one of many random idle actions.
     */
    private suspend fun randomActions() {

        // Maybe idle at bank.
        if (maybe(UNCOMMON) {
                if (handler.banking.travelToBankOpen()) {
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
                bot.naturalDecisionDelay()
            })
            return

        // Otherwise, maybe stand around and say something at ::home.
        if (maybe(UNCOMMON) {
                bot.speechStack.pushFiller()
                bot.naturalDecisionDelay()
            })
            return
    }
}*/
     */
}