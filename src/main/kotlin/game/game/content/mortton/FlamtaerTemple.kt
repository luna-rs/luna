package game.content.mortton

import api.attr.Attr
import api.predef.crafting
import api.predef.ext.addObject
import api.predef.ext.animation
import api.predef.ext.removeObject
import api.predef.ext.scheduleOnce
import game.player.Animations
import game.player.Sound
import game.skill.firemaking.Firemaking
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import java.util.WeakHashMap

/**
 * Shared Flamtaer temple repairs and each participant's Sanctity for blessing oil.
 *
 * @author lare96
 */
object FlamtaerTemple {
    const val HAMMER = 2347
    const val FLAMTAER_HAMMER = 3678
    const val CRAFTING_LEVEL = 20
    const val PLANK = 960
    const val LIMESTONE_BRICK = 3420
    const val PASTE_PER_REPAIR = 5
    const val REPAIRS_TO_REBUILD = 10
    const val BROKEN_ALTAR = 4092
    const val UNLIT_ALTAR = 4091
    const val FLAME = 4090
    val ALTAR_POSITION = Position(3506, 3316)
    val walls = 4068..4089
    val oliveOils = listOf(3422, 3424, 3426, 3428)
    val sacredOils = listOf(3430, 3432, 3434, 3436)

    private const val SANCTITY_REQUIRED = 10
    private const val SANCTITY_PER_REPAIR = 10
    private const val IDLE_TICKS = 500
    private val materials = listOf(Item(PLANK), Item(LIMESTONE_BRICK),
        Item(ShadeRewards.SWAMP_PASTE, PASTE_PER_REPAIR))
    private var Player.sanctity by Attr.int { 0 }
    private var Player.sanctityTick by Attr.long { 0L }
    private val temples = WeakHashMap<World, Temple>()

    /**
     * Active replacements and the last contribution to the shared rebuild.
     *
     * @author lare96
     */
    private class Temple {
        var repairs = 0
        var lastWork = 0L
        val originals = mutableMapOf<Position, GameObject>()
        val replacements = mutableMapOf<Position, GameObject>()
    }

    fun repairsRemaining(world: World): Int =
        REPAIRS_TO_REBUILD - (temples[world]?.repairs ?: 0)

    fun sanctity(player: Player): Int =
        (player.sanctity - ((player.world.currentTick - player.sanctityTick) / 100).toInt()).coerceAtLeast(0)

    fun repair(player: Player, wall: GameObject) {
        if (wall.state != EntityState.ACTIVE) return
        if (player.crafting.level < CRAFTING_LEVEL) {
            player.sendMessage("You need level 20 Crafting to repair the temple.")
            return
        }
        if (!player.inventory.contains(HAMMER) && !player.inventory.contains(FLAMTAER_HAMMER)) {
            player.sendMessage("You need a hammer to repair the temple.")
            return
        }
        if (!player.inventory.containsAll(materials) || !player.inventory.removeAll(materials)) {
            player.sendMessage("You need a plank, a limestone brick and five swamp paste to repair the temple.")
            return
        }
        val world = player.world
        val temple = temples.getOrPut(world) { Temple().also { expire(world, it, IDLE_TICKS) } }
        val finishedWall = if (wall.id >= 4079) 4089 else 4078
        if (wall.id < finishedWall) replace(world, temple, wall, wall.id + 1)
        temple.lastWork = world.currentTick
        updateSanctity(player, (sanctity(player) + SANCTITY_PER_REPAIR).coerceAtMost(100))
        player.animation(Animations.CUT_LOG)
        player.playSound(Sound.HAMMER_AND_BUILD)
        player.crafting.addExperience(5.0)
        if (temple.repairs < REPAIRS_TO_REBUILD && ++temple.repairs == REPAIRS_TO_REBUILD) {
            val altar = world.locator.findObjectsOnTile(ALTAR_POSITION) { it.id == BROKEN_ALTAR }.firstOrNull()
            if (altar != null) replace(world, temple, altar, UNLIT_ALTAR)
            player.sendMessage("The temple has been repaired. The sacred flame can now be lit.")
        } else {
            player.sendMessage("You work on the temple. Your Sanctity is now ${sanctity(player)}%.")
        }
    }

    fun light(player: Player, altar: GameObject) {
        val temple = temples[player.world] ?: return
        if (altar.state != EntityState.ACTIVE || altar.id != UNLIT_ALTAR) return
        if (!player.inventory.contains(Firemaking.TINDERBOX) || sanctity(player) < SANCTITY_REQUIRED) {
            player.sendMessage("You need a tinderbox and at least 10% Sanctity to light the altar.")
            return
        }
        replace(player.world, temple, altar, FLAME)
        temple.lastWork = player.world.currentTick
        player.animation(Animations.FIREMAKING)
        player.playSound(Sound.FIRE_LIT)
        player.sendMessage("You light the sacred flame.")
    }

    fun bless(player: Player, altar: GameObject, slot: Int, oil: Int) {
        val doseIndex = oliveOils.indexOf(oil)
        if (doseIndex < 0 || player.inventory[slot]?.id != oil || altar.state != EntityState.ACTIVE) return
        val points = sanctity(player)
        if (altar.id != FLAME || points < SANCTITY_REQUIRED) {
            player.sendMessage("You need the sacred flame and at least 10% Sanctity to bless the oil.")
            return
        }
        updateSanctity(player, points - (4 - doseIndex))
        player.inventory[slot] = Item(sacredOils[doseIndex])
        player.animation(Animations.PICKPOCKET)
        player.sendMessage("You bless the olive oil in the sacred flame.")
    }

    private fun updateSanctity(player: Player, amount: Int) {
        player.sanctity = amount
        player.sanctityTick = player.world.currentTick
    }

    private fun replace(world: World, temple: Temple, existing: GameObject, id: Int) {
        if (existing.position !in temple.originals) temple.originals[existing.position] = existing
        temple.replacements[existing.position] =
            world.addObject(id, existing.position, existing.objectType, existing.direction)
    }

    private fun expire(world: World, temple: Temple, delay: Int) {
        world.scheduleOnce(delay) {
            val remaining = IDLE_TICKS - (world.currentTick - temple.lastWork)
            if (remaining > 0) {
                expire(world, temple, remaining.toInt())
            } else {
                temples.remove(world)
                for ((position, replacement) in temple.replacements) {
                    if (replacement.state == EntityState.ACTIVE && world.removeObject(replacement)) {
                        val base = temple.originals.getValue(position)
                        world.addObject(base.id, base.position, base.objectType, base.direction)
                    }
                }
            }
        }
    }
}
