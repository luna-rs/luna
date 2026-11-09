package game.skill.agility.shortcut

import api.predef.*
import api.predef.ext.*
import game.skill.agility.Agility
import game.skill.agility.AgilityAction
import game.skill.agility.AgilityApproach
import game.skill.agility.shortcut.ShortcutMoves.ready
import io.luna.game.model.Entity
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.`object`.GameObject
import kotlin.math.abs

/**
 * The open-world Agility shortcuts that cross from one end to the other: walls, tunnels, fences, railings, logs,
 * rocks, a crevice, a pipe, single stepping stones, rope swings, monkey bars and wall climbs. Each is clicked on any of
 * its objects, from within [AgilityApproach.RADIUS] tiles of an end, and starts from the nearer end; a one-way shortcut
 * starts only from its first end. A shortcut with [rope] set is used by using a rope on it, which it uses up. The
 * stepping stone rows, which are crossed a stone at a time, are in [SteppingStones], and the Slayer Tower's chains,
 * climbed from any side, in [SpikyChains].
 *
 * A shortcut of several parts checks the level only where it is entered: leaving the stretch between two parts needs
 * none, so a player whose boost ran out there is not stuck.
 *
 * @author TheLining
 */
enum class AgilityShortcut(val level: Int,
                           val xp: Double,
                           val objects: List<Pair<Int, Position>>,
                           val ends: List<Position>,
                           val oneWay: Boolean = false,
                           private val levelMessage: String? = null,
                           private val freeFrom: Set<Position> = emptySet(),
                           val rope: Boolean = false,
                           private val reach: InteractionPolicy = InteractionPolicy.STANDARD_SIZE,
                           private val crossing: (Player, Position, Position, GameObject) -> AgilityAction) {

    FALADOR_WALL(5, 0.5, listOf(11844 to Position(2935, 3355)),
                 listOf(Position(2934, 3355), Position(2936, 3355)),
                 crossing = { plr, _, to, _ -> ShortcutMoves.climbOverWall(plr, to) }),

    FALADOR_TUNNEL(26, 0.0, listOf(9309 to Position(2948, 3310), 9310 to Position(2948, 3312)),
                   listOf(Position(2948, 3309), Position(2948, 3313)),
                   crossing = { plr, from, to, _ -> ShortcutMoves.underWall(plr, from, to) }),

    VARROCK_TUNNEL(21, 0.0, listOf(9311 to Position(3189, 3493), 9312 to Position(3192, 3493)),
                   listOf(Position(3188, 3493), Position(3193, 3493)),
                   crossing = { plr, from, to, _ -> ShortcutMoves.underWall(plr, from, to) }),

    YANILLE_TUNNEL(16, 0.0, listOf(9301 to Position(2575, 3108), 9302 to Position(2575, 3111)),
                   listOf(Position(2575, 3107), Position(2575, 3112)),
                   crossing = { plr, from, to, _ -> ShortcutMoves.underWall(plr, from, to) }),

    LUMBRIDGE_FENCE(29, 0.0, listOf(9299 to Position(3240, 3190)),
                    listOf(Position(3240, 3191), Position(3240, 3190)),
                    crossing = { plr, _, to, _ -> ShortcutMoves.squeezeThroughFence(plr, to) }),

    COAL_TRUCK_LOG(20, 8.5, listOf(2296 to Position(2599, 3477, 1), 2296 to Position(2602, 3477, 1)),
                   listOf(Position(2598, 3477), Position(2603, 3477)),
                   crossing = { plr, _, to, _ -> ShortcutMoves.walkLog(plr, to) }),

    // Only a crowdsourced chart gives this log's fail rate.
    ARDOUGNE_LOG(33, 4.0, listOf(9328 to Position(2599, 3336, 1), 9329 to Position(2600, 3336, 1),
                                 9330 to Position(2601, 3336, 1)),
                 listOf(Position(2598, 3336), Position(2602, 3336)), crossing = { plr, from, to, _ ->
        if (ShortcutMoves.keepsFooting(plr.agility.level, 90, 250)) ShortcutMoves.walkLog(plr, to)
        else ShortcutMoves.fallOffLog(plr, from, to, Position(2600, 3335), 2.0)
    }),

    FREMENNIK_LOG(48, 4.0, listOf(9322 to Position(2722, 3595, 1), 9323 to Position(2722, 3594, 1),
                                  9324 to Position(2722, 3593, 1)),
                  listOf(Position(2722, 3596), Position(2722, 3592)), crossing = { plr, from, to, _ ->
        if (ShortcutMoves.keepsFooting(plr.agility.level, 140, 255)) ShortcutMoves.walkLog(plr, to)
        else ShortcutMoves.fallOffLog(plr, from, to, Position(2723, 3594), 2.0)
    }),

    AL_KHARID_ROCKS(38, 0.0, listOf(9331 to Position(3305, 3315), 9332 to Position(3303, 3315)),
                    listOf(Position(3306, 3315), Position(3302, 3315)),
                    crossing = { plr, from, to, _ -> ShortcutMoves.climbRocks(plr, from, to) }),

    DWARVEN_MINE_CREVICE(42, 0.0, listOf(9325 to Position(3029, 9806), 9325 to Position(3034, 9806)),
                         listOf(Position(3028, 9806), Position(3035, 9806)),
                         crossing = { plr, from, to, _ -> ShortcutMoves.squeezeThroughCrevice(plr, from, to) }),

    EDGEVILLE_PIPE(51, 10.0, listOf(9295 to Position(3150, 9906), 9295 to Position(3153, 9906)),
                   listOf(Position(3149, 9906), Position(3155, 9906)),
                   crossing = { plr, from, to, _ -> ShortcutMoves.squeezeThroughPipe(plr, from, to) }),

    MOSS_GIANT_ROPE(10, 3.0, listOf(2322 to Position(2705, 3209)),
                    listOf(Position(2709, 3209), Position(2704, 3209)), oneWay = true,
                    crossing = { plr, _, to, rope -> ShortcutMoves.swing(plr, to, rope) }),

    // The swing back off the island checks no level, so nobody is shut on the island.
    MOSS_GIANT_ROPE_BACK(0, 3.0, listOf(2323 to Position(2703, 3205)),
                         listOf(Position(2705, 3205), Position(2709, 3205)), oneWay = true,
                         crossing = { plr, _, to, rope -> ShortcutMoves.swing(plr, to, rope) }),

    EDGEVILLE_MONKEY_BARS(15, 20.0, listOf(2320 to Position(3119, 9964), 2320 to Position(3120, 9964),
                                           2320 to Position(3119, 9969), 2320 to Position(3120, 9969)),
                          listOf(Position(3120, 9964), Position(3120, 9969)),
                          crossing = { plr, from, to, _ -> ShortcutMoves.swingAcrossBars(plr, from, to) }),

    WATCHTOWER_WALL(18, 31.0, listOf(2299 to Position(2548, 3119)),
                    listOf(Position(2548, 3119), Position(2548, 3118, 1)), oneWay = true,
                    crossing = { plr, from, to, _ -> ShortcutMoves.climbWall(plr, from, to) }),

    TROLLHEIM_EASY(41, 8.0, listOf(9303 to Position(2870, 3671), 9303 to Position(2871, 3671)),
                   listOf(Position(2869, 3671), Position(2872, 3671)),
                   crossing = { plr, from, to, _ -> ShortcutMoves.scramble(plr, from, to, 4) }),

    TROLLHEIM_MEDIUM(43, 8.0, listOf(9304 to Position(2878, 3666), 9304 to Position(2878, 3667)),
                     listOf(Position(2878, 3665), Position(2878, 3668)),
                     crossing = { plr, from, to, _ -> ShortcutMoves.scramble(plr, from, to, 4) }),

    TROLLHEIM_ADVANCED(44, 8.0, listOf(9305 to Position(2908, 3682), 9305 to Position(2909, 3683)),
                       listOf(Position(2907, 3682), Position(2909, 3684)),
                       crossing = { plr, from, to, _ -> ShortcutMoves.scramble(plr, from, to, 4) }),

    TROLLHEIM_HARD(47, 8.0, listOf(9306 to Position(2901, 3680), 9306 to Position(2902, 3680)),
                   listOf(Position(2900, 3680), Position(2903, 3680)),
                   crossing = { plr, from, to, _ -> ShortcutMoves.scramble(plr, from, to, 3) }),

    TROLLHEIM_WILDERNESS_FIRST(64, 0.0, listOf(9327 to Position(2916, 3672), 9327 to Position(2917, 3672)),
                               listOf(Position(2915, 3672), Position(2918, 3672)),
                               freeFrom = setOf(Position(2918, 3672)),
                               crossing = { plr, from, to, _ -> ShortcutMoves.climbRocks(plr, from, to) }),

    TROLLHEIM_WILDERNESS_SECOND(64, 0.0, listOf(9327 to Position(2922, 3672), 9327 to Position(2923, 3673)),
                                listOf(Position(2921, 3672), Position(2924, 3673)),
                                freeFrom = setOf(Position(2921, 3672), Position(2924, 3673)),
                                crossing = { plr, from, to, _ -> ShortcutMoves.climbRocks(plr, from, to) }),

    // The last step only goes down into the Wilderness.
    TROLLHEIM_WILDERNESS_LAST(64, 0.0, listOf(9327 to Position(2947, 3678), 9327 to Position(2948, 3679),
                                              9327 to Position(2949, 3680)),
                              listOf(Position(2946, 3678), Position(2950, 3681)), oneWay = true,
                              freeFrom = setOf(Position(2946, 3678)),
                              crossing = { plr, from, to, _ -> ShortcutMoves.climbRocks(plr, from, to) }),

    COSMIC_WALL_MEDIUM(46, 9.9, listOf(12127 to Position(2400, 4403)),
                       listOf(Position(2400, 4402), Position(2400, 4404)),
                       crossing = { plr, from, to, _ -> ShortcutMoves.squeezePastWall(plr, from, to) }),

    COSMIC_WALL_ADVANCED_SOUTH(66, 9.9, listOf(12127 to Position(2408, 4395)),
                               listOf(Position(2408, 4394), Position(2408, 4396)),
                               freeFrom = setOf(Position(2408, 4396)),
                               crossing = { plr, from, to, _ -> ShortcutMoves.squeezePastWall(plr, from, to) }),

    COSMIC_WALL_ADVANCED_NORTH(66, 9.9, listOf(12127 to Position(2415, 4402)),
                               listOf(Position(2415, 4401), Position(2415, 4403)),
                               freeFrom = setOf(Position(2415, 4401)),
                               crossing = { plr, from, to, _ -> ShortcutMoves.squeezePastWall(plr, from, to) }),

    ECTOPOOL_WALL_UP(58, 0.0, listOf(9307 to Position(3670, 9888, 2)),
                     listOf(Position(3671, 9888, 2), Position(3670, 9888, 3)), oneWay = true,
                     levelMessage = "You need an agility level of at least 58 to climb up this wall.",
                     crossing = { plr, from, to, _ -> ShortcutMoves.jumpUpWall(plr, from, to) }),

    ECTOPOOL_WALL_DOWN(58, 0.0, listOf(9308 to Position(3670, 9888, 3)),
                       listOf(Position(3670, 9888, 3), Position(3671, 9888, 2)), oneWay = true,
                       levelMessage = "You need an agility level of at least 58 to climb down this wall.",
                       crossing = { plr, from, to, _ -> ShortcutMoves.jumpDownWall(plr, from, to) }),

    ARANDAR_EASY(59, 0.0, listOf(9296 to Position(2346, 3299), 9297 to Position(2344, 3295)),
                 listOf(Position(2346, 3300), Position(2344, 3294)),
                 crossing = { plr, from, to, _ -> ShortcutMoves.climbRocks(plr, from, to) }),

    ARANDAR_MEDIUM(68, 0.0, listOf(9296 to Position(2338, 3282), 9297 to Position(2338, 3285)),
                   listOf(Position(2338, 3281), Position(2338, 3286)),
                   crossing = { plr, from, to, _ -> ShortcutMoves.climbRocks(plr, from, to) }),

    ARANDAR_ADVANCED(85, 0.0, listOf(9296 to Position(2333, 3252), 9297 to Position(2337, 3253)),
                     listOf(Position(2332, 3252), Position(2338, 3253)),
                     crossing = { plr, from, to, _ -> ShortcutMoves.climbRocks(plr, from, to) }),

    FREMENNIK_CREVICE(62, 0.0, listOf(9321 to Position(2731, 10008), 9321 to Position(2734, 10008)),
                      listOf(Position(2730, 10008), Position(2735, 10008)),
                      crossing = { plr, from, to, _ -> ShortcutMoves.squeezeThroughCrevice(plr, from, to) }),

    FREMENNIK_BLADES_WEST(81, 5.0, listOf(9326 to Position(2769, 10002)),
                          listOf(Position(2768, 10002), Position(2770, 10002)), freeFrom = setOf(Position(2770, 10002)),
                          crossing = { plr, _, to, _ -> ShortcutMoves.jumpSpikedBlades(plr, to) }),

    FREMENNIK_BLADES_EAST(81, 5.0, listOf(9326 to Position(2774, 10003)),
                          listOf(Position(2773, 10003), Position(2775, 10003)), freeFrom = setOf(Position(2773, 10003)),
                          crossing = { plr, _, to, _ -> ShortcutMoves.jumpSpikedBlades(plr, to) }),

    // TODO Once Priest in Peril is added, only players who have finished it can use the Paterdomus shortcut's three
    //  parts.
    PATERDOMUS_CLIFF_RAILING(65, 0.0, listOf(9337 to Position(3425, 3484)),
                             listOf(Position(3425, 3484), Position(3425, 3483)), freeFrom = setOf(Position(3425, 3483)),
                             crossing = { plr, _, to, _ -> ShortcutMoves.squeezeThroughFence(plr, to) }),

    PATERDOMUS_CLIFF(65, 0.0, listOf(9336 to Position(3426, 3477), 9335 to Position(3425, 3476)),
                     listOf(Position(3426, 3478), Position(3424, 3476)),
                     freeFrom = setOf(Position(3426, 3478), Position(3424, 3476)),
                     crossing = { plr, from, to, _ -> ShortcutMoves.climbRocks(plr, from, to) }),

    PATERDOMUS_BRIDGE_RAILING(65, 0.0, listOf(9334 to Position(3424, 3476)),
                              listOf(Position(3424, 3476), Position(3423, 3476)),
                              freeFrom = setOf(Position(3424, 3476)),
                              crossing = { plr, _, to, _ -> ShortcutMoves.squeezeThroughFence(plr, to) }),

    VARROCK_FENCE(13, 0.0, listOf(9300 to Position(3240, 3335)),
                  listOf(Position(3240, 3335), Position(3240, 3334)),
                  crossing = { plr, _, to, _ -> ShortcutMoves.jumpFence(plr, to) }),

    YANILLE_CLIMBING_ROCKS(5, 25.0, listOf(2298 to Position(2561, 3108)),
                           listOf(Position(2561, 3108), Position(2561, 3111)), oneWay = true,
                           crossing = { plr, from, to, _ -> ShortcutMoves.climbRocksOverWall(plr, from, to) }),

    MCGRUBORS_WOOD_RAILING(0, 0.0, listOf(51 to Position(2662, 3500)),
                           listOf(Position(2661, 3500), Position(2662, 3500)),
                           crossing = { plr, _, to, _ ->
                               ShortcutMoves.squeezeThroughFence(plr, to, ShortcutMoves.LOOSE_RAILING)
                           }),

    GNOME_VILLAGE_RAILING(0, 0.0, listOf(2186 to Position(2515, 3161)),
                          listOf(Position(2515, 3160), Position(2515, 3161)),
                          crossing = { plr, _, to, _ ->
                              ShortcutMoves.squeezeThroughFence(plr, to, ShortcutMoves.LOOSE_RAILING)
                          }),

    SWAMP_CAVES_STONE(1, 3.0, listOf(5948 to Position(3206, 9572)),
                      listOf(Position(3204, 9572), Position(3208, 9572)), reach = ShortcutMoves.ACROSS_WATER,
                      crossing = { plr, _, to, stone -> ShortcutMoves.leapAcrossStone(plr, to, stone.position) }),

    SWAMP_CAVES_FROG_STONE(1, 3.0, listOf(5949 to Position(3221, 9554)),
                           listOf(Position(3221, 9556), Position(3221, 9552)), reach = ShortcutMoves.ACROSS_WATER,
                           crossing = { plr, _, to, stone -> ShortcutMoves.leapAcrossStone(plr, to, stone.position) }),

    OGRE_ISLAND_BRANCH(10, 0.0, listOf(2326 to Position(2499, 3087)),
                       listOf(Position(2501, 3087), Position(2505, 3087)), oneWay = true, rope = true,
                       crossing = { plr, _, to, branch -> ShortcutMoves.swingFromBranch(plr, to, branch) }),

    // The swing off the island checks no level, so nobody is shut on the island.
    OGRE_ISLAND_ROPE(0, 3.0, listOf(2324 to Position(2511, 3090)),
                     listOf(Position(2511, 3092), Position(2511, 3096)), oneWay = true,
                     crossing = { plr, _, to, rope -> ShortcutMoves.swing(plr, to, rope, ShortcutMoves.SKILLFULLY) }),

    UNDERGROUND_PASS_SOUTH_BRIDGE(0, 0.0, listOf(3255 to Position(2161, 4654, 1)),
        listOf(Position(2161, 4653, 1), Position(2161, 4657, 1)),
        crossing = { plr, from, to, _ -> ShortcutMoves.undergroundPassBridge(plr, from, to) }),

    UNDERGROUND_PASS_MIDDLE_BRIDGE(0, 0.0, listOf(3254 to Position(2162, 4663, 1)),
        listOf(Position(2162, 4662, 1), Position(2162, 4666, 1)),
        crossing = { plr, from, to, _ -> ShortcutMoves.undergroundPassBridge(plr, from, to) }),

    UNDERGROUND_PASS_WEST_BRIDGE(0, 0.0, listOf(3255 to Position(2121, 4686, 1)),
        listOf(Position(2120, 4686, 1), Position(2124, 4686, 1)),
        crossing = { plr, from, to, _ -> ShortcutMoves.undergroundPassBridge(plr, from, to) }),

    // TODO Once In Aid of the Myreque is added, only players who have entered Burgh de Rott through its gate can jump
    //  the low fence.
    BURGH_DE_ROTT_FENCE(25, 0.0, listOf(12776 to Position(3474, 3221)),
                        listOf(Position(3473, 3221), Position(3474, 3221)),
                        crossing = { plr, _, to, _ -> ShortcutMoves.jumpFence(plr, to) });

    companion object {

        /**
         * The shortcuts by the id and tile of each object they are clicked on.
         */
        private val byObject = values().flatMap { shortcut -> shortcut.objects.map { it to shortcut } }.toMap()

        /**
         * Every object id clicked to use a shortcut.
         */
        val IDS = values().filterNot { it.rope }.flatMap { shortcut -> shortcut.objects.map { it.first } }.toSet()

        /**
         * Every object id a rope is used on to use a shortcut.
         */
        val ROPE_IDS = values().filter { it.rope }.flatMap { shortcut -> shortcut.objects.map { it.first } }.toSet()

        /**
         * The rope item.
         */
        const val ROPE = 954

        /**
         * How near the object [target] a player must come before a click on it starts its shortcut.
         */
        fun reach(target: Entity): InteractionPolicy =
            (target as? GameObject)?.let { byObject[it.id to it.position]?.reach } ?: InteractionPolicy.STANDARD_SIZE

        /**
         * Starts the shortcut [obj] belongs to, from the end [plr] stands nearer, after walking them onto it.
         */
        fun use(plr: Player, obj: GameObject) {
            val shortcut = byObject[obj.id to obj.position] ?: return
            if (!ready(plr)) return
            val first = shortcut.startEnd(plr.position) ?: return
            val start = shortcut.inColumn(plr.position, shortcut.ends[first])
            val end = shortcut.inColumn(plr.position, shortcut.ends[1 - first])
            val begin = begin@ {
                if (!ready(plr) || plr.position != start || obj.state != EntityState.ACTIVE) return@begin
                if (shortcut.rope && !plr.inventory.contains(ROPE)) return@begin
                if (plr.agility.level < shortcut.requiredLevel(start)) {
                    if (shortcut.levelMessage == null) Agility.checkLevel(plr, shortcut.level)
                    else plr.sendMessage(shortcut.levelMessage)
                    return@begin
                }
                val action = shortcut.crossing(plr, start, end, obj)
                if (action !is ShortcutFall && shortcut.xp > 0) {
                    val then = action.onSuccess
                    action.onSuccess = {
                        then()
                        plr.agility.addExperience(shortcut.xp)
                    }
                }
                plr.submitAction(action)
            }
            if (plr.position == start) begin() else plr.submitAction(AgilityApproach(plr, start) { begin() })
        }
    }

    /**
     * The current Agility level needed from [from], including the free exits between parts of a shortcut.
     */
    fun requiredLevel(from: Position): Int = if (from in freeFrom) 0 else level

    /**
     * The exact starting tile chosen for a click from [position], or `null` when no end is near on that floor.
     */
    fun startingTile(position: Position): Position? = startEnd(position)?.let { inColumn(position, ends[it]) }

    /**
     * The index of the end [position] starts from: the nearer end within [AgilityApproach.RADIUS] tiles on its floor, or
     * only the first end of a one-way shortcut. Ends as near as each other go by steps, so a player standing diagonally
     * beside a fence starts from their own side of it.
     */
    private fun startEnd(position: Position): Int? {
        val candidates = if (oneWay) listOf(0) else ends.indices.toList()
        val near = candidates.filter { ends[it].z == position.z }.minWithOrNull(compareBy(
            { position.computeLongestDistance(inColumn(position, ends[it])) },
            { abs(position.x - inColumn(position, ends[it]).x) + abs(position.y - inColumn(position, ends[it]).y) })) ?: return null
        return if (position.computeLongestDistance(inColumn(position, ends[near])) <= AgilityApproach.RADIUS) near
        else null
    }

    /**
     * [end] in the column [position] is nearer: the monkey bars can be crossed in either of their two columns.
     */
    private fun inColumn(position: Position, end: Position) =
        if (this == EDGEVILLE_MONKEY_BARS) Position(position.x.coerceIn(3120, 3121), end.y) else end
}
