package game.skill.agility.shortcut

import api.predef.*
import api.predef.ext.*
import game.skill.agility.Agility
import game.skill.agility.AgilityAction
import game.skill.agility.AgilityApproach
import game.skill.agility.shortcut.ShortcutMoves.ready
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * The open-world Agility shortcuts that cross from one end to the other: walls, tunnels, a fence, logs, rocks, a
 * crevice, a pipe, rope swings, monkey bars and the Watchtower climb. Each is clicked on any of its objects, from
 * within [AgilityApproach.RADIUS] tiles of an end, and starts from the nearer end; a one-way shortcut starts only from
 * its first end. The stepping stones, which are crossed a stone at a time, are in [SteppingStones].
 *
 * @author TheLining
 */
enum class AgilityShortcut(val level: Int,
                           val xp: Double,
                           val objects: List<Pair<Int, Position>>,
                           val ends: List<Position>,
                           val oneWay: Boolean = false,
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
                    crossing = { plr, from, to, _ -> ShortcutMoves.climbWall(plr, from, to) });

    companion object {

        /**
         * The shortcuts by the id and tile of each object they are clicked on.
         */
        private val byObject = values().flatMap { shortcut -> shortcut.objects.map { it to shortcut } }.toMap()

        /**
         * Every object id clicked to use a shortcut.
         */
        val IDS = values().flatMap { shortcut -> shortcut.objects.map { it.first } }.toSet()

        /**
         * Starts the shortcut [obj] belongs to, from the end [plr] stands nearer, after walking them onto it.
         */
        fun use(plr: Player, obj: GameObject) {
            val shortcut = byObject[obj.id to obj.position] ?: return
            if (!ready(plr)) return
            val first = shortcut.startEnd(plr) ?: return
            val start = shortcut.inColumn(plr, shortcut.ends[first])
            val end = shortcut.inColumn(plr, shortcut.ends[1 - first])
            val begin = begin@ {
                if (!ready(plr) || plr.position != start || obj.state != EntityState.ACTIVE) return@begin
                if (shortcut.level > 0 && !Agility.checkLevel(plr, shortcut.level)) return@begin
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
     * The index of the end [plr] starts from: the nearer end within [AgilityApproach.RADIUS] tiles on their floor, or
     * only the first end of a one-way shortcut.
     */
    private fun startEnd(plr: Player): Int? {
        val candidates = if (oneWay) listOf(0) else ends.indices.toList()
        val near = candidates.filter { ends[it].z == plr.z }
            .minByOrNull { plr.position.computeLongestDistance(inColumn(plr, ends[it])) } ?: return null
        return if (plr.position.computeLongestDistance(inColumn(plr, ends[near])) <= AgilityApproach.RADIUS) near
        else null
    }

    /**
     * [end] in the column [plr] is nearer: the monkey bars can be crossed in either of their two columns.
     */
    private fun inColumn(plr: Player, end: Position) =
        if (this == EDGEVILLE_MONKEY_BARS) Position(plr.x.coerceIn(3120, 3121), end.y) else end
}
