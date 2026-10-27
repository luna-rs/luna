package api.bot.social

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.zone.SubZone
import api.predef.*
import engine.bot.speech.BotCampSpeechPool
import engine.bot.speech.BotCampSpeechPool.Line
import engine.bot.speech.BotCampSpeechPool.Tone
import engine.bot.speech.BotChatHandler
import engine.bot.speech.BotChatMessage
import engine.bot.speech.BotConversationPool
import game.bot.scripts.NpcCombatScript
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import java.util.EnumMap
import kotlin.random.Random

/** One bot's camp habits and short-lived memories. Training, attacks and movement still belong to its script. */
class CombatCampBehavior(private val script: NpcCombatScript, private val accepts: (Npc) -> Boolean) {
    private val bot get() = script.bot
    private val now get() = bot.world.currentTick
    private val mean = bot.personality.kindness < 0.35
    // A stable minority are habitual complainers, including otherwise kind players.
    private val complainer = !mean && Math.floorMod(bot.usernameHash, 7L) == 0L
    private val patience = 0.75 + Math.floorMod(bot.usernameHash, 101L) / 200.0
    private var zone: SubZone? = null
    private var nextSample = 0L
    private var nextSpeech = 0L
    private var availableToChatAt = 0L
    private var poorSince = -1L
    private var lastKill = 0L
    private var watched: Npc? = null
    private var watchedLife = -1
    private val lostLives = linkedMapOf<Npc, Int>()
    private var irritation = 0.0
    private var crowded = false
    private var rival: Player? = null
    private var rivalUntil = 0L
    private var partner: Player? = null
    private var talkedUntil = 0L

    val tone: Tone get() = when {
        mean -> Tone.MEAN
        irritation >= (3.0 + 4.0 * bot.personality.kindness) * patience -> Tone.AGGRAVATED
        complainer -> Tone.COMPLAINER
        else -> Tone.FRIENDLY
    }

    /** Kind bots value an actual exchange, including a short lull, over their kill rate. */
    val holdingConversation: Boolean get() = !mean && bot.personality.kindness >= 0.5 &&
        now < talkedUntil && partner?.let { nearby(it) } == true

    fun tick(currentZone: SubZone?, focus: Npc?) {
        if (zone != currentZone) {
            close()
            zone = currentZone
            lastKill = now
            poorSince = -1L
            nextSample = now
            nextSpeech = now + Random.nextLong(60, 150)
            irritation = 0.0
            watched = null
            lostLives.clear()
            rival = null
            crowded = false
        }
        BotConversations.tick(bot)
        if (currentZone == null || !script.canSocialize()) return
        if (now >= rivalUntil || rival?.let { !nearby(it) } == true) rival = null
        if (now >= nextSample) {
            nextSample = now + 10
            observe(focus)
        }
        if (now >= nextSpeech) converse()
    }

    private fun observe(focus: Npc?) {
        val previous = watched
        if (previous != null && previous.spawnCount == watchedLife && !previous.isAlive) {
            lastKill = now
            irritation = (irritation - 0.8).coerceAtLeast(0.0)
            watched = null
        }
        if (focus?.isAlive == true && bot.combat.target === focus) {
            watched = focus
            watchedLife = focus.spawnCount
        }
        val npcs = bot.world.locator.findViewableNpcs(bot) {
            it.isAlive && bot.isWithinDistance(it, 12) && accepts(it)
        }
        val players = bot.world.locator.findViewablePlayers(bot) {
            it !== bot && it.isAlive && bot.isWithinDistance(it, 12) &&
                ((it.combat.target as? Npc) in npcs || npcs.any { npc -> owner(npc) === it })
        }
        val free = npcs.count { owner(it) == null }
        crowded = players.isNotEmpty() && free <= maxOf(1, players.size / 2)
        if (crowded && now - lastKill >= 30) {
            if (poorSince < 0) poorSince = now
            irritation = (irritation + 0.18 * (0.5 + bot.personality.confidence)).coerceAtMost(12.0)
        } else {
            poorSince = -1L
            irritation = (irritation - 0.12).coerceAtLeast(0.0)
        }
        if (mean && rival == null && players.isNotEmpty() && Random.nextDouble() < 0.10) {
            antagonize(players.random())
        }
    }

