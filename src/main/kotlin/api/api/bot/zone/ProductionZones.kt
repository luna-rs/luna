package api.bot.zone

import api.bot.action.trading.BotMarketplace
import api.predef.world
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot

/**
 * Shared native facility and portable-production destinations.
 * Recipes retain their own supplies and player interactions. These pools describe geography rather than
 * selecting a recipe or ranking current occupancy. Portable work tiles are inspected only after arrival;
 * furnace, anvil and other object-backed activities keep their ordinary interaction positions.
 *
 * @author lare96
 */
object ProductionZones {
    /**
     * Native anvils with ordinary parent banking; existing Varrock smithing remains available.
     */
    val ANVILS = listOf(SubZone.HOME, SubZone.SEERS_VILLAGE_ANVILS,
        SubZone.YANILLE_ANVILS, SubZone.RELLEKKA_WORKSHOP)

    /**
     * Native Smelt furnaces shared by ores, jewellery, cannonballs and molten glass.
     */
    val FURNACES = listOf(SubZone.AL_KHARID_BANK, SubZone.FALADOR_WEST_BANK,
        SubZone.LUMBRIDGE_FURNACE, SubZone.ARDOUGNE_FURNACE, SubZone.RELLEKKA_WORKSHOP,
        SubZone.PORT_PHASMATYS_FURNACE, SubZone.SHILO_VILLAGE_FURNACE)

    /**
     * Registered ranges with an authenticated native object and an ordinary player approach.
     */
    val RANGES = listOf(SubZone.LUMBRIDGE_CASTLE_RANGE, SubZone.LUMBRIDGE_SOUTH_RANGE,
        SubZone.CATHERBY_SOUTH_RANGE, SubZone.CATHERBY_NORTH_RANGE, SubZone.SEERS_VILLAGE_RANGE,
        SubZone.ARDOUGNE_NORTH_RANGE, SubZone.ARDOUGNE_SOUTH_RANGE, SubZone.YANILLE_WEST_RANGE,
        SubZone.FALADOR_EAST_RANGE)

    /**
     * Workshops containing both native shaping wheels and firing ovens.
     */
    val POTTERY = listOf(SubZone.BARBARIAN_VILLAGE, SubZone.ARDOUGNE_POTTERY, SubZone.RELLEKKA_WORKSHOP)

    /**
     * Native spinning wheels. The existing Seers wheel keeps its explicit upstairs access helper.
     */
    val SPINNING = listOf(SubZone.FLAX_SPINNING_MAIN, SubZone.SEERS_VILLAGE_MAIN,
        SubZone.LUMBRIDGE_SPINNING_WHEEL, SubZone.FALADOR_SPINNING_WHEEL, SubZone.RELLEKKA_WORKSHOP,
        SubZone.GNOME_STRONGHOLD_WEST_SPINNING, SubZone.GNOME_STRONGHOLD_EAST_SPINNING,
        SubZone.GNOME_STRONGHOLD_NORTH_SPINNING)

    /**
     * Public bank work areas kept separate from overlapping furnace and firemaking destinations.
     */
    val PORTABLE = setOf(SubZone.FALADOR_WEST_BANK_WORK_AREA, SubZone.FALADOR_EAST_BANK_WORK_AREA,
        SubZone.CATHERBY_BANK_WORK_AREA, SubZone.ARDOUGNE_NORTH_BANK_WORK_AREA,
        SubZone.ARDOUGNE_SOUTH_BANK_WORK_AREA, SubZone.YANILLE_BANK_WORK_AREA,
        SubZone.PORT_PHASMATYS_BANK_WORK_AREA)

    /**
     * Public water sources accepted by the existing generic filling action.
     */
    val WATER = listOf(SubZone.SOUTH_FALADOR_FARM, SubZone.EDGEVILLE_MAIN, SubZone.VARROCK_SQUARE,
        SubZone.YANILLE_WATER, SubZone.ARDOUGNE_WATER, SubZone.SEERS_VILLAGE_WATER)

    /**
     * Sand pits without an additional unsupported transport or equipment restriction.
     */
    val SAND = listOf(SubZone.YANILLE_SAND_PIT, SubZone.RELLEKKA_SAND_PIT, SubZone.ZANARIS_SAND_PIT)

