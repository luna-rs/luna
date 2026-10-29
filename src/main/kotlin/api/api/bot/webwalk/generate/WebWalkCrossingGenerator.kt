package api.bot.webwalk.generate

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.model.ItemRequirement
import api.bot.webwalk.walk.crossing.ConditionalDoorCrossing
import game.content.miningGuild.MiningGuild
import game.content.craftingGuild.CraftingGuild
import game.obj.doors.GnomeStrongholdGate
import game.obj.doors.BrassKeyDoor
import game.obj.doors.JewellersDoor
import game.content.gnomeBall.GnomeBall
import game.obj.doors.TaverleyJailDoor
import game.skill.agility.AgilityEntrances
import api.bot.webwalk.walk.crossing.AgilityShortcutCrossing
import api.bot.webwalk.plan.BotConditions
import game.skill.thieving.lockedDoor.ChaosDruidTowerDoorAction
import game.skill.thieving.lockedDoor.YanilleDoorAction
import io.luna.game.model.mob.Skill
import api.bot.webwalk.walk.crossing.BorderGateCrossing
import api.bot.webwalk.walk.crossing.HamTrapdoorCrossing
import api.bot.webwalk.walk.crossing.WebCrossing
import engine.obj.HamTrapdoor
import game.npc.spawn.alKharid.BorderGate
import game.obj.webs.Web
import io.luna.game.cache.map.MapIndexTable
import io.luna.game.model.Position

/**
 * Where a crossing is, for one whose tiles are given rather than found from a wall, such as a trapdoor that leads into a
 * dungeon.
 *
 * @property objectId The id of the object, in the state that the map places it in.
 * @property pos The position of the object.
 * @property from The tile to cross it from.
 * @property to The tile that crossing it arrives at.
 *
 * @author Hydrozoa
 */
data class CrossingPlacement(val objectId: Int, val pos: Position, val from: Position, val to: Position)

/**
 * A kind of crossing that a [api.bot.webwalk.walk.crossing.CrossingHandler] crosses, and the facts about it that the
 * generator needs to put it in the web. These are facts about game objects, the same kind of thing as the teleport spells that
 * `teleports.jsonc` is made from, so they are written in code and not in a data file.
 *
 * @property handler The name of the handler that crosses it.
 * @property closedObjectIds The ids of the objects, in the state that the map places them in, that make a crossing.
 * @property requirements What a bot needs to cross it, from the tile of the object to the tile on the far side.
 * @property reverseRequirements What a bot needs to cross it back, or `null` for the same as [requirements].
 * @property bidirectional If it can be crossed back.
 * @property cost The time to cross it in game ticks.
 * @property placements Where the crossing is, for one that isn't a straight wall. Empty to place the [closedObjectIds] as
 * walls, the way doors are placed.
 *
 * @author Hydrozoa
 */
data class CrossingSpec(val handler: String,
                        val closedObjectIds: Set<Int>,
                        val requirements: Requirements = Requirements.NONE,
                        val reverseRequirements: Requirements? = null,
                        val bidirectional: Boolean = true,
                        val cost: Int? = null,
                        val placements: List<CrossingPlacement> = emptyList())

/**
 * Every kind of crossing with a handler. To add one, add its spec here and write its handler.
 *
 * @author Hydrozoa
 */
object SpecialCrossings {

    /**
     * The toll gate between Lumbridge and Al Kharid, which costs the same to pass either way. It takes the time to walk to
     * the row of the gate, click, go through the dialogue and step through, which is a guess.
     */
    val BORDER_GATE = CrossingSpec(BorderGateCrossing.ID, setOf(BorderGate.LEFT_LEAF, BorderGate.RIGHT_LEAF),
                                   Requirements(coins = BorderGate.TOLL), cost = 8)

