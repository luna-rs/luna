package api.bot.boss

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler.ChatColor
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler.ChatEffect
import io.luna.game.model.mob.bot.speech.BotSpeechPool
import java.util.Locale

/**
 * Configured recruitment phrases repeated with stable trade-style wording and chat formatting. Calls only send
 * public speech; the outing registry owns membership, readiness and departure independently of the text.
 *
 * @author lare96
 */
object BossOutingSpeechPool : BotSpeechPool<BossOutingSpeechPool.Speech>("boss_outings.jsonc", Speech::class.java) {
    private var Bot.advertisement: Advertisement? by Attr.nullableObj(Advertisement::class)

    /**
     * Recruitment speech contexts stored in the shared phrase-pool format.
     *
     * @author lare96
     */
    enum class Speech { INVITATION }

    /**
     * One bot's current autotyping configuration and next delivery time; never persisted with the character.
     *
     * @author lare96
     */
    private class Advertisement(val session: BossOutings.Session, val target: BossTarget,
                                val needed: Int, val text: String, val color: ChatColor,
                                val effect: ChatEffect, var nextAt: Long = 0, var queuedAt: Long? = null)

    /**
     * Recognizes only this session's recent successfully queued invitation after native unpacking has removed
     * chat formatting. Public-chat routing already supplies lowercase text; ordinary reactions cannot recruit.
     */
    fun matchesAdvertisement(bot: Bot, text: String): Boolean {
        val advertisement = bot.advertisement ?: return false
        val queued = advertisement.queuedAt ?: return false
        val membership = BossOutings.member(bot) ?: return false
        return membership.session === advertisement.session && membership.session.host === bot &&
            membership.phase == BossOutings.Phase.RECRUITING &&
            System.currentTimeMillis() - queued in 0..15_000 &&
            text.trim().lowercase(Locale.ROOT) == advertisement.text.trim().lowercase(Locale.ROOT)
    }

    /**
     * Repeats a real recruiting session's invitation at a finite interval. Failed delivery retains its cooldown,
     * and the next call can never manufacture a replacement group or count unprepared companions.
     */
    fun advertise(bot: Bot, target: BossTarget, needed: Int): Boolean {
        val member = BossOutings.member(bot) ?: return false
        if (member.session.host !== bot || member.target != target || member.phase != BossOutings.Phase.RECRUITING ||
            !member.prepared || !member.session.bank.area.contains(bot.position) || needed <= 0) return false
        val old = bot.advertisement
        val current = if (old != null && old.session === member.session && old.target == target && old.needed == needed) old else {
            val phrase = take(bot, Speech.INVITATION) ?: return false
            val boss = when (target) {
                BossTarget.MOLE -> "Giant Mole"
                BossTarget.KBD -> "KBD"
                BossTarget.CHAOS_ELEMENTAL -> "Chaos Elemental"
                BossTarget.KALPHITE_QUEEN -> "Kalphite Queen"
            }
            Advertisement(member.session, target, needed,
                phrase.replace("{boss}", boss).replace("{needed}", needed.toString()),
                ChatColor.entries.random(), listOf(ChatEffect.NONE, ChatEffect.WAVE, ChatEffect.WAVE_2,
                    ChatEffect.SHAKE, ChatEffect.SLIDE).random(), old?.nextAt ?: 0).also { bot.advertisement = it }
        }
        val now = System.currentTimeMillis()
        if (now < current.nextAt) return false
        current.nextAt = now + (6_000L..10_000L).random()
        return bot.output.chat(current.text, current.color, current.effect).also {
            current.queuedAt = if (it) now else null
        }
    }
}
