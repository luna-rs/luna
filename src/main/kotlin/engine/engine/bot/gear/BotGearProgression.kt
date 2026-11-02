package engine.bot.gear

import api.attr.Attr
import api.predef.*
import com.google.common.collect.ImmutableSet
import game.bot.scripts.trading.ObtainGearBotScript
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.def.WantedItemDefinition.WantedItemPriority
import io.luna.game.model.def.WantedItemDefinition.WantedItemTag
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity

/**
 * Reviews equipment unlocks every two permanent skill levels and queues useful upgrades for acquisition.
 * The shared unlock index limits reviews to thresholds crossed since the previous review.
 *
 * @author lare96
 */
object BotGearProgression {

    /**
     * The last reviewed level of each skill, held only for the current bot session.
     */
    private val Bot.reviewedGearLevels by Attr.map<Int, Int>()

    /**
     * Progression requests and the subset created here, so superseded temporary wants can be removed safely.
     */
    private val Bot.progressionGear by Attr.hashSet<Int>()
    private val Bot.createdGearWants by Attr.hashSet<Int>()

    /**
     * Responds to permanent level gains. Intelligent bots reliably notice upgrades; other bots notice
     * them with a probability equal to their intelligence. Very intelligent shoppers arrange an errand.
     */
    fun onLevelUp(bot: Bot, skillId: Int, oldLevel: Int, newLevel: Int) {
        val levels = reviewLevels(bot, skillId, oldLevel, newLevel) ?: return
        if (!bot.personality.isIntelligent && !rand(bot.personality.intelligence)) return
        val upgrades = BotGearUnlocks.find(bot, skillId, levels.first - 1, levels.last)
        requestUpgrades(bot, skillId, upgrades)
    }

    /**
     * Accumulates skipped thresholds, flushes the final level at 99, and suppresses repeated queued events.
     * A first review also covers the preceding odd level when the bot logged in midway through an interval.
     */
    internal fun reviewLevels(bot: Bot, skillId: Int, oldLevel: Int, newLevel: Int): IntRange? {
        if (newLevel <= oldLevel) return null
        val reviewed = bot.reviewedGearLevels
        val previous = reviewed.getOrPut(skillId) { maxOf(1, oldLevel - oldLevel % 2) }
        if (newLevel <= previous || newLevel - previous < 2 && newLevel < 99) return null
        reviewed[skillId] = newLevel
        return previous + 1..newLevel
    }

    /**
     * Promotes missing upgrades to urgent wants while retaining existing quantities and stock metadata.
     * Repeated level gains merge into one queued shopping basket rather than adding duplicate errands.
     */
    internal fun requestUpgrades(bot: Bot, skillId: Int, upgrades: Set<Int>) {
        if (upgrades.isEmpty() && bot.progressionGear.isEmpty()) return
        val useful = refreshUpgrades(bot, bot.progressionGear + upgrades)
        for (id in useful.intersect(upgrades)) {
            val current = bot.preferences.getWantedItem(id)
            if (current == null) bot.createdGearWants.add(id)
            val tag = when {
                !Skill.isCombatSkill(skillId) -> WantedItemTag.TOOL
                equipDef(id).index == Equipment.WEAPON -> WantedItemTag.WEAPON
                else -> WantedItemTag.COMBAT_EQUIPMENT
            }
            bot.preferences.addWantedItem(WantedItemDefinition(id, current?.min ?: -1,
                maxOf(1, current?.target ?: 1), current?.skill ?: -1, current?.maxLevel ?: -1,
                WantedItemPriority.URGENT,
                ImmutableSet.builder<WantedItemTag>().addAll(current?.tags ?: emptySet()).add(tag).build()))
        }
        if (bot.personality.intelligence < 0.85 || !bot.preferences.likesActivity(BotActivity.MERCHANTING)) return
        val queued = bot.scriptStack.scripts.filterIsInstance<ObtainGearBotScript>()
            .firstOrNull { !it.isFinished() && !it.isTerminated() }
        if (queued != null) {
            queued.replace(useful)
        } else if (upgrades.isNotEmpty() && useful.isNotEmpty()) {
            bot.scriptStack.softPushHead(ObtainGearBotScript(bot, useful))
        }
    }

    /**
     * Reconciles progression requests with current ownership, removing only temporary wants created here.
     * Called again when an errand starts so independently acquired upgrades also retire obsolete requests.
     */
    internal fun refreshUpgrades(bot: Bot, candidates: Collection<Int>): Set<Int> {
        val pending = bot.progressionGear
        val useful = BotGearUnlocks.bestUpgrades(bot, pending + candidates)
        for (id in pending - useful) {
            val current = bot.preferences.getWantedItem(id)
            if (id in bot.createdGearWants && current?.min == -1 && current.target == 1) {
                bot.preferences.removeWantedItem(id)
            }
            bot.createdGearWants.remove(id)
        }
        pending.clear()
        pending.addAll(useful)
        return useful.intersect(candidates.toSet())
    }
}
