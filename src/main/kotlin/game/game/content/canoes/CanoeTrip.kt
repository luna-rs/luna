package game.content.canoes

import api.predef.*
import api.predef.ext.*
import game.content.canoes.Canoes.canoeStation
import game.content.sailing.Sailing
import game.player.Sound
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.Region
import io.luna.game.model.map.DynamicMap
import io.luna.game.model.map.DynamicMapController
import io.luna.game.model.map.builder.DynamicMapBuilder
import io.luna.game.model.map.builder.DynamicMapPalette
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import io.luna.game.model.mob.overlay.WalkableInterface
import io.luna.game.model.mob.varp.Varbit
import io.luna.game.model.`object`.ObjectType
import io.luna.net.msg.out.CameraTargetMessageWriter
import io.luna.net.msg.out.MinimapStateMessageWriter
import io.luna.net.msg.out.MinimapStateMessageWriter.State
import io.luna.net.msg.out.ResetCameraMessageWriter
import io.luna.net.msg.out.SoundMessageWriter
import io.luna.net.msg.out.WidgetAnimationMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter
import java.util.OptionalInt
import java.util.function.Function

/**
 * Paddles a player from one canoe station to another.
 *
 * The journey is shown in the player's own copy of a small river scene. They sit paddling in a canoe that stays put
 * while the camera watches from the bank, and trees and bullrushes glide past on the far side. A journey to the
 * Wilderness is shown in the underground river instead.
 *
 * @author TheLining
 */
object CanoeTrip {

    /**
     * The map region that holds both river scenes.
     */
    val SCENE_REGION = Region(7238)

    /**
     * The varbit that picks which canoe floats in the scene.
     */
    const val CANOE_VARBIT = 1843

    /**
     * The animation for paddling.
     */
    val PADDLING = Animation(3302)

    /**
     * The overlay that fades the screen to black and back, and the model on it that does the fading.
     */
    private const val FADE_OVERLAY = 11124
    private const val FADE_WIDGET = 11125

    /**
     * The fade model's animations.
     */
    private const val FADE_OUT = 1377
    private const val FADE_IN = 1378

    /**
     * The tick of a journey on which the player is moved into the scene, the screen having faded out.
     */
    private const val BOARD_TICK = 2

    /**
     * The ticks the screen takes to fade out at the end of the scene, before the player lands.
     */
    private const val FADE_TICKS = 3

    /**
     * The ticks a sunk canoe stays where the player landed.
     */
    private const val SINK_TICKS = 4

    /**
     * A piece of the far bank that glides north past the canoe, a tile each tick.
     *
     * @property npc The NPC that plays the piece.
     * @property tile Where it appears.
     * @property leaves The tick of the scene on which it's taken away.
     * @property appears The tick of the scene on which it appears.
     */
    class Scenery(val npc: Int, val tile: Position, val leaves: Int, val appears: Int = 0)

    /**
     * The two river scenes.
     *
     * @property seat Where the player sits, in the middle of the scene's canoe.
     * @property camera The tile the camera watches from.
     * @property cameraHeight How high the camera is.
     * @property seatHeight How high above the seat the camera aims.
     * @property paddleLoops How many times the paddling sound repeats.
     * @property wait The ticks between the scene being set and the screen fading in.
     * @property length The ticks the scene is watched for.
     * @property scenery What glides past.
     */
    enum class Scene(val seat: Position,
                     val camera: Position,
                     val cameraHeight: Int,
                     val seatHeight: Int,
                     val paddleLoops: Int,
                     val wait: Int,
                     val length: Int,
                     val scenery: List<Scenery>) {
        RIVER(seat = Position(1817, 4515),
              camera = Position(1812, 4515),
              cameraHeight = 255,
              seatHeight = 255,
              paddleLoops = 16,
              wait = 0,
              length = 22,
              scenery = listOf(Scenery(BULLRUSH, Position(1820, 4516), leaves = 7),
                               Scenery(BULLRUSH_LEAF, Position(1819, 4516), leaves = 7),
                               Scenery(TREES, Position(1822, 4516), leaves = 7),
                               Scenery(TALL_TREES, Position(1821, 4503), leaves = 21),
                               Scenery(TREES, Position(1821, 4499), leaves = 25, appears = 7),
                               Scenery(BULLRUSH_LEAF, Position(1819, 4504), leaves = 25, appears = 10),
                               Scenery(BULLRUSH, Position(1820, 4500), leaves = 25, appears = 10))),
        CAVE(seat = Position(1845, 4492),
             camera = Position(1838, 4492),
             cameraHeight = 275,
             seatHeight = 100,
             paddleLoops = 10,
             wait = 1,
             length = 11,
             scenery = listOf(Scenery(CAVE_WALL, Position(1842, 4489), leaves = 14),
                              Scenery(CAVE_ROCKS, Position(1846, 4488), leaves = 14, appears = 1),
                              Scenery(CAVE_WALL, Position(1842, 4482), leaves = 14, appears = 2),
                              Scenery(CAVE_ROCKS, Position(1846, 4484), leaves = 14, appears = 3),
                              Scenery(CAVE_PILLARS, Position(1846, 4480), leaves = 14, appears = 5)))
    }