    /** A failed race is remembered once per NPC life, rather than once per retry tick. */
    fun lost(npc: Npc) {
        val competitor = owner(npc)?.takeIf { it !== bot } ?: return
        if (lostLives[npc] == npc.spawnCount) return
        lostLives[npc] = npc.spawnCount
        if (lostLives.size > 32) lostLives.remove(lostLives.keys.first())
        irritation = (irritation + 1.0).coerceAtMost(12.0)
        if (mean && Random.nextDouble() < 0.45) antagonize(competitor)
    }

    /** Polite bots share occupied monsters even in multi; mean bots use legal attack opportunities immediately. */
    fun acceptsTarget(npc: Npc): Boolean {
        val other = owner(npc)?.takeIf { it !== bot }
        if (other == null || mean || bot.personality.kindness < 0.5) return true
        lost(npc)
        return false
    }

    fun order(options: MutableList<Npc>) {
        if (mean || bot.personality.intelligence >= 0.65) {
            options.sortBy { npc ->
                val distance = bot.position.computeLongestDistance(npc.position).toDouble()
                val contested = rival?.takeIf { now < rivalUntil && nearby(it) }?.let {
                    owner(npc) === it || it.combat.target === npc || it.isWithinDistance(npc, 2)
                } == true
                // Travel and time to finish a kill define a useful target. A temporary grudge biases that choice.
                distance * 3.0 + npc.health.toDouble() / maxOf(1, npc.totalHealth) * 5.0 -
                    (if (mean && contested) 6.0 else 0.0)
            }
        } else if (Random.nextDouble() < bot.personality.dexterity * 0.75) {
            options.sortBy { bot.position.computeLongestDistance(it.position) }
        } else options.shuffle()
    }

    fun shouldLeave(): Boolean {
        if (!crowded || poorSince < 0 || holdingConversation || bot.combat.inCombat()) return false
        val intelligence = bot.personality.intelligence
        val seconds = when {
            mean -> 150 + 150 * bot.personality.confidence
            intelligence < 0.35 -> 100 + 100 * (1 - intelligence)
            bot.personality.kindness >= 0.65 -> 100 + 100 * bot.personality.social
            else -> 45 + 90 * (1 - intelligence)
        } * patience
        if (now - poorSince < seconds / 0.6) return false
        bot.log("Leaving crowded camp: tone=$tone, unproductiveTicks=${now - poorSince}.")
        return true
    }

    private fun converse() {
        nextSpeech = now + Random.nextLong(100, 220)
        if (!canStartAutomaticChat() || now < availableToChatAt ||
            !BotConversations.available(bot, allowWorking = true) ||
            Random.nextDouble() > 0.25 + 0.7 * bot.personality.social) return
        val people = bot.world.locator.findViewablePlayers(bot) {
            it !== bot && nearby(it) && (it !is Bot || BotConversations.available(it, allowWorking = true))
        }
        val listener = rival?.takeIf { it in people } ?: people.shuffled().firstOrNull() ?: return
        val ownTone = tone
        if (listener is Bot) {
            val other = (listener.scriptStack.current() as? NpcCombatScript)?.campBehavior
            if (other != null && now < other.availableToChatAt) return
            if (!BotConversations.available(listener, allowWorking = true)) return
            // Ordinary conversations remain the usual path. Competition adds occasional contextual exchanges.
            val campChance = when {
                rival === listener -> 0.65
                ownTone == Tone.FRIENDLY -> 0.25
                else -> 0.50
            }
            val lines = if (crowded && other != null && other.zone == zone && Random.nextDouble() < campChance) {
                BotCampSpeechPool.exchange(bot, ownTone, listener, other.tone)
            } else BotConversationPool.select(bot, listener)
            if (lines == null) return
            if (BotConversations.start(bot, listener, lines, allowWorking = true, delivered = {
                    rememberTalk(listener)
                    other?.rememberTalk(bot)
                }) != null) {
                paceCamp()
                availableToChatAt = now + 100
                if (other != null) {
                    other.availableToChatAt = now + 100
                    other.nextSpeech = now + Random.nextLong(65, 150)
                }
            }
        } else {
            if (!crowded) return
            // Friendly greetings and complaints invite a reply; a human's reply establishes the social stay.
            val line = BotCampSpeechPool.line(bot, Line.valueOf("OPEN_$ownTone")) ?: return
            if (BotConversations.say(bot, line, valid = { nearby(listener) },
                    delivered = { paceCamp() }) != null) {
                paceCamp()
                partner = listener
            }
        }
    }