    /**
     * The H.A.M. Hideout trapdoor west of Lumbridge, which is picked open and climbed down. It only goes one way, as the way
     * out is the ladder. It takes the time to pick the lock a few times and climb down, which is a guess: a bot with low
     * Thieving takes longer, but there is no other way in to compare it with.
     */
    val HAM_TRAPDOOR = CrossingSpec(HamTrapdoorCrossing.ID, setOf(HamTrapdoor.ID), cost = 20, bidirectional = false,
                                    placements = listOf(CrossingPlacement(HamTrapdoor.ID, HamTrapdoor.POSITION,
                                                                          HamTrapdoor.USE_FROM, HamTrapdoor.HIDEOUT)))

    /**
     * The Mining Guild door checks the current Mining level on both sides.
     */
    val MINING_GUILD = CrossingSpec(ConditionalDoorCrossing.ID, setOf(MiningGuild.DOOR),
                                    Requirements(skills = mapOf(Skill.MINING to MiningGuild.REQUIRED_LEVEL)), cost = 4)

    /**
     * The Yanille door is unlocked from its own tile; the reverse direction needs a lockpick.
     * The cost includes time for failed attempts before a successful pick.
     */
    val YANILLE_DOOR = CrossingSpec(ConditionalDoorCrossing.ID, setOf(YanilleDoorAction.DOOR),
                                    reverseRequirements = Requirements(
                                        skills = mapOf(Skill.THIEVING to YanilleDoorAction.REQUIRED_LEVEL),
                                        items = listOf(ItemRequirement(YanilleDoorAction.LOCKPICK))), cost = 8)

    /**
     * The Chaos Druid Tower door is picked from outside, the side of its own tile, and opens freely from inside.
     * The cost includes time for failed attempts before a successful pick.
     */
    val CHAOS_DRUID_TOWER_DOOR = CrossingSpec(ConditionalDoorCrossing.ID, setOf(ChaosDruidTowerDoorAction.DOOR),
                                              Requirements(skills = mapOf(
                                                  Skill.THIEVING to ChaosDruidTowerDoorAction.REQUIRED_LEVEL)),
                                              reverseRequirements = Requirements.NONE, cost = 8)

    /**
     * The guild building needs level 40 Crafting and a worn brown apron on entry only.
     * The nearby cow field uses its own unrestricted gate.
     */
    val CRAFTING_GUILD = CrossingSpec(ConditionalDoorCrossing.ID, setOf(CraftingGuild.DOOR),
                                      Requirements(flags = setOf(BotConditions.CRAFTING_GUILD_ENTRY)),
                                      reverseRequirements = Requirements.NONE, cost = 4)

    /**
     * The jeweller's door is open to everyone, but it is a scripted passage rather than a toggle door.
     */
    val JEWELLERS_DOOR = CrossingSpec(ConditionalDoorCrossing.ID, setOf(JewellersDoor.DOOR), cost = 4)

    /**
     * The Taverley Dungeon gate into the western section needs a carried dusty key from outside, the side of its own
     * tile, and opens freely from inside.
     */
    val DUSTY_KEY_GATE = TaverleyJailDoor.DUSTY_KEY_GATE.let {
        CrossingSpec(ConditionalDoorCrossing.ID,
                     setOf(it.closed),
                     Requirements(items = listOf(ItemRequirement(it.key))),
                     reverseRequirements = Requirements.NONE,
                     cost = 4)
    }

    /**
     * The Taverley Dungeon pipe into the western section, which is two objects. The western half is the placement.
     */
    val TAVERLEY_PIPE = with(AgilityEntrances) {
        CrossingSpec(AgilityShortcutCrossing.ID, setOf(TAVERLEY_PIPE),
                     Requirements(skills = mapOf(Skill.AGILITY to TAVERLEY_PIPE_LEVEL)), cost = 10,
                     placements = listOf(CrossingPlacement(TAVERLEY_PIPE, Position(2887, 9799), TAVERLEY_PIPE_WEST,
                                                           TAVERLEY_PIPE_EAST)))
    }

