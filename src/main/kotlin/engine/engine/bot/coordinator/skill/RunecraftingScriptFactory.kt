package engine.bot.coordinator.skill

import io.luna.game.model.mob.bot.Bot
import api.bot.script.BotScript
import api.bot.script.VoidBotScript
import api.bot.script.recentEscape
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.CraftRuneBotScript
import game.skill.runecrafting.craftRune.CraftableRune
import game.player.item.consume.food.Food
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import engine.bot.coordinator.RuneRunningCoordinator

/**
 * Creates Runecrafting scripts for skilling bots.
 *
 * Funded skiller accounts may host essence runners for experience. Ordinary training ranks supplied,
 * level-eligible runes by native XP per essence. Profit ranks their guide-price return
 * after the compatible essence cost, including the native multiple-rune output at the bot's current level.
 * Intelligence and dexterity apply the same best/worst/random choice used by other skilling factories.
 * These are per-essence comparisons, not fabricated hourly rates or a guarantee that every guide-price sale fills.
 * Healthy bots with owned healing supplies may choose the existing Abyss shortcut, weighted by confidence; its
 * script validates the packed death-loss budget before departure. Other trips retain ordinary talisman entry.
 *
 * @author lare96
 */
object RunecraftingScriptFactory : SkillingScriptFactory(SKILL_RUNECRAFTING) {

    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        RuneRunningCoordinator.crafterScript(bot, getDuration(bot))?.let { return it }
        return createScript(bot, level, zones, training = true)
    }

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        createScript(bot, level, zones, training = false)

    /**
     * Ranks actual rune rewards using a single price snapshot per decision. Owned essence, including stored pouch
     * contents, takes priority; otherwise stocked accessible shops precede a bounded missing-essence request.
     * Profit sessions skip non-positive margins and recheck the actual input after acquisition.
     */
    private fun createScript(bot: Bot, level: Int, zones: MutableList<SubZone>, training: Boolean): BotScript {
        val currentLevel = minOf(level, bot.runecrafting.level)
        val prices = HashMap<Int, Long>()
        fun price(id: Int): Long = prices.getOrPut(id) { bot.world.economy.getPrice(id).coerceAtLeast(0) }
        val candidates = CraftableRune.entries.filter { it.level <= currentLevel }.mapNotNull { rune ->
            val essence = CraftRuneBotScript.essenceForPurchase(bot, rune)
            val score = if (training) rune.exp else
                price(rune.id).toDouble() * rune.outputMultiplier(bot.runecrafting.level) - price(essence)
            if (!training && score <= 0.0) null else Triple(rune, score, essence)
        }
        val supplied = candidates.filter { CraftRuneBotScript.compatibleEssence(bot, it.first) != null }
        val obtainable = candidates.filter { CraftRuneBotScript.canPurchaseEssence(bot, it.third) }
        val scores = supplied.ifEmpty { obtainable.ifEmpty { candidates } }
            .sortedWith(compareByDescending<Triple<CraftableRune, Double, Int>> { it.second }
                .thenByDescending { it.first.level })
        val rune = selectRankedActivity(bot, scores.map { it.first }) ?: return VoidBotScript(bot)
        zones.clear()
        zones.add(rune.altar.zone)
        val abyss = bot.recentEscape?.avoids(SubZone.ABYSS_ENTRY_MAGE) != true &&
            bot.health >= 30 && Food.values().any { it.heal >= 8 && bot.itemTracker.count(it.id) >= 4 } &&
            rand(0.25 + bot.personality.confidence.coerceIn(0.0, 1.0) * 0.5)
        return CraftRuneBotScript(bot, rune.altar, getDuration(bot), viaAbyss = abyss, profit = !training)
    }
}
