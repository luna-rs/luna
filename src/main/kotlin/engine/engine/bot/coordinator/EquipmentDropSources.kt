package engine.bot.coordinator

import api.bot.zone.SubZone
import engine.combat.drops.StaticNpcDropTable
import game.skill.slayer.SlayerTaskType
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import kotlin.math.min

/**
 * Indexes reviewed repeatable equipment drops against native tables and installed camp spawns once at startup.
 * Unknown, conditional, noted, quest and random-event sources never become ordinary supplier destinations.
 * Discovery reads probabilities without rolling a drop or scanning the live population at activity decisions.
 *
 * @author lare96
 */
internal object EquipmentDropSources {

    /**
     * A native camp and its conservative direct chance of producing one actual target item per credited kill.
     *
     * @author lare96
     */
    data class Source(val itemId: Int, val zone: SubZone, val npcIds: Set<Int>, val names: Set<String>,
                      val chance: Double, val minimumReadiness: Int, val monster: SlayerTaskType?,
                      val spawnCount: Int, val hitpoints: Int, val maximumHit: Int) {
        val key: String get() = "${zone.name}:$itemId"
    }

    /**
     * The reviewed allowlist supplies access/readiness policy, while native data supplies identity and probability.
     *
     * @author lare96
     */
    private data class Review(val item: Int, val zone: SubZone, val npcs: Set<Int>, val minimum: Int,
                              val monster: SlayerTaskType? = null)

    private val reviews by lazy { listOf(
        Review(4129, SubZone.SLAYER_TOWER_SECOND_FLOOR, setOf(1610, 1611), 100, SlayerTaskType.GARGOYLE),
        Review(4131, SubZone.SLAYER_TOWER_SECOND_FLOOR, setOf(1613), 110, SlayerTaskType.NECHRYAEL),
        Review(4127, SubZone.FREMENNIK_SLAYER_DUNGEON, (1637..1642).toSet(), 80, SlayerTaskType.JELLY),
        Review(4125, SubZone.SLAYER_TOWER_FIRST_FLOOR, setOf(1618, 1619), 80, SlayerTaskType.BLOODVELD),
        Review(1357, SubZone.SMOKE_DUNGEON, setOf(1624), 100, SlayerTaskType.DUST_DEVIL),
        Review(1359, SubZone.FREMENNIK_SLAYER_DUNGEON, setOf(1608, 1609), 100, SlayerTaskType.KURASK),
        Review(1359, SubZone.ASGARNIAN_WYVERNS, (3068..3071).toSet(), 115, SlayerTaskType.SKELETAL_WYVERN),
        Review(1361, SubZone.SLAYER_TOWER_SECOND_FLOOR, setOf(1615), 110, SlayerTaskType.ABYSSAL_DEMON),
        Review(1361, SubZone.TAVERLY_BLACK_DEMONS, setOf(84, 677), 110),
        Review(1361, SubZone.EDGEVILLE_DUNGEON, setOf(84, 677), 110),
        Review(1333, SubZone.BRIMHAVEN_DUNGEON_FIRE_GIANTS, setOf(110, 1582, 1583, 1584, 1585, 1586), 65),
        Review(1333, SubZone.WATERFALL_FIRE_GIANTS_WEST, setOf(110, 1582, 1583, 1584, 1585, 1586), 65),
        Review(1333, SubZone.WATERFALL_FIRE_GIANTS_EAST, setOf(110, 1582, 1583, 1584, 1585, 1586), 65)
    ) }
    private var tables: Map<Int, StaticNpcDropTable>? = null
    private var spawns: List<Installed>? = null
    private var entries = emptyList<Source>()
    private var indexed = false

    /**
     * Captures just the installed native identity, location and difficulty required by the reviewed index.
     *
     * @author lare96
     */
    private data class Installed(val id: Int, val name: String, val position: Position,
                                 val hitpoints: Int, val maximumHit: Int)

    fun installTables(loaded: Array<StaticNpcDropTable>) {
        if (indexed) return
        tables = loaded.flatMap { table -> table.ids.map { it to table } }.toMap()
        build()
    }

    fun installSpawns(loaded: Collection<Npc>) {
        if (indexed) return
        val reviewed = reviews.flatMapTo(HashSet()) { it.npcs }
        spawns = loaded.filter { it.id in reviewed }.map {
            Installed(it.id, it.def().name, it.basePosition, it.combatDef().hitpoints, it.combatDef().maximumHit)
        }
        build()
    }

    private fun build() {
        val nativeTables = tables ?: return
        val installed = spawns ?: return
        entries = reviews.mapNotNull { review ->
            val camp = installed.filter { it.id in review.npcs && it.position in review.zone.area }
            if (camp.isEmpty()) return@mapNotNull null
            val probabilities = review.npcs.mapNotNull { id ->
                nativeTables[id]?.let { probability(it, review.item) }?.let { id to it }
            }.toMap()
            if (camp.any { it.id !in probabilities }) return@mapNotNull null
            Source(review.item, review.zone, probabilities.keys, camp.mapTo(HashSet()) { it.name },
                probabilities.values.minOrNull() ?: return@mapNotNull null, review.minimum, review.monster,
                camp.size, camp.maxOf { it.hitpoints }, camp.maxOf { it.maximumHit })
        }
        indexed = true
        tables = null
        spawns = null
    }

    /**
     * Mirrors the native guaranteed/absolute/relative subtable rules for reviewed one-copy direct entries only.
     */
    internal fun probability(table: StaticNpcDropTable, itemId: Int): Double? {
        var missing = 1.0
        var found = false
        for (drops in table.drops.groupBy { it.table }.values) {
            val target = drops.filter { it.id == itemId }
            if (target.isEmpty()) continue
            if (drops.any { it.shared != null || !it.rarity.isFinite() || it.rarity < 0.0 } ||
                target.any { it.noted || it.quantity != "1" || it.shared != null }) return null
            val ordinary = drops.filter { it.rarity < 1.0 }.sumOf { it.rarity }
            val chance = if (target.any { it.rarity >= 1.0 }) 1.0
                else target.sumOf { it.rarity } / maxOf(1.0, ordinary)
            if (chance <= 0.0) return null
            missing *= 1.0 - min(1.0, chance)
            found = true
        }
        return (1.0 - missing).takeIf { found && it > 0.0 }
    }

    fun all(): List<Source> = entries
    fun find(key: String): Source? = entries.firstOrNull { it.key == key }
}
