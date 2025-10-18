package api.bot.injectors

import api.attr.Attr
import api.bot.injectors.TrainingSpeechInjector.TrainingSpeech
import api.predef.*
import io.luna.game.event.Event
import io.luna.game.event.impl.SkillChangeEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionType
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler.ChatColor
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler.ChatEffect
import io.luna.game.model.mob.bot.speech.BotSpeech
import io.luna.game.model.mob.bot.speech.BotSpeechContextInjector
import io.luna.game.model.mob.bot.speech.BotSpeechPool
import io.luna.util.RandomUtils
import java.time.Duration
import java.time.Instant

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
//todo make injectors general purpose, apart of intelligence, processed before scripts after refkex combat logout
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
                bot.personality.isArrogant -> BRAG
                bot.emotions.isFeeling(EmotionType.ANGRY) -> COMPLAIN
                else -> HUMBLE
            }

            /**
             * Computes the appropriate [TrainingSpeech] type for responding to another player's level-up.
             *
             * @param bot The responding bot.
             */
            fun computeOtherType(bot: Bot): TrainingSpeech = when {
                bot.personality.isKind || bot.emotions.isFeeling(EmotionType.HAPPY) -> OTHER_NICE
                bot.personality.isMean || bot.emotions.isFeeling(EmotionType.ANGRY) -> OTHER_MEAN
                else -> OTHER_NEUTRAL
            }
        }
    }

    /**
     * The training speech pool loaded from `training.json`.
     */
    private val training = BotSpeechPool("training.json", TrainingSpeech::class.java)

    /**
     * The attribute representing when a level was last gained by a bot.
     */
    internal var Bot.lastLevelAt by Attr.nullableObj(Instant::class)

    override fun onEvent(event: Event) {
        if (event is SkillChangeEvent && event.mob is Player && event.isLevelUp) {
            // No one is nearby.
            val player = event.mob as Player
            if (player is Bot) {
                player.lastLevelAt = Instant.now()
            }
            if (player.localPlayers.isEmpty()) {
                return
            }

            // Set our speech pool tags.
            val skillId = event.id
            training.withTags {
                setTag("skill") { Skill.getName(skillId) }
                setTag("level") { bot -> bot.skills.getSkill(skillId).level }
                congratulateBot(player, skillId)
            }
        }
    }

    override fun onSpeech(player: Player, message: String, color: ChatColor, effect: ChatEffect) {
        if (player.localBots.isNotEmpty()) {
            if (message.contains("gz") || message.contains("grats")) {
                replyThanks(player)
            }
        }
    }

    /**
     * Handles the logic for bots reacting to a player's level-up event. When a nearby player (or another bot) levels
     * up a skill, this method determines whether surrounding bots should acknowledge the achievement.
     *
     * @param player  The player who gained the level.
     * @param skillId The skill ID that was leveled up.
     */
    private fun congratulateBot(player: Player, skillId: Int) {
        // Look through local players.
        val actualLevel = player.skills.getSkill(skillId).level
        for (localPlr in player.localPlayers) {
            if (localPlr.isBot) {
                val localBot = localPlr.asBot()

                // Should we congratulate the player?
                val isLowLevel = actualLevel < RandomUtils.inclusive(25, 50)
                val shouldTalk = (isLowLevel && localBot.personality.isKind)
                        || (!isLowLevel && localBot.emotions.isFeeling(EmotionType.HAPPY))
                if (shouldTalk) {
                    val phrase = training.take(localBot, TrainingSpeech.computeOtherType(localBot))
                    val delay = RandomUtils.inclusive(0, 15) // Random 0-8 second delay before speaking.
                    localBot.speechStack.pushHead(BotSpeech(phrase, delay))
                }
            }
        }
        if (player is Bot) {
            if (player.emotions.isFeeling(EmotionType.HAPPY)) {
                // Brag, show humility, excitement, etc.
                val phrase = training.take(player, TrainingSpeech.computeSelfType(player))
                val delay = rand(1, 5)
                player.speechStack.pushHead(BotSpeech(phrase, delay))
            }
        }
    }

    /**
     * Handles bot responses to congratulatory chat messages from nearby players.
     *
     * When a player says phrases such as “gz” or “grats,” bots that recently leveled up will respond with a grateful
     * line if they are in a positive emotional state.
     *
     * @param player The player who sent the congratulatory message.
     */
    private fun replyThanks(player: Player) {
        for (bot in player.localBots) {
            if (bot.lastLevelAt == null) {
                continue
            }
            val difference = Duration.between(bot.lastLevelAt, Instant.now())
            if (difference.toSeconds() < 30 && bot.emotions.isFeeling(EmotionType.HAPPY)) {
                // They said something to us, reply.
                val phrase = training.take(bot, TrainingSpeech.GRATEFUL)
                val delay = rand(1, 5)
                bot.speechStack.pushHead(BotSpeech(phrase, delay))
            }
        }
    }
}