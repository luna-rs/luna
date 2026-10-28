package engine.bot.coordinator.skill

import api.bot.action.CombatLoadout
import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import com.google.common.collect.HashMultimap
import engine.bot.coordinator.CombatCoordinator
import engine.bot.coordinator.CombatCoordinator.Companion.Camp
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.KalphiteQueenTrip
import game.bot.scripts.NpcCombatScript
import game.bot.scripts.combat.RockCrabBotScript
import game.bot.scripts.skills.SlayerBotScript
import game.skill.slayer.Slayer.activeSlayerTask
import game.skill.slayer.SlayerMaster
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType
import kotlin.time.Duration

/**
 * Dispatches native Slayer assignments through the ordinary skilling coordinator in both modes.
 * Assignment rolls, streaks and experience remain owned by native Slayer. Existing combat camps
 * supply readiness thresholds and names; exact task NPC IDs remain authoritative during attacks.
 * Boss alternatives reuse ordinary combat preparation: black-dragon tasks may use KBD with an owned one-handed
 * melee loadout and anti-dragon shield, while Kalphite Queen trips require their complete owned melee kit/supplies.
 * Queen sessions contain only the Queen camp so the shared script's entrance and two-form lifecycle remain intact.
 * Additional ordinary assignments use existing mapped mines, sewers, towns and combat areas without changing the
 * general combat coordinator's training or profit distribution. Their sessions retain the same owned supply checks.
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
     * Assignment-only camps verified against native task IDs, cache names and the configured spawn footprints.
     * Shared dungeons retain conservative readiness limits for their other inhabitants: bats in the ogre dungeon
     * or Karamja do not make those areas low-level destinations. These species need no specialist equipment or
     * finishing item; assignments that do remain unavailable until their preparation and geography are supported.
     */
    private val assignmentCamps = listOf(
        Camp(SubZone.YANILLE_DUNGEON_CHAOS_DRUIDS, 30, setOf("Giant bat")),
        Camp(SubZone.CHAOS_DRUID_TOWER_DUNGEON, 55, setOf("Giant bat")),
        Camp(SubZone.KARAMJA_DUNGEON, 65, setOf("Giant bat")),
        Camp(SubZone.AL_KHARID_MINE, 25, setOf("Scorpion")),
        Camp(SubZone.DWARVEN_MINE, 35, setOf("Scorpion")),
        Camp(SubZone.CRAFTING_GUILD_COW_FIELD, 35, setOf("Hobgoblin")),
        Camp(SubZone.ROCK_CRABS, 45, setOf("Hobgoblin")),
        Camp(SubZone.EDGEVILLE_DUNGEON, 45, setOf("Hobgoblin")),
        Camp(SubZone.VARROCK_SEWERS, 50, setOf("Ghost")),
        Camp(SubZone.ARDOUGNE_SQUARE_THIEVING, 50, setOf("Guard dog")),
        Camp(SubZone.ZANARIS_MAIN, 65, setOf("Otherworldly being")),
        Camp(SubZone.NORTH_SEERS_VILLAGE_MAPLES, 75, setOf("Wolf"))
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
     * Specialist readiness uses the selected owned loadout, rather than rejecting all dragon assignments or
     * dispatching an unusable ranged/Magic Queen trip. Missing equipment preserves the existing assignment.
     */
    internal fun camps(bot: Bot, purpose: BotGearPurpose, loadout: CombatLoadout): Map<SubZone, Set<String>> {
        val task = bot.activeSlayerTask ?: return emptyMap()
        if (!canTrain(bot) || task.remaining <= 0 || bot.slayer.level < task.task.level) return emptyMap()
        val taskNames = task.task.npcs.mapNotNull { NpcDefinition.ALL.get(it).orElse(null)?.name }.toSet()
        val readiness = CombatCoordinator.combatReadiness(bot, purpose)
        val hasDragonShield = bot.itemTracker.contains(1540) &&
            loadout.gear[Equipment.WEAPON]?.let { !equipDef(it).isTwoHanded } == true
        val queenReady = purpose == BotGearPurpose.MELEE && KalphiteQueenTrip.ready(bot, loadout)
        return (CombatCoordinator.camps + assignmentCamps).filter {
            it.minimum <= readiness && CombatCoordinator.canUseCamp(it.zone, purpose, hasDragonShield, queenReady) &&
                it.zone.entryBlocker(bot) == null
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
            val loadout = CombatLoadout.find(bot, purpose) ?: continue
            val camps = camps(bot, purpose, loadout)
            if (camps.isEmpty()) continue
            // The Queen's native entrance, supplies and form transition are one dedicated outing.
            var selected = if (SubZone.KALPHITE_QUEEN in camps) {
                mapOf(SubZone.KALPHITE_QUEEN to camps.getValue(SubZone.KALPHITE_QUEEN))
            } else camps
            // Rock crabs sit disguised as rocks, so they need a script that waits for them to wake. The ordinary
            // script trains at the task's other camps, if it has any.
            if (selected[SubZone.ROCK_CRABS]?.contains(RockCrabBotScript.NAME) == true) {
                if (rand(selected.size - 1) == 0) {
                    return RockCrabBotScript(bot, duration, purpose, loadout.spell?.spell, task.task, task.assignee)
                }
                selected = selected - SubZone.ROCK_CRABS
            }
            val names = HashMultimap.create<SubZone, String>()
            selected.forEach { (zone, targets) -> names.putAll(zone, targets) }
            return NpcCombatScript(bot, duration, selected.keys.toMutableList(), names, purpose,
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
