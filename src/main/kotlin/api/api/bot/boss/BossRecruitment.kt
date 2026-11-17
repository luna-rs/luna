package api.bot.boss

import api.bot.isRealBot
import api.bot.script.BotScript
import api.bot.social.BotSocialInteractions
import api.bot.zone.SubZone
import engine.bot.gear.BotGearPurpose
import engine.bot.speech.BotChatHandler
import engine.bot.speech.BotChatMessage
import game.bot.scripts.NpcCombatScript
import game.bot.scripts.combat.CombatBotScript
import com.google.common.collect.ImmutableSetMultimap
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Selects supplied boss outings and recruits safe nearby listeners through delivered public advertisements.
 *
 * @author lare96
 */
object BossRecruitment : BotChatHandler {
    private val disciplines = listOf(BotGearPurpose.MELEE, BotGearPurpose.RANGED, BotGearPurpose.MAGIC)

    /**
     * Uses established bank anchors; KBD can gather at either of its nearby town banks.
     */
    fun meetingBank(bot: Bot, target: BossTarget): SubZone = when (target) {
        BossTarget.MOLE -> SubZone.FALADOR_EAST_BANK
        BossTarget.KBD -> listOf(SubZone.EDGEVILLE_MAIN, SubZone.VARROCK_MARKETPLACE)
            .minBy { bot.position.computeLongestDistance(it.inside) }
        BossTarget.CHAOS_ELEMENTAL -> SubZone.EDGEVILLE_MAIN
        BossTarget.KALPHITE_QUEEN -> SubZone.AL_KHARID_BANK
    }

    /**
     * Acquires a local opening before constructing the script that owns its finite recruitment lease.
     */
    fun candidate(bot: Bot, duration: Duration): NpcCombatScript? {
        if (!bot.isRealBot() || BossOutings.member(bot) != null || BotSocialInteractions.engaged(bot)) return null
        for (target in BossTarget.entries.shuffled()) {
            val purpose = disciplines.shuffled().firstOrNull { BossReadiness.ready(bot, target, true, it) } ?: continue
            return join(bot, target, meetingBank(bot, target), purpose, duration)
        }
        return null
    }

    private fun join(bot: Bot, target: BossTarget, bank: SubZone, purpose: BotGearPurpose,
                     duration: Duration): NpcCombatScript? {
        val membership = BossOutings.joinOrCreate(bot, target, bank) ?: return null
        val names = ImmutableSetMultimap.builder<SubZone, String>()
        target.bossNpcNames.forEach { names.put(target.zone, it) }
        return NpcCombatScript(bot, duration, mutableListOf(target.zone), names.build(), purpose,
            BossReadiness.loadout(bot, target, true, purpose)?.spell?.spell, groupBoss = true)
            .also { it.outingMembership = membership }
    }

    override fun handle(message: BotChatMessage): Boolean {
        val host = message.speaker as? Bot ?: return false
        if (!BossOutingSpeechPool.matchesAdvertisement(host, message.text)) return false
        val membership = BossOutings.member(host) ?: return false
        val session = membership.session
        if (session.host !== host || session.phase != BossOutings.Phase.RECRUITING ||
            !session.bank.area.contains(host.position)) return false
        for (listener in message.hearers) {
            if (session.phase != BossOutings.Phase.RECRUITING) break
            if (BossOutings.member(listener) != null || BotSocialInteractions.engaged(listener) ||
                listener.preferences.dislikesPlayer(host.username) ||
                !BotScript.ActionSafety.NON_COMBAT.allows(listener) || listener.overlays.hasWindow() ||
                listener.scriptStack.current() is CombatBotScript ||
                listener.scriptStack.current()?.allowsCombatResponse == false) continue
            val purpose = disciplines.firstOrNull { BossReadiness.ready(listener, membership.target, true, it) } ?: continue
            val script = join(listener, membership.target, session.bank, purpose, 90.minutes) ?: continue
            listener.scriptStack.softPushHead(script)
        }
        return false
    }
}
