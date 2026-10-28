package engine.bot.speech

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.social.BotConversations
import api.predef.rand
import api.predef.world
import com.google.gson.JsonElement
import engine.bot.speech.MiningLines.Line
import game.skill.mining.Ore
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.speech.BotSpeech
import io.luna.game.model.mob.bot.speech.BotSpeechPool

/** Contextual mining reactions, said in the bot's own voice, with a per-bot cooldown that survives script changes.
 * @author TheLining
 */
object BotMiningSpeechPool : BotSpeechPool<BotMiningSpeechPool.Reaction>("mining.jsonc", Reaction::class.java) {
    enum class Reaction { YIELD, CONTEST, CROWDED, LOST_ROCK }

    private var Bot.nextMiningReaction by Attr.long()
    private var reactions: Map<Reaction, Map<MiningVoice, List<Line>>> = emptyMap()
    private val recent = ArrayDeque<String>()

    override fun read(data: JsonElement) {
        reactions = data.asJsonObject.entrySet().associate { (reaction, voices) ->
            Reaction.valueOf(reaction) to MiningLines.readVoices(voices, reaction)
        }
    }

    fun react(bot: Bot, reaction: Reaction, voice: MiningVoice, ore: Ore) {
        if (bot.localHumans.isEmpty() || bot.speechStack.isAllDisabled || BotConversations.engaged(bot)) return
        val now = world.currentTick
        if (now < bot.nextMiningReaction) return
        val complaining = reaction == Reaction.CROWDED || reaction == Reaction.LOST_ROCK
        // Complainers grumble twice as often as everyone else.
        bot.nextMiningReaction = now + when {
            complaining && voice == MiningVoice.COMPLAINER -> rand(150, 250)
            complaining -> rand(300, 600)
            else -> rand(300, 500)
        }
        if (!rand(bot.personality.social.coerceIn(0.0, 1.0) * 0.65)) return
        val text = MiningLines.pick(MiningLines.linesFor(reactions[reaction], voice), ore, recent) ?: return
        recent.addLast(text)
        if (recent.size > 30) recent.removeFirst()
        bot.speechStack.pushHead(BotSpeech(text, rand(2, 5)))
    }
}
