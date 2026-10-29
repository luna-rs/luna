package engine.bot.speech

import com.google.gson.JsonElement
import engine.bot.speech.MiningLines.Line
import game.skill.mining.Ore
import io.luna.game.model.mob.bot.speech.BotSpeechPool
import kotlin.random.Random

/**
 * Builds rock arguments from beats instead of fixed scripts. The starter (a mean miner, or one of two complainers)
 * claims the rock, and every line after that is picked in the speaker's own voice. The responder may ignore the
 * starter, and either side may walk away early, so arguments run anywhere from two to six lines.
 *
 * @author TheLining
 */
object BotMiningArgumentPool : BotSpeechPool<BotMiningArgumentPool.Beat>(
    "mining_arguments.jsonc", Beat::class.java
) {
    /**
     * One step of an argument. Starter beats: [OPEN], [IGNORED], [PUSH], [LAST_WORD].
     * Responder beats: [REPLY], [COMEBACK], [CLOSER]. [LAUGHS] are tacked onto punchlines.
     */
    enum class Beat { OPEN, IGNORED, REPLY, PUSH, COMEBACK, LAST_WORD, CLOSER, LAUGHS }

    /** The finished argument: each line and who says it (0 = starter, 1 = responder). */
    class Argument(val lines: List<String>, val speakers: List<Int>)

    private const val STARTER = 0
    private const val RESPONDER = 1

    private var beats: Map<Beat, Map<MiningVoice, List<Line>>> = emptyMap()

    /** Recently heard lines across the server, so onlookers rarely hear the same joke twice. */
    private val recent = ArrayDeque<String>()
    private const val RECENT_LIMIT = 60

    override fun read(data: JsonElement) {
        beats = data.asJsonObject.entrySet().associate { (beat, voices) ->
            Beat.valueOf(beat) to MiningLines.readVoices(voices, beat)
        }
    }

    /** How often each voice just keeps mining instead of answering. Power miners can't spare the clicks. */
    private fun ignoreChance(voice: MiningVoice) = when (voice) {
        MiningVoice.POWER_MINER, MiningVoice.IRON_TRAINER -> 0.4
        MiningVoice.BALANCED -> 0.2
        MiningVoice.MEAN -> 0.1
        MiningVoice.KIND, MiningVoice.COMPLAINER -> 0.05
    }

    /** How often the starter gives up after each exchange. Mean miners always want the last word. */
    private fun quitChance(voice: MiningVoice, round: Int) = when (voice) {
        MiningVoice.MEAN -> if (round == 1) 0.15 else 0.25
        else -> if (round == 1) 0.25 else 0.35
    }

    /** Who laughs at their own lines. Complainers and power miners never find anything funny. */
    private fun laughChance(voice: MiningVoice) = when (voice) {
        MiningVoice.MEAN -> 0.3
        MiningVoice.KIND -> 0.25
        MiningVoice.BALANCED -> 0.2
        else -> 0.0
    }

    private val alreadyLaughing = listOf("lol", "rofl", "lmao", "haha", "hehe", ":)")

    /** Builds an argument over [ore], or `null` when a voice has nothing to say. */
    fun compose(starter: MiningVoice, responder: MiningVoice, ore: Ore): Argument? {
        val lines = ArrayList<String>()
        val speakers = ArrayList<Int>()
        fun say(beat: Beat, voice: MiningVoice, who: Int, punchline: Boolean = false): Boolean {
            var text = MiningLines.pick(MiningLines.linesFor(beats[beat], voice), ore, recent) ?: return false
            heard(text)
            if (punchline && Random.nextDouble() < laughChance(voice) && !text.endsWith("?") &&
                alreadyLaughing.none { it in text }) {
                val laugh = MiningLines.pick(MiningLines.linesFor(beats[Beat.LAUGHS], voice), ore, emptyList())
                if (laugh != null && text.length + laugh.length < 80) text = "$text $laugh"
            }
            lines += text
            speakers += who
            return true
        }

        if (!say(Beat.OPEN, starter, STARTER)) return null
        if (Random.nextDouble() < ignoreChance(responder)) {
            // The responder keeps swinging; the starter is left talking to a rock.
            if (!say(Beat.IGNORED, starter, STARTER)) return null
            return Argument(lines, speakers)
        }
        if (!say(Beat.REPLY, responder, RESPONDER, punchline = responder == MiningVoice.KIND)) return null
        if (Random.nextDouble() < quitChance(starter, 1)) return Argument(lines, speakers)

        if (!say(Beat.PUSH, starter, STARTER, punchline = true) ||
            !say(Beat.COMEBACK, responder, RESPONDER, punchline = true))
            return Argument(lines.take(2), speakers.take(2))
        if (Random.nextDouble() < quitChance(starter, 2)) return Argument(lines, speakers)

        if (!say(Beat.LAST_WORD, starter, STARTER, punchline = true) ||
            !say(Beat.CLOSER, responder, RESPONDER, punchline = true))
            return Argument(lines.take(4), speakers.take(4))
        return Argument(lines, speakers)
    }

    private fun heard(text: String) {
        recent.addLast(text)
        if (recent.size > RECENT_LIMIT) recent.removeFirst()
    }
}
