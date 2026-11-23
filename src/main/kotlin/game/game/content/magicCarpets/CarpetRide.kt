package game.content.magicCarpets

import api.predef.*
import api.predef.ext.*
import game.content.sailing.Sailing
import game.player.Jingles
import game.player.Sound
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.PlayerModelAnimation
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import io.luna.game.model.mob.varp.Varp
import java.util.ArrayDeque
import java.util.OptionalInt

/**
 * Flies a player along a [CarpetRoute].
 *
 * The player walks onto the station's rug and sits down. The rug rises with them and becomes the carpet in their
 * right hand, which flies them across the desert as fast as running, without using or restoring run energy. It sets
 * them down on the far station's rug and they step off.
 *
 * @author TheLining
 */
class CarpetRide(plr: Player, private val route: CarpetRoute) : LockedAction(plr, true, 1) {

    companion object {

        /**
         * The carpet held in the right hand while flying.
         */
        const val MAGIC_CARPET = 5614

        /**
         * Hides the stations' rugs while it's 1, since the rider is flying on one.
         */
        const val RUG_VARP = 499

        /**
         * Sitting down on the rug.
         */
        private val SIT_DOWN = Animation(2266)

        /**
         * The rug rising into the air.
         */
        private val RAISE = Animation(2262)

        /**
         * The carpet setting down.
         */
        private val LAND = Animation(2263)

        /**
         * Sitting on the carpet while it flies.
         */
        private const val FLYING = 2261

        /**
         * The rider's animations for the whole flight, moving or not.
         */
        private val FLIGHT_MODEL = PlayerModelAnimation.Builder()
            .setStandingId(FLYING)
            .setStandingTurnId(FLYING)
            .setWalkingId(FLYING)
            .setTurning180DegreesId(FLYING)
            .setTurning90DegreesCwId(FLYING)
            .setTurning90DegreesCcwId(FLYING)
            .setRunningId(FLYING)
            .build()

        /**
         * The ticks spent sitting down before the rug rises.
         */
        private const val SIT_TICKS = 3

        /**
         * The ticks the rug takes to rise.
         */
        private const val RAISE_TICKS = 2

        /**
         * The ticks the carpet takes to set down.
         */
        private const val LAND_TICKS = 2

        /**
         * The ticks a rider gets to reach the rug before they're put on it.
         */
        private const val BOARD_TIMEOUT = 10

        /**
         * The least run energy kept while flying, so the carpet keeps its speed for a rider who has none.
         */
        private const val FLIGHT_ENERGY = 5.0
    }

    /**
     * The stages of a ride.
     */
    private enum class Stage { BOARDING, SITTING, RISING, FLYING, LANDING, STEPPING_OFF }

    /**
     * The stage the ride is in, and the tick it started on.
     */
    private var stage = Stage.BOARDING
    private var stageStarted = 0L

    /**
     * The rider's run energy when they took off, given back when they land.
     */
    private var energy = 0.0

    /**
     * The side panel tabs taken away for the flight, and what they held.
     */
    private var tabs: Map<TabIndex, OptionalInt> = emptyMap()

    /**
     * Set once the carpet has taken off, and cleared once it has set down again.
     */
    private var airborne = false

    /**
     * Set once the rider has stepped off at the far station.
     */
    private var landed = false

    override fun beforeLock() = MagicCarpets.pay(mob)

    override fun onLock() {
        mob.overlays.closeWindows()
        mob.walking.clear()
        walk(route.from.exit, route.from.spot)
        enter(Stage.BOARDING)
    }

    override fun run(): Boolean {
        val ticks = (world.currentTick - stageStarted).toInt()
        when (stage) {
            Stage.BOARDING -> if (mob.position == route.from.spot || ticks >= BOARD_TIMEOUT) sitDown()
            Stage.SITTING -> if (ticks >= SIT_TICKS) rise()
            Stage.RISING -> if (ticks >= RAISE_TICKS) fly()
            Stage.FLYING -> {
                holdEnergy()
                if (mob.position == route.to.spot && mob.walking.isEmpty) land()
            }
            Stage.LANDING -> if (ticks >= LAND_TICKS) stepOff()
            Stage.STEPPING_OFF -> {
                landed = true
                return true
            }
        }
        return false
    }

