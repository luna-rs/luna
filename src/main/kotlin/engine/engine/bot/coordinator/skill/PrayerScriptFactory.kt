package engine.bot.coordinator.skill

import api.bot.action.BotCombatActionHandler
import api.bot.action.trading.BotMarketPreference
import api.bot.script.BotScript
import api.bot.script.DynamicBotScript
import api.bot.script.VoidBotScript
import api.bot.zone.SubZone
import api.predef.*
import engine.bot.coordinator.CombatCoordinator
import game.bot.scripts.skills.BuryBoneBotScript
import game.bot.scripts.skills.PrayerBoneStock
import game.bot.scripts.skills.PrayerPurchaseBotScript
import game.bot.scripts.skills.EctofuntusBotScript
import game.content.ectofuntus.EctofuntusBone
import game.skill.prayer.Bone
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Chooses Prayer supplies by personality and prefers worship when a native Ectofuntus trip is feasible.
 *
 * @author lare96
 */
object PrayerScriptFactory : SkillingScriptFactory(SKILL_PRAYER) {
    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        getProductionScript(bot) ?: VoidBotScript(bot)

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        object : DynamicBotScript(bot) {
            override suspend fun run() = true
        }

    internal fun getProductionScript(bot: Bot): BotScript? {
        val duration = getDuration(bot, skillId)
        if (!BotCombatActionHandler.wantsToTrain(bot, skillId)) return null
        EctofuntusBotScript.recover(bot, duration)?.let { return it }
        PrayerBonePurchases.select(bot)?.let { return PrayerPurchaseBotScript(bot, duration, it) }
        if (PrayerBoneStock.hasNotes(bot)) return PrayerPurchaseBotScript(bot, duration)
        ownedTraining(bot, duration)?.let { return it }
        return CombatCoordinator.prayerBoneGatheringScript(bot, duration)
    }

    internal fun ownedTraining(bot: Bot, duration: Duration, preferred: Bone? = null): BotScript? {
        if (!BotCombatActionHandler.wantsToTrain(bot, skillId)) return null
        val reserves = PrayerBoneStock.reserves(bot)
        val options = Bone.entries.filter { PrayerBoneStock.owned(bot, it.id) > (reserves[it.id] ?: 0) }
        val merchant = BotMarketPreference.profitSeeking(bot)
        val bone = preferred?.takeIf { it in options } ?: if (bot.personality.isDextrous) {
            options.maxByOrNull { it.exp }
        } else {
            options.minByOrNull {
                val guide = bot.world.economy.getPrice(it.id).takeIf { price -> price > 0 } ?: Long.MAX_VALUE
                if (merchant) guide.toDouble() else guide / it.exp
            }
        }
        if (bone == null) return EctofuntusBotScript.select(bot, duration, preferWorship = true)
        val recipe = EctofuntusBone.forBone(bone.id)
        return recipe?.let { EctofuntusBotScript.select(bot, duration, it, preferWorship = true) }
            ?: BuryBoneBotScript(bot, bone, duration, ownedOuting = true)
    }
}
