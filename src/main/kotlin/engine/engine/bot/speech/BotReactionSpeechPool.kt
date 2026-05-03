package engine.bot.speech

import engine.bot.speech.BotReactionSpeechPool.ReactionSpeech
import engine.bot.speech.BotReactions.lastImpressedBy
import engine.bot.speech.BotReactions.lastRareDrop
import engine.bot.speech.BotReactions.lastScammedBy
import engine.bot.speech.BotReactions.lastSkillAdvanced
import engine.bot.speech.BotReactions.lastWitnessedSkillAdvance
import io.luna.game.model.mob.bot.speech.BotSpeechPool
import java.nio.file.Paths

/**
 * A [BotSpeechPool] for short bot reactions to gameplay events.
 *
 * This pool loads event-specific reaction lines from `data/bots/speech/reactions.jsonc`. Each top-level JSONC key maps
 * to a [ReactionSpeech] category and contains short chat lines that can be selected when the matching event occurs.
 *
 * Some reaction lines support runtime tags. These tags are replaced when the line is selected, allowing reactions to
 * reference the relevant player, skill, level, drop, or scammer.
 *
 * @author lare96
 */
object BotReactionSpeechPool :
    BotSpeechPool<ReactionSpeech>(Paths.get("reactions.jsonc"), ReactionSpeech::class.java) {

    /**
     * Enumerates event-triggered bot reaction speech categories.
     *
     * Each category corresponds to a top-level key in `reactions.jsonc`. The lines in each category are intended to be
     * short, casual, and suitable for public chat after the matching event occurs.
     */
    enum class ReactionSpeech {

        /**
         * Lines a bot may say after dying.
         *
         * This category does not currently define any runtime tags.
         */
        DIED,

        /**
         * Lines a bot may say when reacting positively to another player, item, achievement, or display of wealth.
         *
         * Supports `<name>` for the name of the player or entity that impressed the bot.
         */
        IMPRESSED,

        /**
         * Lines a bot may say after gaining a level.
         *
         * Supports `<skill>` for the advanced skill name and `<level>` for the new level.
         */
        LEVEL_UP,

        /**
         * Lines a bot may say after witnessing another player die.
         *
         * This category does not currently define any runtime tags.
         */
        OTHER_DIED,

        /**
         * Lines a bot may say after witnessing another player gain a level.
         *
         * Supports `<name>` for the name of the player who gained the level.
         */
        OTHER_LEVEL_UP,

        /**
         * Lines a bot may say after receiving a rare or valuable drop.
         *
         * Supports `<drop>` for the name of the rare or valuable drop.
         */
        RARE_DROP,

        /**
         * Lines a bot may say after being scammed.
         *
         * Supports `<name>` for the name of the bot that scammed this bot.
         */
        SCAMMED
    }

    /**
     * Registers runtime tag resolvers for reaction lines.
     */
    init {
        setTag(ReactionSpeech.IMPRESSED, "<name>") { it.lastImpressedBy }
        setTag(ReactionSpeech.LEVEL_UP, "<skill>") { it.lastSkillAdvanced.first }
        setTag(ReactionSpeech.LEVEL_UP, "<level>") { it.lastSkillAdvanced.second }
        setTag(ReactionSpeech.OTHER_LEVEL_UP, "<name>") { it.lastWitnessedSkillAdvance }
        setTag(ReactionSpeech.RARE_DROP, "<drop>") { it.lastRareDrop }
        setTag(ReactionSpeech.SCAMMED, "<name>") { it.lastScammedBy }
    }
}