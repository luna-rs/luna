package game.content.chompyHunting

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import io.luna.game.model.EntityState
import io.luna.game.model.LocalGraphic
import io.luna.game.model.Position
import io.luna.game.model.collision.CollisionFlag
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.net.msg.out.ClearHintMessageWriter
import io.luna.net.msg.out.MobHintMessageWriter
import java.lang.ref.WeakReference

/**
 * A bloated toad left on the ground as bait. Every 25 ticks it may draw a chompy bird down, and after four rolls it
 * bursts, hurting anyone standing next to it.
 *
 * @author TheLining
 */
class BloatedToad(baiter: Player, position: Position) : Npc(baiter.context, ChompyHunting.BLOATED_TOAD_NPC, position) {

    /**
     * The player who placed the toad.
     */
    private val baiter = WeakReference(baiter)

    /**
     * How many times the toad has rolled for a chompy bird.
     */
    private var rolls = 0

    /**
     * Whether a chompy bird has already been drawn down by this toad.
     */
    private var lured = false

    /**
     * Whether a chompy bird is eating the toad.
     */
    var eaten = false
        private set

    init {
        respawnTicks = -1
    }

    override fun onSpawn() {
        world.schedule(25) { task ->
            if (state != EntityState.ACTIVE || eaten) {
                task.cancel()
                return@schedule
            }
            val burst = rolls == 3
            rolls++
            if (!lured && rand(4) == 1) {
                lured = true
                spawnChompy()
            }
            if (burst) {
                task.cancel()
                burst()
            }
        }
    }

    /**
     * A chompy bird lands near the toad.
     */
    private fun spawnChompy() {
        val owner = baiter.get() ?: return
        val tile = landingTile() ?: return
        val chompy = ChompyBird(owner, tile)
        if (!world.npcs.add(chompy)) return
        chompy.land()
    }

    /**
     * A free tile four tiles away from the toad.
     */
    private fun landingTile(): Position? {
        val view = world.collisionManager.view(false)
        val blocked = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or CollisionFlag.BLOCK_NPCS or 0xff
        return (-4..4).flatMap { x -> (-4..4).map { y -> position.translate(x, y) } }
            .filter { it.computeLongestDistance(position) == 4 }
            .shuffled().firstOrNull { view.get(it.x, it.y, it.z) and blocked == 0 }
    }

    /**
     * The toad bursts, hitting everyone on or next to it for one or two damage.
     */
    private fun burst() {
        LocalGraphic(context, ChompyHunting.TOAD_EXPLODE_GRAPHIC, 0, 30, position,
                     ChunkUpdatableView.globalView()).display()
        for (plr in world.players) {
            if (plr.state != EntityState.ACTIVE || !plr.isAlive ||
                plr.position.computeLongestDistance(position) > 1 || plr.position.z != position.z) continue
            plr.sendMessage("You're hit by bits of exploding toad!")
            plr.playSound(Sound.TOAD_BURST)
            plr.damage(rand(1, 2))
        }
        world.npcs.remove(this)
    }

    /**
     * A chompy bird eats the toad: it croaks once and is gone.
     */
    fun beEaten() {
        if (eaten) return
        eaten = true
        world.scheduleOnce(3) {
            if (state != EntityState.ACTIVE) return@scheduleOnce
            speak("!!Croak!!")
            world.scheduleOnce(2) { if (state == EntityState.ACTIVE) world.npcs.remove(this) }
        }
    }
}

/**
 * A chompy bird drawn down by a player's bloated toad. It goes for the nearest bait, never fights back, and only
 * the player who baited it can shoot it.
 *
 * @author TheLining
 */
class ChompyBird(baiter: Player, position: Position) : Npc(baiter.context, ChompyHunting.CHOMPY_BIRD, position) {

    /**
     * The player whose toad drew the bird down.
     */
    private val baiter = WeakReference(baiter)

    /**
     * The tick the bird finishes eating, or 0 while it isn't eating.
     */
    private var eatingUntil = 0L

    /**
     * The tick the bird gulps the bait down, or 0 once it has.
     */
    private var gobbleAt = 0L

    init {
        respawnTicks = -1
    }

    /**
     * Whether [plr] baited this bird.
     */
    fun isBaitedBy(plr: Player) = baiter.get() === plr

    /**
     * The bird swoops down and squawks.
     */
    fun land() {
        combat.isDisabled = true
        animation(Animation(LANDING_ANIMATION))
        speak("Sqwirk!")
        baiter.get()?.queue(MobHintMessageWriter(this))
        val leaves = world.currentTick + LIFE_TICKS
        world.schedule(1) { task ->
            if (state != EntityState.ACTIVE || !isAlive) {
                task.cancel()
                return@schedule
            }
            if (world.currentTick >= leaves) {
                task.cancel()
                clearHint()
                world.npcs.remove(this)
                return@schedule
            }
            hunt()
        }
    }

    /**
     * Walks to the nearest bloated toad and eats it once close enough.
     */
    private fun hunt() {
        if (gobbleAt > 0 && world.currentTick >= gobbleAt) {
            gobbleAt = 0
            speak("Gobble!")
        }
        if (eatingUntil > 0) {
            if (world.currentTick < eatingUntil) return
            eatingUntil = 0
        }
        if (combat.isSingleCombatLocked) return
        val toad = world.npcs.filterIsInstance<BloatedToad>()
            .filter { it.state == EntityState.ACTIVE && !it.eaten && it.position.z == position.z &&
                      it.position.isWithinDistance(position, HUNT_RANGE) }
            .minByOrNull { it.position.computeLongestDistance(position) } ?: return
        if (position.computeLongestDistance(toad.position) <= 2 &&
            world.collisionManager.raycast(position, toad.position)) {
            navigator.cancel()
            walking.clear()
            face(toad.position)
            speak("Sqwark!")
            animation(Animation(EAT_ANIMATION))
            gobbleAt = world.currentTick + 3
            eatingUntil = world.currentTick + 5
            toad.beEaten()
        } else if (walking.isEmpty) {
            navigator.navigate(toad, false)
        }
    }

    /**
     * Takes the hint arrow off the bird for the player who baited it.
     */
    fun clearHint() {
        baiter.get()?.queue(ClearHintMessageWriter())
    }

    companion object {
        const val LANDING_ANIMATION = 1014
        const val EAT_ANIMATION = 1011
        const val LIFE_TICKS = 100
        const val HUNT_RANGE = 12
    }
}
