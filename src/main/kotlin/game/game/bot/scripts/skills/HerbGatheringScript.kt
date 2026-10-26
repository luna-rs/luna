package game.bot.scripts.skills

import api.bot.zone.SubZone
import com.google.common.collect.ImmutableSetMultimap
import com.google.gson.JsonObject
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.NpcCombatScript
import game.bot.scripts.NpcCombatScript.Companion.NpcCombatData
import game.skill.herblore.identifyHerb.Herb
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Collects herbs from configured Chaos druid and Monk of Zamorak camps using ordinary combat, drops and banking.
 * All herb drops, water vials and snape grass are retained, but only the requested unidentified
 * herb counts toward the identification batch. Existing combat eligibility and supply checks apply.
 * The caller queues identification separately so failed or interrupted gathering cannot loop forever.
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
         * Existing herb-dropping camps, shared by default zone selection and exact NPC-name restrictions.
         * All configured Zamorak monk areas now use the Chaos druid drop table, including Wilderness temples.
         */
        private val herbTargets = ImmutableSetMultimap.builder<SubZone, String>()
            .put(SubZone.CHAOS_DRUID_TOWER, "Chaos druid")
            .put(SubZone.YANILLE_DUNGEON_CHAOS_DRUIDS, "Chaos druid")
            .put(SubZone.EDGEVILLE_DUNGEON_CHAOS_DRUIDS, "Chaos druid")
            .put(SubZone.TAVERLEY_DUNGEON_CHAOS_DRUIDS, "Chaos druid")
            .put(SubZone.SOUTH_ARDOUGNE_ZAMORAK_MONKS, "Monk of Zamorak")
            .put(SubZone.TAVERLEY_DUNGEON_ZAMORAK_MONKS, "Monk of Zamorak")
            .put(SubZone.NORTH_FALADOR_CHAOS_TEMPLE, "Monk of Zamorak")
            .put(SubZone.VARROCK_ZAMORAK_TEMPLE, "Monk of Zamorak")
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

    override fun isLoot(item: GroundItem): Boolean = item.id in lootIds

    override suspend fun onAssignFocus(newFocus: Npc): Boolean {
        if (bot.inventory.isFull || bot.itemTracker.count(herb.id) >= IdentifyHerbBotScript.herbTarget(bot)) {
            forceBanking = true
            return false
        }
        return super.onAssignFocus(newFocus)
    }

    override suspend fun onBankOpen(initial: Boolean) {
        if (bot.itemTracker.count(herb.id) >= IdentifyHerbBotScript.herbTarget(bot)) {
            stop()
            return
        }
        super.onBankOpen(initial)
    }

    override fun snapshot(): GatheringData = GatheringData().also {
        it.herb = herb
        it.duration = duration
        it.zones = originalZones.toMutableList()
    }
}
