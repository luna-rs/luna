package game.bot.scripts.skills

import api.bot.script.recentEscape
import api.bot.zone.SubZone
import api.bot.action.CombatLoadout
import api.bot.action.BotCombatActionHandler
import api.predef.*
import api.predef.ext.totalItemAmount
import com.google.common.collect.ImmutableSetMultimap
import com.google.gson.JsonObject
import engine.bot.coordinator.CombatCoordinator
import engine.bot.gear.BotGearPurpose
import game.bot.scripts.NpcCombatScript
import game.bot.scripts.TreasureTrailBotScript
import game.bot.scripts.NpcCombatScript.Companion.NpcCombatData
import game.skill.herblore.identifyHerb.Herb
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.Skill
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Collects herbs from configured Chaos druid and Monk of Zamorak camps using ordinary combat, drops and banking.
 * All herb drops, water vials and snape grass are retained. The requested herb determines the reserve
 * ceiling; a smaller usable haul can finish the trip sooner. Existing combat eligibility and supply checks apply.
 * A productive bank return continues into identification, even for a smaller haul or another usable herb.
 * Identification cannot request another gathering trip, and retains only the remaining session budget.
 *
 * @author lare96
 */
class HerbGatheringScript(
    bot: Bot,
    val herb: Herb,
    duration: Duration,
    zones: MutableList<SubZone> = herbTargets.keySet().toMutableList()
) : NpcCombatScript(bot, duration, zones, herbTargets) {

    companion object {
        /**
         * Readiness limits retained from the removed general-combat camps. These do not re-enter that list.
         */
        private val profitMinimums = mapOf(
            SubZone.CHAOS_DRUID_TOWER to 25,
            SubZone.YANILLE_DUNGEON_CHAOS_DRUIDS to 25,
            SubZone.EDGEVILLE_DUNGEON_CHAOS_DRUIDS to 25,
            SubZone.TAVERLEY_DUNGEON_CHAOS_DRUIDS to 25,
            SubZone.NORTH_FALADOR_CHAOS_TEMPLE to 35,
            SubZone.SOUTH_ARDOUGNE_ZAMORAK_MONKS to 35,
            SubZone.PATERDOMUS_ZAMORAK_MONKS to 35,
            SubZone.LVL_12_WILDERNESS_CHAOS_TEMPLE to 35,
            SubZone.TAVERLEY_DUNGEON_ZAMORAK_MONKS to 55,
            SubZone.LVL_40_WILDERNESS_CHAOS_TEMPLE to 55)

        /**
         * Offers a bounded melee herb trip using owned equipment and currently accessible native camps.
         * The existing gathering script retains exact targets, loot, banking and identification continuation.
         */
        internal fun profitCandidate(bot: Bot, duration: Duration, camp: SubZone? = null): HerbGatheringScript? {
            if (!ActionSafety.NON_COMBAT.allows(bot) ||
                !BotCombatActionHandler.mayGainExperience(bot, Skill.HITPOINTS) ||
                bot.totalItemAmount(Herb.GUAM_LEAF.id) >= IdentifyHerbBotScript.herbTarget(bot)) return null
            val outfit = CombatLoadout.find(bot, BotGearPurpose.MELEE) ?: return null
            if (outfit.gear[Equipment.WEAPON] == null) return null
            val readiness = CombatCoordinator.combatReadiness(bot, BotGearPurpose.MELEE)
            val zones = profitMinimums.filter { (zone, minimum) ->
                (camp == null || camp == zone && bot.recentEscape?.avoids(zone) != true) &&
                    readiness >= minimum && zone.entryBlocker(bot) == null && NpcCombatScript.campSuppliesOwned(bot, zone)
            }.keys.shuffled().take(3).toMutableList()
            if (zones.isEmpty()) return null
            return HerbGatheringScript(bot, Herb.GUAM_LEAF, minOf(duration, 90.minutes), zones)
        }

        /**
         * Existing herb-dropping camps, shared by default zone selection and exact NPC-name restrictions.
         * All configured Zamorak monk areas now use the Chaos druid drop table, including Wilderness temples.
         * The imprisoned Varrock monk is excluded because its drops cannot be reached on foot.
         */
        private val herbTargets = ImmutableSetMultimap.builder<SubZone, String>()
            .put(SubZone.CHAOS_DRUID_TOWER, "Chaos druid")
            .put(SubZone.YANILLE_DUNGEON_CHAOS_DRUIDS, "Chaos druid")
            .put(SubZone.EDGEVILLE_DUNGEON_CHAOS_DRUIDS, "Chaos druid")
            .put(SubZone.TAVERLEY_DUNGEON_CHAOS_DRUIDS, "Chaos druid")
            .put(SubZone.SOUTH_ARDOUGNE_ZAMORAK_MONKS, "Monk of Zamorak")
            .put(SubZone.TAVERLEY_DUNGEON_ZAMORAK_MONKS, "Monk of Zamorak")
            .put(SubZone.NORTH_FALADOR_CHAOS_TEMPLE, "Monk of Zamorak")
            .put(SubZone.PATERDOMUS_ZAMORAK_MONKS, "Monk of Zamorak")
            .put(SubZone.LVL_12_WILDERNESS_CHAOS_TEMPLE, "Monk of Zamorak")
            .put(SubZone.LVL_40_WILDERNESS_CHAOS_TEMPLE, "Monk of Zamorak")
            .build()

        /**
         * Persists the requested herb along with the inherited combat session.
         *
         * @author lare96
         */
        class GatheringData : NpcCombatData() {
            var herb = Herb.GUAM_LEAF

            override fun load(data: JsonObject) {
                super.load(data)
                herb = data.get("herb")?.asString?.let(Herb::valueOf) ?: Herb.GUAM_LEAF
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("herb", herb.name)
            }
        }

        private val lootIds = Herb.entries.map { it.id }.toSet() + setOf(227, 231)
    }

    constructor(bot: Bot, data: GatheringData) : this(bot, data.herb, data.duration, data.zones)

    override fun isLoot(item: GroundItem): Boolean = item.id in lootIds || TreasureTrailBotScript.wantsClue(bot, item.id)

    override suspend fun onAssignFocus(newFocus: Npc): Boolean {
        val targetReached = bot.totalItemAmount(herb.id) >= IdentifyHerbBotScript.herbTarget(bot)
        if (bot.inventory.isFull || targetReached) {
            if (targetReached) forceBanking = true
            return false
        }
        return super.onAssignFocus(newFocus)
    }

    override suspend fun onBankOpen(initial: Boolean) {
        val targetReached = bot.totalItemAmount(herb.id) >= IdentifyHerbBotScript.herbTarget(bot)
        if (!initial || targetReached) {
            val remaining = remainingDuration()
            val identification = Herb.entries.sortedWith(
                compareByDescending<Herb> { it == herb }.thenByDescending { it.exp }
            ).asSequence().map {
                IdentifyHerbBotScript(bot, it, remaining, allowAcquisition = false)
            }.firstOrNull { it.isEligible() }
            if (identification != null || targetReached) {
                if (identification != null && remaining > Duration.ZERO) {
                    bot.scriptStack.softPushHead(identification)
                }
                stop()
                return
            }
        }
        super.onBankOpen(initial)
    }

    override fun snapshot(): GatheringData = GatheringData().also {
        it.herb = herb
        it.duration = duration
        it.zones = originalZones.toMutableList()
    }
}
