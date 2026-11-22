package game.content.canoes

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import engine.obj.LadderDestination
import game.player.Sound
import game.skill.Skills
import game.skill.woodcutting.cutTree.Axe
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.def.VarBitDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.Varbit
import io.luna.game.model.mob.varp.Varp
import io.luna.game.model.`object`.GameObject
import io.luna.net.msg.out.WidgetVisibilityMessageWriter
import java.util.concurrent.CompletableFuture

/**
 * Makes canoes at the River Lum canoe stations: chopping the canoe tree down, shaping it, pushing the canoe into the
 * water and picking where to paddle it.
 *
 * Each player sees their own tree or canoe at a station, and has one canoe at a time: starting on another
 * station's tree gives up whatever was at the last one. Nothing is kept over a logout.
 *
 * @author TheLining
 */
object Canoes {

    /**
     * The interface that asks which canoe to shape.
     */
    const val SHAPING_INTERFACE = 18178

    /**
     * The interface that asks where to paddle.
     */
    const val MAP_INTERFACE = 18220

    /**
     * The confirmation for the one-way trip to the Wilderness pond.
     */
    const val ACCEPT_WILDERNESS = "Yes, I'm brave."

    /**
     * The varp that holds what every station shows.
     */
    const val STATIONS_VARP = 674

    /**
     * The Woodcutting level needed to start on a canoe tree.
     */
    const val TREE_LEVEL = 12

    /**
     * The station state for a standing canoe tree.
     */
    const val TREE = 0

    /**
     * The station state for the tree as it falls.
     */
    const val FALLING = 9

    /**
     * The station state for the fallen tree, ready to be shaped.
     */
    const val FALLEN = 10

    /**
     * The station object's animation, which fells the tree and slides the canoe into the water.
     */
    const val STATION_ANIMATION = 3304

    /**
     * The animation for pushing a canoe into the water.
     */
    val PUSH_ANIMATION = Animation(3301)

    /**
     * The low and high chances out of 256 of felling the tree, or finishing the canoe, on one attempt with each axe.
     */
    private val AXE_CHANCES = mapOf(Axe.BRONZE to (32 to 100),
                                    Axe.IRON to (48 to 150),
                                    Axe.STEEL to (64 to 200),
                                    Axe.BLACK to (72 to 225),
                                    Axe.MITHRIL to (80 to 250),
                                    Axe.ADAMANT to (96 to 300),
                                    Axe.RUNE to (112 to 350),
                                    Axe.DRAGON to (112 to 350))

    /**
     * The animation for shaping a canoe with each axe.
     */
    private val SHAPE_ANIMATIONS = mapOf(Axe.RUNE to Animation(3285),
                                         Axe.ADAMANT to Animation(3286),
                                         Axe.MITHRIL to Animation(3287),
                                         Axe.BLACK to Animation(3288),
                                         Axe.STEEL to Animation(3289),
                                         Axe.IRON to Animation(3290),
                                         Axe.BRONZE to Animation(3291),
                                         Axe.DRAGON to Animation(3292))

    /**
     * The station the player's tree or canoe is at, or `null` if they have none.
     */
    var Player.canoeStation: CanoeStation? by Attr.nullableObj(CanoeStation::class)

    /**
     * The canoe the player picked to shape and hasn't finished, or `null`. While it's set, clicking the fallen tree
     * carries on shaping without asking again.
     */
    var Player.canoeChoice: CanoeType? by Attr.nullableObj(CanoeType::class)

    /**
     * The shaping interface, opened at [station].
     */
    class ShapingInterface(val station: CanoeStation, val tree: GameObject) : StandardInterface(SHAPING_INTERFACE)

    /**
     * The destination map, opened from a [type] canoe floating at [station].
     */
    class DestinationMap(val station: CanoeStation, val type: CanoeType) : StandardInterface(MAP_INTERFACE)

