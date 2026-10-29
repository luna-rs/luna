/*
 * Random-event behavior adapted from LostCityRS/Content (377-wip) and LostCityRS/Engine-TS,
 * engine commit 1d25566cb53e7af1b1cb18ade8af996316c19614.
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
package game.skill.fishing.fishingSpot

import api.predef.*
import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import game.player.Sound
import game.skill.fishing.catchFish.FishingSpot
import game.skill.fishing.catchFish.Tool
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.task.Task

/**
 * Tracks fishing spots, their random movement and shared whirlpool transformations. Lost City's
 * four ordinary spot categories turn into stationary whirlpools for sixty ticks. Their original
 * form and spawn position return afterward, followed by the normal spot movement timer.
 *
 * @author lare96
 */
object FishingSpotHandler : Task(false, 1) {

    /**
     * A model representing a fishing spot that moves around its [area].
     *
     * @author lare96
     */
    private class MovingSpot(val npc: Npc, val area: FishingSpotArea, var countdown: Int = 1)

    /**
     * Exact transformation ownership, so expiry cannot overwrite a different form or removed NPC.
     *
     * @author lare96
     */
    private class Whirlpool(val originalId: Int, val hazardId: Int, val wandering: Boolean, val expiresAt: Long)

    /**
     * Source temporary player counter, shared across whirlpools and fishing tools. It counts
     * catch attempts after a new click, rather than taking equipment when the hazard appears.
     */
    private var Player.whirlpoolAttempts by Attr.int()

    /**
     * World-owned hazards outlive the triggering player's logout and release the personal event slot.
     */
    private val whirlpools = mutableMapOf<Npc, Whirlpool>()

    /**
     * Identifies both transformed spots and explicit source whirlpool NPCs by their current form.
     */
    fun isWhirlpool(npc: Npc): Boolean = npc.id in 403..406

    /**
     * Supplies a stable identity for one hazard lifetime, used by bots to avoid rerolling decisions.
     * Explicit whirlpool spawns have no expiry and use a fixed token for their NPC lifetime.
     */
    fun whirlpoolExpiry(npc: Npc): Long? = if (isWhirlpool(npc)) whirlpools[npc]?.expiresAt ?: -1L else null

    /**
     * Transforms the existing live spot without creating an extra NPC or interrupting other actions.
     * Already active hazards cannot be extended by another player or another readiness roll.
     */
    fun startWhirlpool(player: Player, npc: Npc, spot: FishingSpot): Boolean {
        val hazardId = spot.whirlpoolId ?: return false
        if (npc.state != EntityState.ACTIVE || npc.id !in spot.npcs || npc in whirlpools) return false
        whirlpools[npc] = Whirlpool(npc.id, hazardId, npc.isWandering, world.currentTick + 60)
        npc.walking.clear()
        npc.navigator.cancel()
        npc.isWandering = false
        npc.transform(hazardId)
        player.playSound(Sound.WHIRLPOOL)
        return true
    }

    /**
     * The fifth hazardous catch roll removes one tool. Whirlpool rolls never consume bait, award
     * fish or grant experience. The normal fishing action checks supplies before reaching this hook.
     */
    fun attemptWhirlpool(player: Player, tool: Tool) {
        if (++player.whirlpoolAttempts < 5) return
        player.whirlpoolAttempts = 0
        if (player.inventory.remove(Item(tool.id))) player.playSound(Sound.WATERSPLASH)
    }

    /**
     * Restores only owned active forms. Native spot respawning resets location to its spawn tile;
     * the existing movement controller then chooses another ordinary tile on its next pass.
     */
    internal fun tickWhirlpools() {
        val iterator = whirlpools.iterator()
        while (iterator.hasNext()) {
            val (npc, state) = iterator.next()
            if (npc.state != EntityState.ACTIVE || npc.id != state.hazardId) {
                iterator.remove()
            } else if (world.currentTick >= state.expiresAt) {
                iterator.remove()
                npc.transform(state.originalId)
                npc.isWandering = state.wandering
                npc.move(npc.basePosition)
                movingSpots.firstOrNull { it.npc === npc }?.countdown = 1
            }
        }
    }
// todo this can probably be dynamically automated. Look for all possible water tiles within half viewing distance,
//  where a tile only has 1 non-water tile at one of its cardinal axes. Then that is a possible teleportation tile.
//  when doing the selection, fail/re-select if another fishing spot is already on that tile
    /**
     * How many ticks a fishing spot waits before moving again.
     */
    private val MOVE_INTERVAL = 280..529

    /**
     * How many random tiles are tried before a fishing spot gives up moving.
     */
    private const val MOVE_ATTEMPTS = 50

    /**
     * The tracked fishing spots.
     */
    private val movingSpots = ArrayList<MovingSpot>()

    /**
     * If the spawned fishing spots have been added to [movingSpots].
     */
    private var loaded = false

    override fun execute() {
        tickWhirlpools()
        if (!loaded) {
            // Global NPC spawns are added before the first tick, so they can all be found here.
            for (npc in world.npcs) {
                val area = FishingSpotArea.NPC_TO_AREA[npc.id] ?: continue
                movingSpots += MovingSpot(npc, area)
            }
            loaded = true
        }
        for (spot in movingSpots) {
            if (spot.npc.state != EntityState.ACTIVE || isWhirlpool(spot.npc)) continue
            if (--spot.countdown <= 0) {
                move(spot)
                spot.countdown = MOVE_INTERVAL.random()
            }
        }
    }

    /**
     * Moves [spot] to a random tile in its area that no other fishing spot is on. The spot stays where it is if no
     * such tile is found.
     */
    private fun move(spot: MovingSpot) {
        repeat(MOVE_ATTEMPTS) {
            val tile = spot.area.tiles.random()
            if (world.locator.findNpcsOnTile(tile) { it.def().name == "Fishing spot" }.isEmpty()) {
                spot.npc.move(tile)
                return
            }
        }
    }
}
