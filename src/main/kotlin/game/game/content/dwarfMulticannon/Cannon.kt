package game.content.dwarfMulticannon

import api.predef.*
import api.predef.ext.*
import engine.controllers.Controllers.inMultiArea
import game.content.dwarfMulticannon.DwarfMulticannon.CANNONBALL
import game.content.dwarfMulticannon.DwarfMulticannon.DECAY_TICKS
import game.content.dwarfMulticannon.DwarfMulticannon.cannonPosition
import game.content.dwarfMulticannon.DwarfMulticannon.cannonStage
import game.player.Sound
import io.luna.game.model.Direction
import io.luna.game.model.LocalGraphic
import io.luna.game.model.LocalProjectile
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.`object`.GameObject
import io.luna.game.task.Task
import java.util.OptionalInt
import kotlin.math.max
import kotlin.math.min

/**
 * A Dwarf multicannon set up in the world. It decays after [DECAY_TICKS], and fires while its owner is online.
 *
 * @author TheLining
 */
class Cannon(val owner: String, val position: Position) : Task(false, 1) {

    companion object {

        /**
         * The cannons set up in the world, by owner username.
         */
        val ALL = HashMap<String, Cannon>()

        /**
         * The directions the cannon faces in turn, clockwise from north.
         */
        private val SWEEP = listOf(
            Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST,
            Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST
        )

        /**
         * The animations for turning from each direction in [SWEEP] to the next.
         */
        private val TURN_ANIMATIONS = listOf(515, 516, 517, 518, 519, 520, 521, 514)

        /**
         * How far out the cannon looks in a straight direction, close to far.
         */
        private val STRAIGHT_REACH = listOf(3, 7, 14)

        /**
         * How far out the cannon looks in a diagonal direction, close to far.
         */
        private val DIAGONAL_REACH = listOf(2, 5, 12)

        /**
         * How far from each point in the reach lists a target may be.
         */
        private val SEARCH_RADIUS = listOf(1, 2, 5)

        /**
         * The cannonball projectile.
         */
        private const val PROJECTILE = 53

        /**
         * The graphic shown when a cannon is destroyed.
         */
        private const val DESTROYED_GRAPHIC = 189

        /**
         * The King Black Dragon and Kalphite Queen, which destroy a cannon 1 in 4 shots.
         */
        private val DESTROYERS = setOf(50, 1158, 1160)
    }

    /**
     * The tile the cannon fires from.
     */
    val centre: Position = position.translate(1, 1)

    /**
     * The stage the cannon is built to.
     */
    var stage = CannonStage.BASE
        private set

    /**
     * The cannon object.
     */
    var obj: GameObject = world.addObject(stage.objectId, position)
        private set

    /**
     * The cannonballs loaded.
     */
    var ammo = 0

    /**
     * If the cannon is firing.
     */
    var firing = false
        private set

    /**
     * The ticks left before the cannon decays.
     */
    private var decayTicks = DECAY_TICKS

    /**
     * The index in [SWEEP] the cannon faces.
     */
    private var direction = 0

    override fun execute() {
        if (--decayTicks <= 0) {
            decay()
            return
        }
        if (firing) {
            val plr = world.getPlayer(owner).orElse(null)
            if (plr == null) {
                firing = false
            } else {
                rotate(plr)
            }
        }
    }

    /**
     * Adds the next part.
     */
    fun build(next: CannonStage) {
        stage = next
        obj = world.addObject(stage.objectId, position)
        decayTicks = DECAY_TICKS
    }

    /**
     * Starts firing, facing north.
     */
    fun startFiring() {
        firing = true
        direction = 0
    }

    /**
     * Stops firing.
     */
    fun stopFiring() {
        firing = false
    }

    /**
     * Gives the parts and cannonballs back to [plr] and removes the cannon.
     */
    fun pickUp(plr: Player) {
        stage.parts.forEach { plr.inventory.add(Item(it)) }
        plr.cannonPosition = null
        plr.cannonStage = null
        plr.sendMessage("You pick up the cannon,")
        plr.sendMessage("It's really heavy.")
        plr.playSound(Sound.PICK)
        remove()
        if (ammo > 0) {
            plr.giveItem(Item(CANNONBALL, ammo))
        }
    }