    /**
     * What [plr] sees at [station]: [TREE], [FALLING], [FALLEN] or one of the canoe states of [CanoeType].
     */
    fun state(plr: Player, station: CanoeStation): Int {
        val tree = station.tree ?: return TREE
        val def = VarBitDefinition.ALL.retrieve(tree.stateVarbit)
        val mask = (1 shl (def.mostSignificantBit - def.leastSignificantBit + 1)) - 1
        return (plr.cachedVarps.getOrDefault(def.parentVarpId, 0) ushr def.leastSignificantBit) and mask
    }

    /**
     * Changes what [plr] sees at [station].
     */
    fun setState(plr: Player, station: CanoeStation, state: Int) {
        val tree = station.tree ?: return
        plr.sendVarbit(Varbit(tree.stateVarbit, state))
    }

    /**
     * Stands every station's tree back up for [plr] and forgets their canoe. The client keeps its varps over a
     * logout, so this also runs at login.
     */
    fun reset(plr: Player) {
        plr.sendVarp(Varp(STATIONS_VARP, 0))
        plr.canoeStation = null
        plr.canoeChoice = null
    }

    /**
     * Does whatever the object at [station] offers [plr] right now.
     */
    fun click(plr: Player, station: CanoeStation, tree: GameObject) {
        val state = state(plr, station)
        val type = CanoeType.of(state)
        when {
            state == TREE -> chopDown(plr, station, tree)
            state == FALLEN -> shapeCanoe(plr, station, tree)
            type != null && state == type.shapedState -> floatCanoe(plr, station, tree, type)
            type != null && state == type.afloatState -> openMap(plr, station, type)
        }
    }

    /**
     * Walks [plr] to the tree at [station] and starts chopping it down.
     */
    private fun chopDown(plr: Player, station: CanoeStation, tree: GameObject) {
        val spot = station.tree ?: return
        val last = plr.canoeStation
        if (last != null && last != station) {
            setState(plr, last, TREE)
        }
        plr.canoeChoice = null
        plr.canoeStation = station
        plr.submitAction(Approach(plr, spot.chopTile) { plr.submitAction(ChopDownAction(plr, station, tree)) })
    }

    /**
     * Asks [plr] which canoe to shape from the fallen tree at [station], or carries on with the one they picked.
     */
    private fun shapeCanoe(plr: Player, station: CanoeStation, tree: GameObject) {
        val choice = plr.canoeChoice
        if (choice != null) {
            startShaping(plr, station, tree, choice)
            return
        }
        plr.interact(tree)
        for (type in CanoeType.values()) {
            val canMake = plr.woodcutting.level >= type.level
            type.unlocked?.let { plr.queue(WidgetVisibilityMessageWriter(it, !canMake)) }
            type.locked?.let { plr.queue(WidgetVisibilityMessageWriter(it, canMake)) }
        }
        plr.overlays.open(ShapingInterface(station, tree))
    }

    /**
     * Shapes the [type] canoe that [plr] picked on the shaping interface.
     */
    fun pickCanoe(plr: Player, type: CanoeType) {
        val shaping = plr.overlays.getOverlay(ShapingInterface::class.java) ?: return
        plr.overlays.closeWindows()
        if (plr.woodcutting.level < type.level || state(plr, shaping.station) != FALLEN) {
            return
        }
        plr.canoeChoice = type
        startShaping(plr, shaping.station, shaping.tree, type)
    }

    /**
     * Walks [plr] beside the fallen tree at [station] and starts shaping it into a [type] canoe.
     */
    private fun startShaping(plr: Player, station: CanoeStation, tree: GameObject, type: CanoeType) {
        val spot = station.tree ?: return
        val here = plr.position
        val beside = (-1..1).flatMap { dx -> (-1..1).map { dy -> spot.shapeTile.translate(dx, dy) } }
            .filter { LadderDestination.canLand(it) }
            .minByOrNull { if (it == here) -1 else it.computeLongestDistance(here) } ?: spot.shapeTile
        plr.submitAction(Approach(plr, beside) { plr.submitAction(ShapeAction(plr, station, tree, type)) })
    }

