/*
 * Adapted from LostCityRS/Content 377-wip, commit 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
 * MIT License
 *
 * Copyright (c) 2023-2026 Lost City
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package game.content.castleWars

import io.luna.game.model.Position

/**
 * Castle Wars match rules derived from Lost City's 377 controller and flag scripts.
 * World interactions validate inventory, distance and equipment before committing these transitions.
 * Times are game ticks: five minutes of waiting followed by twenty minutes of play.
 *
 * @author lare96
 */
class CastleWarsMatch<K : Any> {
    /**
     * Teams and their revision 377 waiting rooms, respawns and standards.
     *
     * @author lare96
     */
    enum class Team(val waiting: Position, val spawn: Position, val home: Position, val banner: Int, val cloak: Int) {
        SARADOMIN(Position(2381, 9489), Position(2427, 3076, 1), Position(2429, 3074, 3), 4037, 4041),
        ZAMORAK(Position(2422, 9524), Position(2372, 3131, 1), Position(2370, 3133, 3), 4039, 4042);

        val enemy: Team get() = if (this == SARADOMIN) ZAMORAK else SARADOMIN
    }

    /**
     * Immutable view of a team's standard; a null carrier means it is standing at position.
     *
     * @author lare96
     */
    data class Flag<K>(val carrier: K?, val position: Position?, val pickedAt: Int)

    /**
     * Changes emitted once by a tick, for the world adapter to apply.
     *
     * @author lare96
     */
    data class Update<K>(val started: Map<K, Team> = emptyMap(), val rewards: Map<K, Int> = emptyMap(),
                         val ended: Boolean = false)

    private val waiting = linkedMapOf<K, Team>()
    private val playing = linkedMapOf<K, Team>()
    private val flags = Team.entries.associateWith { Flag<K>(null, it.home, 0) }.toMutableMap()
    private val scores = IntArray(2)
    private var clock = 0
    var active = false
        private set
    var remaining = 0
        private set
    var warmup = 0
        private set

    fun team(player: K): Team? = playing[player] ?: waiting[player]
    fun isPlaying(player: K): Boolean = player in playing
    fun isWaiting(player: K): Boolean = player in waiting
    fun players(): Map<K, Team> = playing.toMap()
    fun waitingPlayers(): Map<K, Team> = waiting.toMap()
    fun score(team: Team): Int = scores[team.ordinal]
    fun flag(team: Team): Flag<K> = flags.getValue(team)
    fun carried(player: K): Team? = Team.entries.firstOrNull { flags.getValue(it).carrier == player }

    fun join(player: K, preferred: Team? = null): Boolean {
        if (team(player) != null) return false
        val sara = waiting.values.count { it == Team.SARADOMIN }
        val zammy = waiting.size - sara
        val chosen = preferred ?: if (sara <= zammy) Team.SARADOMIN else Team.ZAMORAK
        if (chosen == Team.SARADOMIN && sara > zammy || chosen == Team.ZAMORAK && zammy > sara) return false
        waiting[player] = chosen
        return true
    }

    fun joinInProgress(player: K): Boolean {
        val team = waiting[player] ?: return false
        if (!active || playing.values.count { it == team } >= playing.values.count { it == team.enemy }) return false
        waiting.remove(player)
        playing[player] = team
        return true
    }

    fun requeue(player: K, team: Team) {
        check(this.team(player) == null)
        waiting[player] = team
    }

    fun leave(player: K, position: Position) {
        drop(player, position)
        waiting.remove(player)
        playing.remove(player)
    }

    fun tick(): Update<K> {
        clock++
        if (active) {
            if (--remaining > 0) return Update()
            val winner = when {
                scores[0] > scores[1] -> Team.SARADOMIN
                scores[1] > scores[0] -> Team.ZAMORAK
                else -> null
            }
            val rewards = playing.mapValues { (_, team) -> if (winner == null) 2 else if (winner == team) 3 else 0 }
            playing.clear()
            Team.entries.forEach(::resetFlag)
            active = false
            return Update(rewards = rewards, ended = true)
        }
        if (warmup == 0) {
            if (Team.entries.any { it !in waiting.values }) return Update()
            warmup = 500
        }
        if (--warmup > 0) return Update()
        if (Team.entries.any { it !in waiting.values }) return Update()
        scores.fill(0)
        Team.entries.forEach(::resetFlag)
        playing.putAll(waiting)
        waiting.clear()
        active = true
        remaining = 2000
        return Update(started = playing.toMap())
    }

    fun pickup(player: K, flagTeam: Team, atOwnBase: Boolean): Boolean {
        val team = playing[player] ?: return false
        val flag = flag(flagTeam)
        if (!active || flag.carrier != null || carried(player) != null) return false
        if (team == flagTeam) {
            if (flag.position == flagTeam.home) return false
            if (atOwnBase) { resetFlag(flagTeam); return true }
        }
        flags[flagTeam] = Flag(player, null, clock)
        return true
    }

    fun capture(player: K): Boolean {
        val team = playing[player] ?: return false
        val carried = carried(player) ?: return false
        resetFlag(carried)
        if (carried != team) scores[team.ordinal]++
        return true
    }

    fun drop(player: K, position: Position): Boolean {
        val carried = carried(player) ?: return false
        flags[carried] = Flag(null, position, clock)
        return true
    }

    fun canTakeFrom(player: K, other: K): Boolean {
        val team = playing[player] ?: return false
        if (playing[other] != team || carried(player) != null) return false
        val flag = flag(team.enemy)
        return flag.carrier == other && clock - flag.pickedAt > 300
    }

    fun takeFrom(player: K, other: K): Boolean {
        if (!canTakeFrom(player, other)) return false
        val team = playing.getValue(player)
        flags[team.enemy] = Flag(player, null, clock)
        return true
    }

    private fun resetFlag(team: Team) {
        flags[team] = Flag(null, team.home, clock)
    }
}
