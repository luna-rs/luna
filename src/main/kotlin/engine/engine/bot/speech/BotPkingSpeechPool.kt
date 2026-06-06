package engine.bot.speech

import engine.bot.speech.BotPkingSpeechPool.PkingSpeech
import io.luna.game.model.mob.bot.speech.BotSpeechPool
import java.nio.file.Paths

/**
 * A [BotSpeechPool] for bot player-killing speech.
 *
 * This pool loads player-killing chat lines from `data/bots/speech/pking.jsonc`. Each top-level JSONC key maps to a
 * [PkingSpeech] category and contains lines that can be selected during different stages of a PK encounter.
 *
 * The categories are split by both fight stage and emotional tone. Positive categories are used when the bot feels
 * confident, aggressive, excited, or in control. Negative categories are used when the bot feels pressured, annoyed,
 * scared, outmatched, or frustrated.
 *
 * @author lare96
 */
object BotPkingSpeechPool :
    BotSpeechPool<PkingSpeech>(Paths.get("pking.jsonc"), PkingSpeech::class.java) {

    /**
     * Enumerates player-killing speech categories.
     *
     * Each category corresponds to a top-level key in `pking.jsonc`. Categories are grouped by encounter stage:
     * start-of-fight, mid-fight, and end-of-fight.
     */
    enum class PkingSpeech {

        /**
         * Lines a bot may say at the start of a PK encounter when it feels confident or eager to fight.
         */
        POSITIVE_START,

        /**
         * Lines a bot may say at the start of a PK encounter when it feels nervous, annoyed, cautious, or outmatched.
         */
        NEGATIVE_START,

        /**
         * Lines a bot may say during a PK encounter when the fight is going well or the bot feels in control.
         */
        POSITIVE_FIGHTING,

        /**
         * Lines a bot may say during a PK encounter when the fight is going poorly or the bot feels pressured.
         */
        NEGATIVE_FIGHTING,

        /**
         * Lines a bot may say after a PK encounter ends positively, such as after getting a kill, escaping, or winning.
         */
        POSITIVE_END,

        /**
         * Lines a bot may say after a PK encounter ends negatively, such as after dying, losing loot, or failing to kill.
         */
        NEGATIVE_END
    }
}