    /**
     * The Taverley Dungeon strange floor into the western section.
     */
    val TAVERLEY_STRANGE_FLOOR = with(AgilityEntrances) {
        CrossingSpec(AgilityShortcutCrossing.ID, setOf(TAVERLEY_FLOOR),
                     Requirements(skills = mapOf(Skill.AGILITY to TAVERLEY_FLOOR_LEVEL)), cost = 4,
                     placements = listOf(CrossingPlacement(TAVERLEY_FLOOR, Position(2879, 9813), TAVERLEY_FLOOR_EAST,
                                                           TAVERLEY_FLOOR_WEST)))
    }

    /**
     * The 16 webs that the map places, each crossed from the tile on one side to the tile on the other. 13 of them
     * separate one area from another, and the 3 in the ruins of the east Wilderness can be walked around. The Yanille
     * hut's east web (2574,3124) has a wall east of it, so it's crossed from inside the hut to the tile south of it.
     * Slashing takes two tries on average, then the web opens 2 ticks later.
     */
    val WEBS = CrossingSpec(WebCrossing.ID, setOf(Web.WEB), Requirements(flags = setOf(BotConditions.SLASH_WEB)),
                            cost = 8,
                            placements = listOf(web(2565, 3124, 2564, 3124, 2566, 3124),
                                                web(2569, 3118, 2569, 3117, 2569, 3119),
                                                web(2570, 3118, 2570, 3117, 2570, 3119),
                                                web(2574, 3124, 2573, 3124, 2574, 3123),
                                                web(2654, 9766, 2654, 9765, 2654, 9767),
                                                web(3030, 3852, 3029, 3852, 3031, 3852),
                                                web(3093, 3957, 3092, 3957, 3094, 3957),
                                                web(3095, 3957, 3094, 3957, 3096, 3957),
                                                web(3105, 3958, 3105, 3957, 3105, 3959),
                                                web(3106, 3958, 3106, 3957, 3106, 3959),
                                                web(3115, 3859, 3115, 3858, 3115, 3860),
                                                web(3147, 3727, 3147, 3726, 3147, 3728),
                                                web(3158, 3951, 3158, 3950, 3158, 3952),
                                                web(3163, 3736, 3163, 3735, 3163, 3737),
                                                web(3183, 3733, 3183, 3732, 3183, 3734),
                                                web(3210, 9898, 3210, 9897, 3210, 9899)))

    /**
     * The house door north of Edgeville, above the hill giants, needs a carried brass key both ways.
     */
    val BRASS_KEY_DOOR = CrossingSpec(ConditionalDoorCrossing.ID, setOf(BrassKeyDoor.DOOR),
                                      Requirements(items = listOf(ItemRequirement(BrassKeyDoor.KEY))), cost = 4)

    /**
     * The Gnome Ball pitch gate is a scripted passage. Going in puts the weapon and shield away, which needs inventory
     * space for them.
     */
    val GNOME_BALL_GATE = CrossingSpec(ConditionalDoorCrossing.ID, setOf(GnomeBall.GATE), cost = 4)

    /**
     * The Tree Gnome Stronghold's main gate, a 5x2 object crossed from the tile in front of its middle to the tile behind it.
     */
    val GNOME_STRONGHOLD_GATE = CrossingSpec(ConditionalDoorCrossing.ID, setOf(GnomeStrongholdGate.GATE), cost = 6,
                                             placements = listOf(CrossingPlacement(GnomeStrongholdGate.GATE,
                                                                                   GnomeStrongholdGate.POSITION,
                                                                                   GnomeStrongholdGate.SOUTH,
                                                                                   GnomeStrongholdGate.NORTH)))
    /**
     * Every spec.
     */
    val ALL = listOf(BORDER_GATE, HAM_TRAPDOOR, MINING_GUILD, YANILLE_DOOR, CHAOS_DRUID_TOWER_DOOR, CRAFTING_GUILD,
                     JEWELLERS_DOOR, BRASS_KEY_DOOR, WEBS, DUSTY_KEY_GATE, TAVERLEY_PIPE, TAVERLEY_STRANGE_FLOOR,
                     GNOME_BALL_GATE, GNOME_STRONGHOLD_GATE)

