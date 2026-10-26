package game.content.pestControl

import api.predef.*
import api.predef.ext.*
import game.obj.doors.DoorSide
import game.obj.doors.DoorType
import game.obj.doors.Doors
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.GameObject

/**
 * Owns destructible map defences for a round, including both leaves of each gate.
 * Every changed object is restored at settlement, including abandoned games.
 *
 * @author lare96
 */
class PestControlDefences {
    /**
     * A defence retains its original location even while a gate leaf swings aside.
     *
     * @author lare96
     */
    private class Defence(val original: GameObject) {
        var current = original
        var health = 100
        var open = false
    }

    private val sections = mutableListOf<Defence>()
    private val cooldowns = mutableMapOf<Player, Int>()
    private var clock = 0

    fun load() {
        world.locator.findObjects(PestControl.KNIGHT, 40) {
            PestControl.onIsland(it.position) && it.id in setOf(14210, 14211, 14224, 14225, 14226, 14233, 14235)
        }.forEach { sections += Defence(it) }
    }

    fun reset() {
        sections.forEach {
            if (it.current !== it.original) {
                world.removeObject(it.current)
                val old = it.original
                world.addObject(old.id, old.position, old.objectType, old.direction)
            }
        }
        sections.clear()
        cooldowns.clear()
    }

    fun tick() {
        clock++
        cooldowns.entries.removeIf { it.value <= clock }
    }

    fun nearest(position: Position): GameObject? = sections.filter {
        it.health > 0 && !it.open && position.isWithinDistance(it.current.position, 6)
    }.minByOrNull { position.computeLongestDistance(it.current.position) }?.current

    fun damage(position: Position, amount: Int) {
        val section = sections.firstOrNull { it.current.position == position && it.health > 0 } ?: return
        section.health = (section.health - amount.coerceAtLeast(0)).coerceAtLeast(0)
        render(section)
    }

    fun blast(position: Position, amount: Int) {
        sections.filter { it.health > 0 && it.current.position.isWithinDistance(position, 1) }
            .toList().forEach { damage(it.current.position, amount) }
    }

    private fun allowed(player: Player, obj: GameObject): Boolean = PestControl.round.playing(player) &&
        player.isAlive && player.position.isWithinDistance(obj.position, 2) &&
        sections.any { it.current === obj }

    fun repair(player: Player, obj: GameObject) {
        if (!allowed(player, obj) || (cooldowns[player] ?: 0) > clock) return
        val section = sections.first { it.current === obj }
        if (section.health >= 100) return
        if (!player.inventory.contains(2347) || !player.inventory.contains(1511)) {
            player.sendMessage("You need a hammer and some logs to repair the defences.")
            return
        }
        if (!player.inventory.remove(1511)) return
        cooldowns[player] = clock + 4
        player.animation(Animation(3931))
        section.health = (section.health + 25).coerceAtMost(100)
        player.crafting.addExperience(5.0)
        PestControl.round.credit(player, 5)
        render(section)
    }

    fun gate(player: Player, obj: GameObject) {
        if (!allowed(player, obj) || (cooldowns[player] ?: 0) > clock) return
        val clicked = sections.first { it.current === obj }
        if (clicked.original.id !in setOf(14233, 14235) || clicked.health == 0) return
        cooldowns[player] = clock + 2
        val pair = sections.filter {
            it.original.id in setOf(14233, 14235) &&
                it.original.position.isWithinDistance(clicked.original.position, 1)
        }
        val open = !clicked.open
        pair.forEach { it.open = open; render(it) }
    }

    private fun render(section: Defence) {
        val old = section.original
        val id = when (old.id) {
            14210, 14211 -> when {
                section.health == 0 -> 14220 + old.id - 14210
                section.health <= 50 -> 14218 + old.id - 14210
                section.health < 100 -> 14216 + old.id - 14210
                else -> old.id
            }
            in 14224..14226 -> old.id + when {
                section.health == 0 -> 6
                section.health < 100 -> 3
                else -> 0
            }
            else -> old.id + when {
                section.health == 0 -> 12
                section.health <= 25 -> 8
                section.health < 100 -> 4
                else -> 0
            }
        }
        var position = old.position
        var direction = old.direction
        var renderedId = id
        if (old.id == 14233 || old.id == 14235) {
            if (section.open) {
                val type = DoorType(id, id + 1, if (old.id == 14233) DoorSide.LEFT else DoorSide.RIGHT, null, null, 2000)
                val swap = Doors.doubleSwap(old, type, true)
                position = swap.position
                direction = swap.direction
                renderedId++
            }
        }
        val current = section.current
        if (current.id == renderedId && current.position == position && current.direction == direction) return
        world.removeObject(current)
        section.current = world.addObject(renderedId, position, old.objectType, direction)
    }
}