    /**
     * The NPCs that play the scenery.
     */
    private const val TREES = 3332
    private const val TALL_TREES = 3333
    private const val BULLRUSH = 3335
    private const val BULLRUSH_LEAF = 3336
    private const val CAVE_WALL = 3337
    private const val CAVE_ROCKS = 3338
    private const val CAVE_PILLARS = 3339

    /**
     * Paddles [plr] from [from] to [to] in the [type] canoe floating at [from].
     */
    fun start(plr: Player, from: CanoeStation, to: CanoeStation, type: CanoeType) {
        if (plr.isLocked || Canoes.state(plr, from) != type.afloatState || to !in from.destinations(type)) {
            return
        }
        plr.submitAction(Journey(plr, from, to, type))
    }

    /**
     * Keeps a player in their copy of the river scene, and lands them where they were going however they leave it.
     */
    private class SceneController(plr: Player, private val landing: Position) : DynamicMapController(plr) {

        override fun enter(player: Player) {
        }

        override fun exit(player: Player): Position {
            val map = player.dynamicMap
            // The map still holds the player until this returns, so it can't be deleted yet.
            world.scheduleOnce(1) { map.delete() }
            return landing
        }

        override fun recovery(player: Player) = landing
    }

    /**
     * One journey, from the click on the destination map to stepping ashore.
     *
     * @param plr The player.
     * @param from The station they leave.
     * @param to Where they're going.
     * @param type Their canoe.
     */
    private class Journey(plr: Player,
                          private val from: CanoeStation,
                          private val to: CanoeStation,
                          private val type: CanoeType) : LockedAction(plr, false, 1) {

        /**
         * The scene this journey is shown in.
         */
        private val scene = if (to == CanoeStation.WILDERNESS_POND) Scene.CAVE else Scene.RIVER

        /**
         * The tick this journey started on.
         */
        private var started = 0L

        /**
         * The tick of this journey that [run] last handled.
         */
        private var handled = -1

        /**
         * The player's copy of the scene, while they're in it.
         */
        private var map: DynamicMap? = null

        /**
         * The fade overlay this journey last opened.
         */
        private var fade: WalkableInterface? = null

        /**
         * The side panel tabs taken away for the scene, and what they held.
         */
        private var tabs: Map<TabIndex, OptionalInt> = emptyMap()

        /**
         * The scenery in the scene, each with the piece it plays.
         */
        private val scenery = HashMap<Scenery, Npc>()

        /**
         * Set once the player has been moved into the scene.
         */
        private var boarded = false

        /**
         * Set once the player has stepped ashore.
         */
        private var landed = false

        override fun beforeLock() = Canoes.state(mob, from) == type.afloatState

        override fun onLock() {
            started = world.currentTick
            mob.overlays.closeWindows()
            mob.walking.clear()
            fade(FADE_OUT)
        }

        override fun run(): Boolean {
            val now = (world.currentTick - started).toInt()
            // Every tick is handled once and in order, whichever tick this action first runs on.
            while (handled < now) {
                if (step(++handled)) {
                    return true
                }
            }
            return false
        }

        /**
         * Handles one [tick] of the journey. Returns `true` once it's over.
         */
        private fun step(tick: Int): Boolean {
            val set = BOARD_TICK + 1
            val shown = set + scene.wait
            val over = shown + scene.length
            val land = over + FADE_TICKS
            when {
                tick < BOARD_TICK -> return false
                tick == BOARD_TICK -> board()
                tick <= land && (map == null || mob.dynamicMap != map) -> return true
            }
            if (tick in set until land) {
                moveScenery(tick - set)
            }
            // The river scene is set and shown on the same tick, so each step is checked on its own.
            if (tick == set) {
                setScene()
            }
            if (tick == shown) {
                fade(FADE_IN)
            }
            if (tick == over) {
                fade(FADE_OUT)
            }
            if (tick == land) {
                map?.remove(mob)
                map = null
                mob.animation(Animation.CANCEL)
                mob.queue(ResetCameraMessageWriter())
            }
            if (tick == land + 1) {
                arrive()
                return true
            }
            return false
        }

        /**
         * Moves the player into their own copy of the scene, with the side panels and minimap put away.
         */
        private fun board() {
            val palette = DynamicMapPalette().setRegion(4, 0, SCENE_REGION, 0)
            val landing = to.landing
            val copy = DynamicMapBuilder(ctx)
                .setPalette(palette.getChunk(6, 6, 0).chunk, palette)
                .setController(Function<Player, DynamicMapController> { SceneController(it, landing) })
                .build()
            copy.create()
            copy.add(mob, copy.getInstancePosition(scene.seat))
            map = copy
            boarded = true
            mob.sendVarbit(Varbit(CANOE_VARBIT, type.ordinal + 1))
            tabs = Sailing.HIDDEN_TABS.associateWith { mob.tabs.get(it) }
            Sailing.HIDDEN_TABS.forEach { mob.tabs.clear(it) }
            mob.queue(MinimapStateMessageWriter(State.CONCEALED))
        }

        /**
         * Sits the player paddling and points the camera at them from the bank.
         */
        private fun setScene() {
            val copy = map ?: return
            mob.animation(PADDLING)
            mob.face(Direction.WEST)
            mob.queue(SoundMessageWriter(Sound.CANOE_PADDLE_LOOP.id, scene.paddleLoops, 0))
            mob.queue(CameraTargetMessageWriter(copy.getInstancePosition(scene.camera), scene.cameraHeight, 100, 100,
                                                false))
            mob.queue(CameraTargetMessageWriter(copy.getInstancePosition(scene.seat), scene.seatHeight, 100, 100,
                                                true))
        }

        /**
         * Brings in, moves on and takes away the scenery for [tick] of the scene.
         */
        private fun moveScenery(tick: Int) {
            val copy = map ?: return
            for (piece in scene.scenery) {
                when {
                    tick == piece.leaves -> scenery.remove(piece)?.let { world.removeNpc(it) }
                    tick == piece.appears ->
                        scenery[piece] = world.addNpc(Npc(ctx, piece.npc, copy.getInstancePosition(piece.tile)))

                    tick > piece.appears -> scenery[piece]?.walking?.addForcedStep(Direction.NORTH)
                }
            }
        }

        /**
         * Steps the player ashore as their canoe sinks behind them.
         */
        private fun arrive() {
            landed = true
            restore()
            fade(FADE_IN)
            val shown = fade
            val plr = mob
            world.scheduleOnce(FADE_TICKS) {
                if (shown != null && plr.overlays.getOverlay(WalkableInterface::class.java) === shown) {
                    plr.overlays.closeWalkable()
                }
            }
            val canoe = world.addObject(type.sinking, to.sinkTile, ObjectType.DEFAULT, to.sinkDirection)
            world.scheduleOnce(SINK_TICKS) { world.removeObject(canoe) }
            mob.playSound(Sound.CANOE_SINK)
            to.arrival.forEach { mob.sendMessage(it) }
        }

        /**
         * Gives the player back their side panels and minimap, and takes the canoe they left in off the station.
         */
        private fun restore() {
            if (!boarded) {
                return
            }
            for ((tab, id) in tabs) {
                if (id.isPresent) mob.tabs.set(tab, id.asInt) else mob.tabs.clear(tab)
            }
            tabs = emptyMap()
            mob.queue(MinimapStateMessageWriter(State.NORMAL))
            mob.sendVarbit(Varbit(CANOE_VARBIT, 0))
            Canoes.setState(mob, from, Canoes.TREE)
            mob.canoeStation = null
        }

        /**
         * Shows the fade overlay playing [sequence].
         */
        private fun fade(sequence: Int) {
            mob.queue(WidgetAnimationMessageWriter(FADE_WIDGET, -1))
            fade = WalkableInterface(FADE_OVERLAY).also { mob.overlays.open(it) }
            mob.queue(WidgetItemModelMessageWriter(FADE_WIDGET, 1000, 4032))
            mob.queue(WidgetAnimationMessageWriter(FADE_WIDGET, sequence))
        }

        override fun onUnlock() {
            if (landed) {
                return
            }
            // The journey was cut short. Leaving the scene lands the player where they were going.
            scenery.values.forEach { world.removeNpc(it) }
            map?.let { if (mob.dynamicMap == it) it.remove(mob) }
            if (mob.state == EntityState.ACTIVE) {
                mob.animation(Animation.CANCEL)
                mob.queue(ResetCameraMessageWriter())
                if (fade != null && mob.overlays.getOverlay(WalkableInterface::class.java) === fade) {
                    mob.overlays.closeWalkable()
                }
                restore()
            }
        }
    }
}