    override fun getPersistencePosition(): Position = route.to.exit

    /**
     * Starts [next] on this tick.
     */
    private fun enter(next: Stage) {
        stage = next
        stageStarted = world.currentTick
    }

    /**
     * Walks the rider through [tiles] at walking pace, whatever is in the way.
     */
    private fun walk(vararg tiles: Position) {
        mob.walking.isRunning = false
        mob.walking.replaceForcedPath(ArrayDeque(tiles.toList()))
    }

    /**
     * Sits the rider down on the rug, facing the way they'll fly.
     */
    private fun sitDown() {
        if (mob.position != route.from.spot) {
            mob.move(route.from.spot)
        }
        mob.walking.clear()
        mob.face(route.path.first())
        mob.animation(SIT_DOWN)
        enter(Stage.SITTING)
    }

    /**
     * Lifts the rider up on the rug, which leaves the ground.
     */
    private fun rise() {
        airborne = true
        energy = mob.runEnergy
        mob.sendVarp(Varp(RUG_VARP, 1))
        tabs = Sailing.HIDDEN_TABS.associateWith { mob.tabs.get(it) }
        Sailing.HIDDEN_TABS.forEach { mob.tabs.clear(it) }
        mob.animation(RAISE)
        mob.playSound(Sound.CARPET_RISE)
        mob.playJingle(Jingles.MAGIC_CARPET_RIDE)
        enter(Stage.RISING)
    }

    /**
     * Puts the carpet in the rider's hand and flies them along the route.
     */
    private fun fly() {
        mob.equipment.set(Equipment.WEAPON, Item(MAGIC_CARPET))
        // Taking up the carpet sets the rider's animations and brings back the combat tab, so these go after it.
        Sailing.HIDDEN_TABS.forEach { mob.tabs.clear(it) }
        mob.model = FLIGHT_MODEL
        mob.flags.flag(UpdateFlag.APPEARANCE)
        holdEnergy()
        mob.walking.isRunning = true
        mob.walking.replaceForcedPath(ArrayDeque(route.path))
        enter(Stage.FLYING)
    }

    /**
     * Keeps the rider's run energy from running out, so the carpet keeps its speed.
     */
    private fun holdEnergy() {
        if (mob.runEnergy < FLIGHT_ENERGY) {
            mob.setRunEnergy(FLIGHT_ENERGY, false)
        }
    }

    /**
     * Sets the carpet down on the far station's rug.
     */
    private fun land() {
        mob.walking.clear()
        mob.animation(LAND)
        mob.playSound(Sound.CARPET_DESCEND)
        enter(Stage.LANDING)
    }

    /**
     * Puts the carpet back down as the far station's rug and steps the rider off it.
     */
    private fun stepOff() {
        putCarpetDown()
        walk(route.to.exit)
        enter(Stage.STEPPING_OFF)
    }

    /**
     * Takes the carpet out of the rider's hand and gives them back their run energy, running and side panels.
     */
    private fun putCarpetDown() {
        if (!airborne) {
            return
        }
        airborne = false
        if (mob.equipment.get(Equipment.WEAPON)?.id == MAGIC_CARPET) {
            mob.equipment.set(Equipment.WEAPON, null)
        }
        mob.setRunEnergy(energy, true)
        mob.walking.isRunning = mob.isRunning
        for ((tab, id) in tabs) {
            if (id.isPresent) mob.tabs.set(tab, id.asInt) else mob.tabs.clear(tab)
        }
        tabs = emptyMap()
        mob.sendVarp(Varp(RUG_VARP, 0))
    }

    override fun onUnlock() {
        if (landed) {
            return
        }
        // The ride was cut short. The rider paid, so they're set down where they were going.
        putCarpetDown()
        if (mob.state == EntityState.ACTIVE) {
            mob.animation(Animation.CANCEL)
            mob.move(route.to.exit)
        }
    }
}
