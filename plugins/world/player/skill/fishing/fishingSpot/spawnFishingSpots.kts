package world.player.skill.fishing.fishingSpot

import api.predef.*
import io.luna.game.event.impl.ServerLaunchEvent

/**
 * A list of fishing spots, where all moving fishing spots should be spawned.
 */
val fishingSpots: List<FishingSpot> = listOf()

/**
 * Attempts to move fishing spots from their 'home' to 'away' positions, and vice-versa.
 */
fun moveSpots() {
    fishingSpots.stream()
        .filter { it.countdown() }
        .forEach {
            when (it.position) {
                it.home -> it.teleport(it.away)
                it.away -> it.teleport(it.home)
            }
        }
}

/**
 * Spawns fishing spots.
 */
fun addSpots() = fishingSpots.forEach { world.npcs.add(it) }

// Schedules a task that spawns fishing spots, and attempts to move them every minute.
on(ServerLaunchEvent::class) {
    if (fishingSpots.isNotEmpty()) {
        addSpots()

        world.schedule(100) {
            moveSpots()
        }
    }
}

