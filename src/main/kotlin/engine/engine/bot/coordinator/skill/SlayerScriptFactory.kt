package engine.bot.coordinator.skill

import api.bot.action.CombatLoadout
import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import com.google.common.collect.HashMultimap
import engine.bot.coordinator.CombatCoordinator
import engine.bot.gear.BotGearPurpose
import game.bot.scripts.NpcCombatScript
import game.bot.scripts.skills.SlayerBotScript
import game.skill.slayer.Slayer.activeSlayerTask
import game.skill.slayer.SlayerMaster
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType
import kotlin.time.Duration

/**
 * Dispatches native Slayer assignments through the ordinary skilling coordinator in both modes.
 * Assignment rolls, streaks and experience remain owned by native Slayer. Existing combat camps
 * supply readiness thresholds and names; exact task NPC IDs remain authoritative during attacks.
 * Unsupported assignments are retained until their camps and specialist preparation are available.
 *
 * @author lare96
 */
object SlayerScriptFactory : SkillingScriptFactory(SKILL_SLAYER) {
    /**
     * Verified master destinations. Unmapped masters stay unavailable until their subzones are supplied.
     * Assignment dialogue and level eligibility are shared by all five native masters.
     */
    internal val masterZones = mapOf(
        SlayerMaster.TURAEL to SubZone.TURAEL,
        SlayerMaster.MAZCHNA to SubZone.CANIFIS_MAIN,
        SlayerMaster.VANNAKA to SubZone.EDGEVILLE_DUNGEON,
        SlayerMaster.CHAELDAR to SubZone.ZANARIS_MAIN,
        SlayerMaster.DURADEL to SubZone.SHILO_VILLAGE
    )

    /**
     * Applies every native master's combat and Slayer thresholds before considering travel or dialogue.
     */
    internal fun eligibleMasters(combatLevel: Int, slayerLevel: Int): List<SlayerMaster> =
        SlayerMaster.entries.filter { combatLevel >= it.combatLevel && slayerLevel >= it.slayerLevel }

    /**
     * Uses the existing factory personality selection over eligible, mapped masters. Intelligent and
     * dextrous bots prefer the strongest available master; other personalities retain lower-tier choices.
     */
    internal fun selectMaster(bot: Bot): SlayerMaster? {
        if (!canTrain(bot)) return null
        val available = eligibleMasters(bot.combatLevel, bot.slayer.level).filter { it in masterZones }
        return getBestActivity(bot, bot.combatLevel, { it.combatLevel }, available)
    }

    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        SlayerBotScript(bot, getDuration(bot))

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        SlayerBotScript(bot, getDuration(bot))

    /**
     * Excludes level-three skillers and skiller templates before any travel or task request.
     */
    internal fun canTrain(bot: Bot): Boolean = !bot.preferences.isSkiller &&
        bot.personality.type != PersonalityTemplateType.SKILLER

    /**
     * Requires an owned, usable discipline before asking a master for an assignment.
     * The loadout selector also enforces permanent PK build experience restrictions.
     */
    internal fun hasLoadout(bot: Bot): Boolean = canTrain(bot) &&
        disciplines.any { CombatLoadout.find(bot, it) != null }

    /**
     * Matches native task IDs to configured ordinary camps without inheriting the combat coordinator's
     * preference for high tiers: a strong bot must still be able to finish an assigned chicken task.
     */
    internal fun camps(bot: Bot, purpose: BotGearPurpose): Map<SubZone, Set<String>> {
        val task = bot.activeSlayerTask ?: return emptyMap()
        if (!canTrain(bot) || task.remaining <= 0 || bot.slayer.level < task.task.level) return emptyMap()
        val taskNames = task.task.npcs.mapNotNull { NpcDefinition.ALL.get(it).orElse(null)?.name }.toSet()
        val readiness = CombatCoordinator.combatReadiness(bot, purpose)
        return CombatCoordinator.camps.filter {
            it.minimum <= readiness && it.zone !in NpcCombatScript.DRAGON_ZONES
        }.mapNotNull { camp ->
            camp.names.intersect(taskNames).takeIf { it.isNotEmpty() }?.let { camp.zone to it }
        }.toMap()
    }

    /**
     * Builds one assignment-bound session using the shared gear, food, ammunition and rune lifecycle.
     * No task is cancelled or rerolled when equipment, readiness or camp support is missing.
     */
    internal fun combatScript(bot: Bot, duration: Duration): NpcCombatScript? {
        if (!canTrain(bot)) return null
        val task = bot.activeSlayerTask ?: return null
        for (purpose in disciplines.shuffled()) {
            val camps = camps(bot, purpose)
            if (camps.isEmpty()) continue
            val loadout = CombatLoadout.find(bot, purpose) ?: continue
            val names = HashMultimap.create<SubZone, String>()
            camps.forEach { (zone, targets) -> names.putAll(zone, targets) }
            return NpcCombatScript(bot, duration, camps.keys.toMutableList(), names, purpose,
                loadout.spell?.spell, task.task, task.assignee)
        }
        return null
    }

    /**
     * Disciplines supported by the existing NPC combat script and owned loadout resolver.
     */
    private val disciplines = listOf(BotGearPurpose.MELEE, BotGearPurpose.RANGED, BotGearPurpose.MAGIC)

    // TODO Map additional task camps and their required protection/finishing supplies before enabling them.
    // Keep every unmapped assignment and its streak intact; do not filter the master's native task rolls.
}