    /**
     * Places a web at [x],[y], crossed from [fromX],[fromY] to [toX],[toY].
     */
    private fun web(x: Int, y: Int, fromX: Int, fromY: Int, toX: Int, toY: Int) =
        CrossingPlacement(Web.WEB, Position(x, y), Position(fromX, fromY), Position(toX, toY))
}

/**
 * Makes `crossings.jsonc`, which holds the crossings that have a handler, from the [CrossingSpec]s and the map data of the
 * cache. This needs no running world, like [WebWalkDoorGenerator] which it uses to place the crossings that are walls, so a
 * test can run it to make sure that the committed file is still up to date. A crossing with [CrossingSpec.placements] is put
 * at the tiles they give, as long as the map has its object there.
 *
 * @author Hydrozoa
 */
object WebWalkCrossingGenerator {

    /**
     * The result of a generation.
     *
     * @property obstacles The crossings, which are not sorted.
     * @property text The text of `crossings.jsonc`.
     * @property report What was made and what was left out.
     */
    class Result(val obstacles: List<GeneratedObstacle>, val text: String, val report: GenerationReport)

    /**
     * Makes the crossings of the specs that are in the map data.
     *
     * @param table The map data.
     * @param specs The kinds of crossing. An object id may only be in one of them.
     * @return The crossings. A spec with an object that the map doesn't have is reported.
     */
    fun generate(table: MapIndexTable, specs: List<CrossingSpec> = SpecialCrossings.ALL): Result {
        val ids = specs.flatMap { it.closedObjectIds }
        require(ids.size == ids.toSet().size) { "An object is in the specs of more than one crossing: $ids" }

        val report = GenerationReport()
        val crossings = ArrayList<GeneratedObstacle>()
        for (spec in specs) {
            val (placed, placedReport) = if (spec.placements.isEmpty())
                WebWalkDoorGenerator.generate(table, spec.closedObjectIds.associateWith { EdgeType.CROSSING })
            else place(table, spec.placements)
            placedReport.skipped.forEach { report.skipped += it }
            for (id in spec.closedObjectIds) {
                if (placed.none { it.objectId == id }) {
                    report.skipped += "crossing ${spec.handler}: no object $id could be placed in the map"
                }
            }
            for (obstacle in placed) {
                crossings += obstacle.copy(handler = spec.handler, cost = spec.cost, requirements = spec.requirements,
                                           reverseRequirements = spec.reverseRequirements,
                                           bidirectional = spec.bidirectional.takeIf { it != EdgeType.CROSSING.bidirectionalByDefault })
                report.count("${spec.handler} crossings")
            }
        }
        return Result(crossings, WebWalkWriter.obstacles(crossings), report)
    }

    /**
     * Places crossings at the tiles that they are given, if the map data has their objects where they say.
     *
     * @param table The map data.
     * @param placements Where the crossings are.
     * @return The crossings whose objects the map has, and the ones that were left out.
     */
    private fun place(table: MapIndexTable,
                      placements: List<CrossingPlacement>): Pair<List<GeneratedObstacle>, GenerationReport> {
        val report = GenerationReport()
        val placed = ArrayList<GeneratedObstacle>()
        for (placement in placements) {
            if (table.objectSet.none { it.objectId == placement.objectId && it.position == placement.pos }) {
                report.skipped += "CROSSING ${placement.objectId} at ${DoorObstacles.describe(placement.pos)}: not in the map"
                continue
            }
            placed += GeneratedObstacle(EdgeType.CROSSING,
                                        placement.objectId,
                                        placement.pos,
                                        placement.from,
                                        placement.to)
        }
        return Pair(placed, report)
    }
}