    /**
     * Turns the cannon one direction, firing at the closest target that way.
     */
    private fun rotate(plr: Player) {
        if (direction == 0 && ammo < 1) {
            plr.sendMessage("Your cannon is out of ammo!")
            firing = false
            return
        }
        val dir = SWEEP[direction]
        val reach = if (dir.isDiagonal) DIAGONAL_REACH else STRAIGHT_REACH
        for (index in reach.indices) {
            val search = centre.translate(dir.translateX * reach[index], dir.translateY * reach[index])
            val target = hunt(search, SEARCH_RADIUS[index]) ?: continue
            if (!world.collisionManager.raycast(centre, target.position)) {
                continue
            }

            // The cannon only tries the closest target it can see, even if it can't fire at it.
            fire(plr, target)
            break
        }
        obj.animate(TURN_ANIMATIONS[direction])
        direction = (direction + 1) % SWEEP.size
    }

    /**
     * Finds the attackable NPC closest to [search], within [radius] and in sight of it.
     */
    private fun hunt(search: Position, radius: Int): Npc? {
        return world.locator.findNpcs(search, radius) {
            it.combat.isAttackable && world.collisionManager.raycast(search, it.position)
        }.minByOrNull {
            val dx = it.position.x - search.x
            val dy = it.position.y - search.y
            dx * dx + dy * dy
        }
    }

    /**
     * Fires a cannonball at [npc] for [plr].
     */
    private fun fire(plr: Player, npc: Npc) {
        if (ammo < 1 || !canFireAt(plr, npc)) {
            return
        }
        val flight = 35 + distanceTo(npc) * 5
        object : LocalProjectile.TargetBuilder(ctx) {
            override fun sourcePosition() = centre
            override fun targetPosition(): Position = npc.position
            override fun targetIndex() = OptionalInt.of(npc.index + 1)
            override fun distanceFromSource() = 0
        }.setId(PROJECTILE)
            .setStartHeight(36)
            .setEndHeight(35)
            // The builder sends ticksToEnd as the client's start cycle and ticksToStart as its end cycle.
            .setTicksToEnd(0)
            .setTicksToStart(flight)
            .setInitialSlope(2)
            .build()
            .display()
        ammo--
        world.locator.findPlayers(position, 10) { true }.forEach { it.playSound(Sound.MCANNON_FIRE) }

        val attack = CannonAttack(plr, npc)
        val damage = attack.calculateDamage(npc)
        if (damage.rawAmount > 0) {
            plr.ranged.addExperience(2.0 * min(damage.rawAmount, npc.hitpoints.level))
        }
        npc.combat.lastCombatWith = plr
        npc.combat.resetCombatTimer()
        world.scheduleOnce(flight / 30) {
            npc.submitAction(CombatDamageAction(damage, attack, true))
        }
        if (npc.id in DESTROYERS && rand(3) == 0) {
            destroy(plr)
        }
    }

    /**
     * Determines if single-way combat lets [plr]'s cannon fire at [npc].
     */
    private fun canFireAt(plr: Player, npc: Npc): Boolean {
        if (npc.inMultiArea()) {
            return true
        }
        val plrOpponent = plr.combat.lastCombatWith
        if (plr.combat.inCombat() && plrOpponent != null && plrOpponent != npc) {
            plr.sendMessage("I'm already under attack!")
            return false
        }
        val npcOpponent = npc.combat.lastCombatWith
        if (npc.combat.inCombat() && npcOpponent != null && npcOpponent != plr) {
            plr.sendMessage("Someone else is fighting that.")
            return false
        }
        return true
    }

    /**
     * The distance from [centre] to the nearest tile of [npc].
     */
    private fun distanceTo(npc: Npc): Int {
        val pos = npc.position
        val dx = max(0, max(pos.x - centre.x, centre.x - (pos.x + npc.size() - 1)))
        val dy = max(0, max(pos.y - centre.y, centre.y - (pos.y + npc.size() - 1)))
        return max(dx, dy)
    }

    /**
     * Destroys the cannon. Nulodion will replace it.
     */
    private fun destroy(plr: Player) {
        LocalGraphic(ctx, DESTROYED_GRAPHIC, 200, 0, centre, ChunkUpdatableView.globalView()).display()
        plr.sendMessage("Your cannon has been destroyed!")
        remove()
    }

    /**
     * Removes a decayed cannon. Nulodion will replace it.
     */
    private fun decay() {
        if (stage == CannonStage.FURNACE) {
            world.getPlayer(owner).ifPresent { it.sendMessage("Your cannon has decayed!") }
        }
        remove()
    }

    /**
     * Removes the cannon from the world.
     */
    private fun remove() {
        firing = false
        world.removeObject(obj)
        ALL.remove(owner)
        cancel()
    }
}