    /**
     * Walks [plr] to the [type] canoe at [station] and pushes it into the water.
     */
    private fun floatCanoe(plr: Player, station: CanoeStation, tree: GameObject, type: CanoeType) {
        val spot = station.tree ?: return
        plr.submitAction(Approach(plr, spot.shapeTile) { plr.submitAction(FloatAction(plr, station, tree, type)) })
    }

    /**
     * Shows [plr] where the [type] canoe floating at [station] can take them.
     */
    private fun openMap(plr: Player, station: CanoeStation, type: CanoeType) {
        val destinations = station.destinations(type)
        for (stop in CanoeStation.values()) {
            plr.queue(WidgetVisibilityMessageWriter(stop.mapLayer, stop !in destinations))
            stop.tree?.let { plr.queue(WidgetVisibilityMessageWriter(it.hereLayer, stop != station)) }
        }
        plr.overlays.open(DestinationMap(station, type))
    }

    /**
     * Paddles [plr] to the [destination] they picked on the destination map, after a warning if it's the Wilderness.
     */
    fun pickDestination(plr: Player, destination: CanoeStation) {
        val map = plr.overlays.getOverlay(DestinationMap::class.java) ?: return
        plr.overlays.closeWindows()
        if (destination !in map.station.destinations(map.type)) {
            return
        }
        if (destination != CanoeStation.WILDERNESS_POND) {
            CanoeTrip.start(plr, map.station, destination, map.type)
            return
        }
        plr.newDialogue()
            .text("@dre@Warning!@bla@ This canoe will take you deep into the @dre@Wilderness@bla@. There",
                  "are no trees suitable to make a canoe there. You will have to walk",
                  "back.")
            .options(ACCEPT_WILDERNESS, {
                it.overlays.closeWindows()
                CanoeTrip.start(it, map.station, destination, map.type)
            },
                     "Eeep! The Wilderness... No thank you.", {
                         it.overlays.closeWindows()
                         it.sendMessage("You decide not to travel to the Wilderness.")
                     })
            .title("Are you sure you wish to travel?")
            .open()
    }

    /**
     * Rolls one attempt at felling the tree or finishing the canoe with [axe].
     */
    private fun attempt(plr: Player, axe: Axe): Boolean {
        val (low, high) = AXE_CHANCES.getValue(axe)
        return Skills.successRate(low, high, minOf(plr.woodcutting.level, 99)) > rand().nextDouble()
    }

    /**
     * Walks a player who clicked a station onto the tile the next step is done from, then starts that step.
     *
     * This is a weak action, so walking away, or doing anything else, cancels it.
     *
     * @param plr The player.
     * @param tile The tile to walk to.
     * @param onArrival Starts the step once the player stands on [tile].
     */
    private class Approach(plr: Player, private val tile: Position, private val onArrival: () -> Unit) :
        Action<Player>(plr, ActionType.WEAK) {

        /**
         * The walk to [tile], requested when this action is submitted.
         */
        private var walking: CompletableFuture<NavigationResult>? = null

        /**
         * The ticks that have passed.
         */
        private var ticks = 0

        override fun onSubmit() {
            if (mob.position != tile) {
                walking = mob.navigator.navigate(tile, false)
            }
        }

        override fun run(): Boolean {
            // Checked before the walk's result, which only completes a tick after the player arrives.
            if (mob.position == tile) {
                onArrival()
                return true
            }
            val pending = walking ?: return true
            return pending.isDone || ++ticks > 15
        }
    }

