package api.bot.injectors

import api.bot.injectors.TrainingSpeechInjector.TrainingSpeech
import io.luna.game.event.Event
import io.luna.game.event.impl.SkillChangeEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.speech.BotSpeech
import io.luna.game.model.mob.bot.speech.BotSpeechContextInjector
import io.luna.game.model.mob.bot.speech.BotSpeechPool
import io.luna.util.RandomUtils

/**
 * A [BotSpeechContextInjector] responsible for injecting speech during non-combat skill training sessions
 * and reacting to nearby player level-ups.
 *
 * This injector gives bots the ability to acknowledge XP gains, brag about progress,
 * or respond socially to other players’ achievements in real time.
 *
 * @author lare96
 */
object TrainingSpeechInjector :
    BotSpeechContextInjector<TrainingSpeech>("training.json", TrainingSpeech::class.java) {

    /**
     * The distinct types of training-related speech contexts.
     */
    enum class TrainingSpeech {

        /**
         * The bot expresses gratitude for a level-up or a congratulatory message.
         */
        GRATEFUL,

        /**
         * The bot brags about their training progress or XP gains.
         */
        BRAG,

        /**
         * The bot complains about the slow pace or difficulty of training.
         */
        COMPLAIN,

        /**
         * The bot reacts humbly to a level-up (e.g. "finally!").
         */
        HUMBLE,

        /**
         * The bot congratulates another player in a kind, positive way.
         */
        OTHER_NICE,

        /**
         * The bot responds neutrally to another player's level-up.
         */
        OTHER_NEUTRAL,

        /**
         * The bot responds rudely or dismissively to another player's level-up.
         */
        OTHER_MEAN;

        companion object {

            /**
             * Computes the appropriate [TrainingSpeech] type for the bot’s own level-up event.
             *
             * @param bot The bot that levelled up.
             */
            fun computeSelfType(bot: Bot): TrainingSpeech = when {
                bot.personality.isFeelingArrogant -> BRAG
                bot.personality.isFeelingTalkative && bot.personality.intelligence < 0.6 -> COMPLAIN
                else -> HUMBLE
            }

            /**
             * Computes the appropriate [TrainingSpeech] type for responding to another player's level-up.
             *
             * @param bot The responding bot.
             */
            fun computeOtherType(bot: Bot): TrainingSpeech = when {
                bot.personality.isFeelingFriendly -> OTHER_NICE
                bot.personality.isFeelingMean -> OTHER_MEAN
                else -> OTHER_NEUTRAL
            }
        }
    }

    /**
     * The training speech pool loaded from `training.json`.
     */
    private val training = BotSpeechPool("training.json", TrainingSpeech::class.java)

    override fun onEvent(event: Event) {
        if (event is SkillChangeEvent && event.mob is Player && event.isLevelUp) {

            // No one is nearby.
            val player = event.mob as Player
            if (player.localPlayers.isEmpty()) {
                return
            }

            // Set our speech pool tags.
            val skillId = event.id
            training.setTag("skill") { Skill.getName(skillId) }
            training.setTag("level") { bot -> bot.skills.getSkill(skillId).level }

            // Look through local players.
            val actualLevel = player.skills.getSkill(skillId).level
            var sentDelay = -1
            for (localPlr in player.localPlayers) {
                if (localPlr.isBot) {
                    val localBot = localPlr.asBot()
                    val personality = localBot.personality

                    // Should we congratulate the player?
                    val isLowLevel = actualLevel < RandomUtils.inclusive(25, 50)
                    val shouldTalk = (isLowLevel && personality.isFeelingFriendly)
                            || (!isLowLevel && personality.isFeelingTalkative)
                    if (shouldTalk) {
                        val phrase = training.take(localBot, TrainingSpeech.computeOtherType(localBot))
                        sentDelay = RandomUtils.inclusive(0, 15) // Random 0-8 second delay before speaking.
                        localBot.speechStack.pushHead(BotSpeech(phrase, sentDelay))
                    }
                }
            }

            // Should the bot that levelled up say something?
            if (player.isBot) {
                val bot = player as Bot
                val delay = RandomUtils.inclusive(0, 3)
                if (sentDelay != -1 && bot.personality.isFeelingFriendly) {
                    // They said something to us, reply.
                    val phrase = training.take(bot, TrainingSpeech.GRATEFUL)
                    bot.speechStack.pushHead(BotSpeech(phrase, sentDelay + delay))
                } else if (bot.personality.isFeelingTalkative) {
                    // Otherwise, brag, show humility, excitement, etc.
                    val phrase = training.take(bot, TrainingSpeech.computeSelfType(bot))
                    bot.speechStack.pushHead(BotSpeech(phrase, delay))
                }
            }
            training.clearTags()
        }
    }
}