    /**
     * Actual milkable Dairy Cow objects, independent of combat cattle NPCs.
     */
    val MILK = listOf(SubZone.FALADOR_DAIRY_COW, SubZone.LUMBRIDGE_DAIRY_COW,
        SubZone.EAST_LUMBRIDGE_DAIRY_COW, SubZone.ARDOUGNE_DAIRY_COW, SubZone.RELLEKKA_DAIRY_COW,
        SubZone.YANILLE_DAIRY_COW, SubZone.SEERS_VILLAGE_DAIRY_COW)

    /**
     * Existing Seers flax and connected native field clusters in Rellekka and the Gnome Stronghold.
     */
    val FLAX = listOf(SubZone.SOUTH_SEERS_VILLAGE_FLAX, SubZone.RELLEKKA_FLAX_1,
        SubZone.GNOME_STRONGHOLD_FLAX_1, SubZone.GNOME_STRONGHOLD_FLAX_2,
        SubZone.GNOME_STRONGHOLD_FLAX_3, SubZone.GNOME_STRONGHOLD_FLAX_4,
        SubZone.GNOME_STRONGHOLD_FLAX_5, SubZone.GNOME_STRONGHOLD_FLAX_6,
        SubZone.GNOME_STRONGHOLD_FLAX_7)

    /**
     * Retains skill-themed marketplaces and the existing Draynor fallback, adding public bank work areas.
     */
    fun portableForSkill(skill: Int): MutableList<SubZone> =
        (BotMarketplace.entries.filter { it.skills.isEmpty() || skill in it.skills }
            .map { it.activityZone } + SubZone.DRAYNOR_MAIN + PORTABLE).distinct().toMutableList()

    /**
     * Tests a work tile against current collision and player/NPC footprints after reaching its area.
     */
    fun canWorkAt(bot: Bot, zone: SubZone, tile: Position): Boolean =
        tile in zone.area && !world.collisionManager.isBlocked(tile, false) && tile !in occupied(bot, zone)

    /**
     * Finds visible free work positions in the arrival tile's live local component. This is a bounded
     * after-arrival check, never a reason to prefer or exclude a zone before travelling there.
     */
    fun availableWorkPositions(bot: Bot, zone: SubZone): List<Position> {
        require(zone in PORTABLE)
        val collision = world.collisionManager
        if (collision.isBlocked(zone.inside, false)) return emptyList()
        val reached = linkedSetOf(zone.inside)
        val queue = ArrayDeque<Position>().also { it.add(zone.inside) }
        while (queue.isNotEmpty()) {
            val from = queue.removeFirst()
            for (direction in Direction.NESW) {
                val next = from.translate(1, direction)
                if (next in zone.area && next !in reached &&
                    collision.traversable(from, EntityType.PLAYER, direction)) {
                    reached.add(next)
                    queue.add(next)
                }
            }
        }
        val occupied = occupied(bot, zone)
        return reached.filter { bot.position.isViewable(it) && it !in occupied && !collision.isBlocked(it, false) }
    }

    /**
     * Collects only footprints intersecting this arrived work area, including large NPCs whose origins are outside
     * its bounds. Clipping before tile allocation keeps distant world populations out of each work-cycle set.
     */
    private fun occupied(bot: Bot, zone: SubZone): Set<Position> = buildSet {
        val area = zone.area
        world.players.filter { it !== bot && it.state == EntityState.ACTIVE && it.position in area }
            .forEach { add(it.position) }
        world.npcs.filter { it.state == EntityState.ACTIVE }.forEach { npc ->
            val origin = npc.position
            if (origin.z != zone.inside.z) return@forEach
            val size = npc.size()
            val minX = maxOf(origin.x, area.southWest.x)
            val minY = maxOf(origin.y, area.southWest.y)
            val maxX = minOf(origin.x + size - 1, area.northEast.x)
            val maxY = minOf(origin.y + size - 1, area.northEast.y)
            for (x in minX..maxX) for (y in minY..maxY) add(Position(x, y, origin.z))
        }
    }
}