    /**
     * Chops a canoe tree down. The player needs no swing for the first attempt, which comes two ticks after they
     * face the tree; later attempts come every four ticks, each with a swing.
     *
     * @param plr The player.
     * @param station The station.
     * @param tree The station's object.
     */
    private class ChopDownAction(plr: Player, private val station: CanoeStation, private val tree: GameObject) :
        Action<Player>(plr, ActionType.WEAK, false, 1) {

        /**
         * The ticks since the player first tried the tree.
         */
        private var ticks = -1

        override fun onSubmit() {
            val spot = station.tree ?: return
            mob.face(spot.chopTile.translate(1, spot.chopFacing))
        }

        override fun run(): Boolean {
            ticks++
            if (state(mob, station) != TREE) {
                return true
            }
            if (mob.woodcutting.level < TREE_LEVEL) {
                mob.sendMessage("You must have at least level $TREE_LEVEL woodcutting to start making canoes.")
                return true
            }
            val axe = Axe.computeAxeType(mob)
            if (axe == null) {
                Axe.sendNoAxeMessages(mob)
                return true
            }
            if (ticks == 5 || (ticks >= 10 && ticks % 4 == 2)) {
                mob.animation(axe.animation)
                mob.playSound(Sound.CUT_TREE_2)
            }
            if (ticks % 4 == 2 && attempt(mob, axe)) {
                fell()
                return true
            }
            return false
        }

        /**
         * Brings the tree down, which everyone nearby hears and sees on their own station.
         */
        private fun fell() {
            setState(mob, station, FALLING)
            mob.playSound(Sound.TREE_FALL)
            tree.animate(STATION_ANIMATION)
            val plr = mob
            world.scheduleOnce(1) {
                if (plr.state == EntityState.ACTIVE && state(plr, station) == FALLING) {
                    setState(plr, station, FALLEN)
                }
            }
        }

        override fun onFinished() {
            mob.animation(Animation.CANCEL)
        }
    }

    /**
     * Shapes a fallen canoe tree into a canoe. The first attempt comes five ticks after the player starts, and
     * later attempts every six ticks.
     *
     * @param plr The player.
     * @param station The station.
     * @param tree The station's object.
     * @param type The canoe to make.
     */
    private class ShapeAction(plr: Player,
                              private val station: CanoeStation,
                              private val tree: GameObject,
                              private val type: CanoeType) : Action<Player>(plr, ActionType.WEAK, false, 1) {

        /**
         * The ticks since the player started shaping.
         */
        private var ticks = 0

        override fun onSubmit() {
            mob.interact(tree)
        }

        override fun run(): Boolean {
            ticks++
            if (state(mob, station) != FALLEN) {
                return true
            }
            val axe = Axe.computeAxeType(mob)
            if (axe == null) {
                mob.sendMessage("You need an axe to shape a canoe.")
                mob.sendMessage("You do not have an axe that you have the woodcutting level to use.")
                mob.canoeChoice = null
                return true
            }
            if (ticks % 6 == 1) {
                mob.animation(SHAPE_ANIMATIONS.getValue(axe))
            }
            if (ticks % 6 == 5 && attempt(mob, axe)) {
                setState(mob, station, type.shapedState)
                mob.canoeChoice = null
                mob.woodcutting.addExperience(type.exp)
                return true
            }
            return false
        }

        override fun onFinished() {
            mob.animation(Animation.CANCEL)
        }
    }

    /**
     * Pushes a shaped canoe into the water, which takes two ticks.
     *
     * @param plr The player.
     * @param station The station.
     * @param tree The station's object.
     * @param type The canoe on the bank.
     */
    private class FloatAction(plr: Player,
                              private val station: CanoeStation,
                              private val tree: GameObject,
                              private val type: CanoeType) : LockedAction(plr, false, 1) {

        /**
         * The ticks since the push.
         */
        private var ticks = -1

        override fun beforeLock() = state(mob, station) == type.shapedState

        override fun run(): Boolean {
            when (++ticks) {
                0 -> {
                    setState(mob, station, type.floatingState)
                    mob.animation(PUSH_ANIMATION)
                    mob.playSound(Sound.CANOE_ROLL)
                    tree.animate(STATION_ANIMATION)
                }

                1 -> mob.interact(tree)
                else -> {
                    setState(mob, station, type.afloatState)
                    return true
                }
            }
            return false
        }

        override fun onUnlock() {
            // A push that was cut short still leaves the canoe in the water.
            if (state(mob, station) == type.floatingState) {
                setState(mob, station, type.afloatState)
            }
        }
    }
}