    /** One spontaneous exchange gets room to finish; replies to a human are never gated by this budget. */
    private fun canStartAutomaticChat(): Boolean {
        val camp = zone ?: return false
        if (holdingConversation || now < (nextCampConversation[camp] ?: 0L)) return false
        return bot.world.locator.findViewablePlayers(bot) {
            it is Bot && it !== bot && bot.isWithinDistance(it, 8) &&
                (BotConversations.engaged(it) ||
                    (it.scriptStack.current() as? NpcCombatScript)?.campBehavior?.holdingConversation == true)
        }.isEmpty()
    }

    /** Refresh after every delivered turn, leaving 75–150 seconds of breathing room after an exchange. */
    private fun paceCamp() {
        val camp = zone ?: return
        nextCampConversation[camp] = now + Random.nextLong(125, 251)
    }

    internal fun humanReply(speaker: Player, text: String): Boolean {
        if (!script.canSocialize() || !nearby(speaker)) return false
        val insult = insult.containsMatchIn(text)
        if (insult) {
            irritation = (irritation + 2.0).coerceAtMost(12.0)
            if (mean) antagonize(speaker)
        }
        rememberTalk(speaker)
        if (now < nextHumanReply || !BotConversations.available(bot, allowWorking = true)) return false
        val skill = when {
            Regex("\\b(str|strength)\\b").containsMatchIn(text) -> "str" to bot.strength.level
            Regex("\\b(att|attack)\\b").containsMatchIn(text) -> "attack" to bot.attack.level
            Regex("\\b(def|defence)\\b").containsMatchIn(text) -> "def" to bot.defence.level
            Regex("\\b(range|ranged)\\b").containsMatchIn(text) -> "range" to bot.ranged.level
            Regex("\\b(mage|magic)\\b").containsMatchIn(text) -> "mage" to bot.magic.level
            else -> "combat" to bot.combatLevel
        }
        val kind = when {
            !mean && !insult && levelQuestion.containsMatchIn(text) -> Line.LEVEL
            hello.containsMatchIn(text) && !insult -> Line.valueOf("HELLO_$tone")
            else -> Line.valueOf("REPLY_$tone")
        }
        val line = BotCampSpeechPool.line(bot, kind, skill.first, skill.second) ?: return false
        val expires = now + 35
        if (BotConversations.say(bot, line, valid = { now < expires && nearby(speaker) },
                delivered = { rememberTalk(speaker) }) == null) return false
        nextHumanReply = now + 12
        availableToChatAt = now + 75
        nextSpeech = now + Random.nextLong(65, 150)
        return true
    }

    private var nextHumanReply = 0L

    private fun rememberTalk(player: Player) {
        partner = player
        talkedUntil = now + 75
        paceCamp()
    }

    private fun antagonize(player: Player) {
        rival = player
        rivalUntil = now + Random.nextLong(100, 250)
    }

    private fun nearby(player: Player): Boolean = player !== bot && player.state == EntityState.ACTIVE &&
        player.isAlive && bot.isWithinDistance(player, 8) &&
        !bot.ignores.contains(player.usernameHash) && !player.ignores.contains(bot.usernameHash)

    fun close() {
        BotConversations.cancel(bot)
        partner = null
        talkedUntil = 0L
    }

    companion object : BotChatHandler {
        // Only deadlines are shared. Participants and conversations remain owned by their normal script/speech state.
        private val nextCampConversation = EnumMap<SubZone, Long>(SubZone::class.java)
        private val hello = Regex("\\b(hi|hey|hello|sup|yo)\\b")
        private val greeting = Regex("\\b(hi|hey|hello|sup|yo|anyone|crowded|busy|share|spot|training|train|kills|respawn)\\b")
        private val levelQuestion = Regex("\\b(lvl|level|levels|cb)\\b")
        private val insult = Regex("\\b(noob|nub|nerd|loser|idiot|stupid|ks|steal|stole|crash|crasher)\\b")
        private var Player.tauntedUntil by Attr.long { 0L }

        private fun owner(npc: Npc): Player? = (npc.combat.target as? Player)?.takeIf { it.isAlive }
            ?: (npc.combat.lastCombatWith as? Player)?.takeIf { npc.combat.inCombat() && it.isAlive }

        /** Human messages recruit one nearby listener; bot dialogue is already owned by the conversation runner. */
        override fun handle(message: BotChatMessage): Boolean {
            if (message.speaker is Bot) return false
            val candidates = message.hearers.mapNotNull {
                (it.scriptStack.current() as? NpcCombatScript)?.campBehavior
            }.filter { it.script.canSocialize() && it.nearby(message.speaker) }
            val named = candidates.filter {
                val name = it.bot.username.lowercase().replace('_', ' ')
                val text = message.text.replace('_', ' ')
                if (name.length < 3) text == name || text.startsWith("$name,") || text.startsWith("@$name ")
                else Regex("(?<![a-z0-9])${Regex.escape(name)}(?![a-z0-9])").containsMatchIn(text)
            }
            val continuing = candidates.filter { it.partner === message.speaker && it.now < it.talkedUntil }
            val options = named.ifEmpty { continuing }.ifEmpty {
                if (greeting.containsMatchIn(message.text) || levelQuestion.containsMatchIn(message.text) ||
                    insult.containsMatchIn(message.text)) candidates else emptyList()
            }
            val listener = options.minByOrNull { it.bot.position.computeLongestDistance(message.speaker.position) }
                ?: return false
            return listener.humanReply(message.speaker, message.text)
        }

        /** Called only for a damaging hit that crosses into the last tenth of health, never after death. */
        fun nearDeath(player: Player, damage: Int) {
            val now = world.currentTick
            val threshold = maxOf(1, player.totalHealth / 10)
            if (damage <= 0 || player.health !in 1..threshold || player.health + damage <= threshold ||
                now < player.tauntedUntil || !player.combat.inCombat()) return
            val observers = world.locator.findViewablePlayers(player) {
                it is Bot && it !== player && it.isWithinDistance(player, 8)
            }.filterIsInstance<Bot>().mapNotNull {
                (it.scriptStack.current() as? NpcCombatScript)?.campBehavior
            }.filter { it.mean && it.nearby(player) && BotConversations.available(it.bot, allowWorking = true) }
                .sortedByDescending { it.rival === player }
            val observer = observers.firstOrNull() ?: return
            if (now < observer.availableToChatAt ||
                Random.nextDouble() > 0.35 + 0.5 * observer.bot.personality.social) return
            val line = BotCampSpeechPool.line(observer.bot, Line.DYING) ?: return
            val expires = now + 8
            if (BotConversations.say(observer.bot, line, valid = {
                    world.currentTick < expires && observer.nearby(player) &&
                        player.health in 1..threshold && player.combat.inCombat()
                }, typingLimit = 6) != null) {
                player.tauntedUntil = now + 150
                observer.paceCamp()
                observer.availableToChatAt = now + 150
                observer.nextSpeech = now + 150
            }
        }
    }
}
