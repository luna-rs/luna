package engine.obj

import api.predef.*
import api.predef.ext.*
import engine.obj.TrapdoorLanding.Companion.BELOW
import engine.obj.TrapdoorLanding.Companion.byTrapdoor
import engine.obj.TrapdoorLanding.Companion.floorsDown
import engine.obj.TrapdoorLanding.Companion.tile
import game.player.Sound
import io.luna.game.action.impl.LockedAction
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.overlay.WalkableInterface
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import io.luna.net.msg.out.WidgetAnimationMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * How many ticks an opened trapdoor stays open before closing by itself.
 */
val TRAPDOOR_TICKS = 500

/**
 * How many ticks an opened manhole stays open before its cover goes back by itself.
 */
val MANHOLE_TICKS = 400

/**
 * Bending down to the floor, to open a trapdoor or climb through it.
 */
val BEND_DOWN = Animation(827)

/**
 * The message for climbing down a trapdoor.
 */
val CLIMB_DOWN_MESSAGE = "You climb down through the trapdoor..."

/**
 * The overlay that fades the screen to and from black.
 */
val FADE_OVERLAY = 11124

/**
 * The model on [FADE_OVERLAY] that plays the fade.
 */
val FADE_MODEL = 11125

/**
 * The black square item [FADE_MODEL] shows.
 */
val FADE_TO_BLACK_ITEM = 4032

/**
 * Fading out to black.
 */
val FADE_OUT = 1377

/**
 * Fading back in from black.
 */
val FADE_IN = 1378

/**
 * Where the rat pit manholes lead.
 */
val RAT_PITS = byTrapdoor(BELOW, Position(3018, 3232) to tile(2962, 9650), Position(3267, 3400) to tile(2894, 5097))

/**
 * The objects [change] replaced, by position and type, or `null` where there wasn't one.
 */
val replaced = HashMap<Pair<Position, ObjectType>, GameObject?>()

/**
 * How many times [change] has changed each position and type.
 */
val changes = HashMap<Pair<Position, ObjectType>, Int>()

/**
 * Puts [id] on [position] for [ticks], or removes the [type] of object there if [id] is `null`. After that, the object
 * that was there before it was first changed comes back, unless it's been changed again since.
 *
 * @param position The position to change.
 * @param type The type of object to change.
 * @param direction The direction to put [id] in.
 * @param id The object to put there, or `null` for none.
 * @param ticks How long the change lasts.
 */
fun change(position: Position, type: ObjectType, direction: ObjectDirection, id: Int?, ticks: Int) {
    val key = position to type
    if (key !in replaced) {
        replaced[key] = world.objects.findAll(position).filter { it.objectType == type }.findFirst().orElse(null)
    }
    place(position, type, direction, id)
    val count = changes.merge(key, 1, Int::plus)
    world.scheduleOnce(ticks) {
        if (changes[key] == count) {
            changes -= key
            val original = replaced.remove(key)
            place(position, type, original?.direction ?: direction, original?.id)
        }
    }
}

/**
 * Changes [obj] into [id] for [ticks], or removes it for [ticks] if [id] is `null`.
 */
fun change(obj: GameObject, id: Int?, ticks: Int) = change(obj.position, obj.objectType, obj.direction, id, ticks)

/**
 * Puts [id] on [position], or removes the [type] of object there if [id] is `null`.
 */
fun place(position: Position, type: ObjectType, direction: ObjectDirection, id: Int?) {
    if (id == null) {
        world.objects.removeFromPosition(position) { it.objectType == type }
    } else {
        world.objects.register(
            GameObject.createDynamic(ctx, id, position, type, direction, ChunkUpdatableView.globalView()))
    }
}

/**
 * Returns the object with [id] on [position], or `null` if there isn't one.
 */
fun objectOn(position: Position, id: Int): GameObject? =
    world.objects.findAll(position).filter { it.id == id }.findFirst().orElse(null)

/**
 * Runs [first] now and [then] after [delay] ticks, with [plr] locked in between.
 */
fun delayed(plr: Player, delay: Int, first: () -> Unit, then: () -> Unit) {
    plr.submitAction(object : LockedAction(plr, true, delay) {
        override fun onLock() {
            mob.walking.clear()
        }

        override fun run(): Boolean {
            if (executions == 0) {
                first()
                return false
            }
            then()
            return true
        }
    })
}

/**
 * Climbs [plr] down to [target], or beside it if it can't be stood on (see [TrapdoorLanding.land]). [message] is sent
 * and [plr] bends down if [bend] is `true`, then they move after [delay] ticks, or straight away if it's 0.
 *
 * @param plr The climbing player.
 * @param target Where the trapdoor leads, or `null` if nowhere.
 * @param message The message to send, if any.
 * @param bend Whether to bend down to climb through.
 * @param delay How many ticks to wait before moving.
 * @param face The direction to face after landing, if any.
 */
fun climbDown(plr: Player, target: Position?, message: String? = CLIMB_DOWN_MESSAGE, bend: Boolean = true,
              delay: Int = 1, face: Direction? = null) {
    val destination = target?.let { TrapdoorLanding.land(it) }
    if (destination == null) {
        plr.sendMessage("Nothing interesting happens.")
        return
    }
    val start: () -> Unit = {
        message?.let { plr.sendMessage(it) }
        if (bend) {
            plr.animation(BEND_DOWN)
        }
    }
    val move: () -> Unit = {
        plr.move(destination)
        face?.let { plr.face(it) }
    }
    if (delay == 0) {
        start()
        move()
    } else {
        delayed(plr, delay, start, move)
    }
}

/**
 * Climbs [plr] down [trapdoor], which leads to [landing].
 */
fun climbDown(plr: Player, trapdoor: GameObject, landing: TrapdoorLanding) =
    climbDown(plr, landing.from(trapdoor.position, plr.position))

/**
 * Fades [plr]'s screen out to black or back in, with the [FADE_OUT] or [FADE_IN] animation.
 */
fun fade(plr: Player, animation: Int) {
    plr.queue(WidgetAnimationMessageWriter(FADE_MODEL, -1))
    plr.overlays.open(WalkableInterface(FADE_OVERLAY))
    plr.queue(WidgetItemModelMessageWriter(FADE_MODEL, 1000, FADE_TO_BLACK_ITEM))
    plr.queue(WidgetAnimationMessageWriter(FADE_MODEL, animation))
}

/**
 * Opens [trapdoor] into [open].
 */
fun open(plr: Player, trapdoor: GameObject, open: Int) {
    plr.sendMessage("The trapdoor opens...")
    plr.playSound(Sound.DOOR_OPEN)
    change(trapdoor, open, TRAPDOOR_TICKS)
}

/**
 * Closes [trapdoor] into [closed].
 */
fun close(plr: Player, trapdoor: GameObject, closed: Int) {
    plr.sendMessage("You close the trapdoor.")
    plr.playSound(Sound.DOOR_CLOSE)
    change(trapdoor, closed, TRAPDOOR_TICKS)
}

on(ServerLaunchEvent::class) {
    for (trapdoor in Trapdoor.values()) {
        val closed = trapdoor.closed
        if (closed != null) {
            object1(closed) { open(plr, gameObject, trapdoor.open) }
            if ("Close" in GameObjectDefinition.ALL.retrieve(trapdoor.open).actions) {
                object2(trapdoor.open) { close(plr, gameObject, closed) }
            }
        }
        object1(trapdoor.open) { climbDown(plr, gameObject, trapdoor.landing) }
    }
}

// Manholes into the Varrock and Ardougne sewers. Opening one puts its cover beside it, which closes it again.
object1(881) {
    plr.sendMessage("You pull back the cover from over the manhole.")
    plr.playSound(Sound.COFFIN_OPEN)
    change(gameObject, 882, MANHOLE_TICKS)
    change(gameObject.position.translate(0, -1), gameObject.objectType, gameObject.direction, 883, MANHOLE_TICKS)
}
object1(882) {
    climbDown(plr, BELOW.from(gameObject.position, plr.position), "You climb down through the manhole.", bend = false,
              delay = 0)
}
object1(883) {
    plr.sendMessage("You place the cover back over the manhole.")
    change(gameObject, null, MANHOLE_TICKS)
    objectOn(gameObject.position.translate(0, 1), 882)?.let { change(it, 881, MANHOLE_TICKS) }
}

// Manholes into the Port Sarim and Varrock rat pits, which are left open.
object1(10321) {
    climbDown(plr, RAT_PITS.from(gameObject.position, plr.position), "You climb down through the manhole.", bend = false,
              delay = 0)
}

// West Ardougne manhole into the sewer pipe (Plague City).
object1(2543) {
    change(gameObject, 2544, TRAPDOOR_TICKS)
    change(gameObject.position.translate(0, -1), ObjectType.DEFAULT, gameObject.direction, 2545, TRAPDOOR_TICKS)
}
object1(2544) {
    climbDown(plr, Position(2514, 9739), "You climb down through the manhole.", face = Direction.NORTH)
}
object1(2545) {
    change(gameObject, null, TRAPDOOR_TICKS)
    objectOn(gameObject.position.translate(0, 1), 2544)?.let { change(it, 2543, TRAPDOOR_TICKS) }
}

// Rellekka, the seer's house: the trapdoor down from the roof (The Fremennik Trials).
object1(4174) { open(plr, gameObject, 4173) }
object1(4173) { climbDown(plr, floorsDown(2).from(gameObject.position, plr.position), message = null, bend = false) }
object2(4173) {
    plr.sendMessage("The trapdoor closes...")
    plr.playSound(Sound.DOOR_OPEN)
    change(gameObject, 4174, TRAPDOOR_TICKS)
}

// Castle Wars, down from the team respawn rooms.
for (id in listOf(4471, 4472)) {
    object1(id) {
        climbDown(plr, floorsDown(1).from(gameObject.position, plr.position), message = null, bend = false, delay = 0)
    }
}

// Ape Atoll temple, into the dungeon below (Monkey Madness).
object1(4879) {
    val trapdoor = gameObject
    delayed(plr, 1, { plr.animation(BEND_DOWN) }) {
        plr.playSound(Sound.DOOR_OPEN)
        change(trapdoor, 4880, TRAPDOOR_TICKS)
        plr.sendMessage("The trapdoor opens...")
    }
}
object1(4880) {
    val destination = TrapdoorLanding.land(Position(2807, 9201))
    if (destination == null) {
        plr.sendMessage("Nothing interesting happens.")
        return@object1
    }
    plr.submitAction(object : LockedAction(plr) {
        override fun onLock() {
            mob.walking.clear()
        }

        override fun run(): Boolean {
            when (executions) {
                0 -> {
                    mob.sendMessage("You climb down the trapdoor.")
                    fade(mob, FADE_OUT)
                }

                4 -> mob.move(destination)
                5 -> fade(mob, FADE_IN)
                9 -> {
                    mob.overlays.closeWalkable()
                    return true
                }
            }
            return false
        }
    })
}
object2(4880) {
    plr.playSound(Sound.DOOR_CLOSE)
    change(gameObject, 4879, TRAPDOOR_TICKS)
}

// Ape Atoll, the eastern warehouse (Monkey Madness).
object1(4712) {
    delayed(plr, 1, { plr.animation(BEND_DOWN) }) { plr.sendMessage("The trapdoor seems locked from the inside.") }
}

// Canifis, the Rising Sun Inn's basement (In Search of the Myreque).
object1(5055) {
    climbDown(plr, Position(3477, 9845), "You open the trap door and find yourself in the inn basement", bend = false,
              delay = 0)
}

// West Ardougne, the Mourner Headquarters' basement (Mourning's End Part I). It swings open as it's used.
object1(8783) {
    plr.animation(BEND_DOWN)
    plr.playSound(Sound.DOOR_OPEN)
    change(gameObject, 8784, 5)
    climbDown(plr, Position(2044, 4649), message = null, bend = false, delay = 2)
}

// Tree Gnome Stronghold, the trapdoors into the Grand Tree's tunnels (The Grand Tree). They swing open as they're used.
object1(2446) {
    plr.animation(BEND_DOWN)
    plr.playSound(Sound.DOOR_OPEN)
    change(gameObject, 2445, 6)
    climbDown(plr, BELOW.from(gameObject.position, plr.position), message = null, bend = false, delay = 2)
}
object1(2444) {
    plr.animation(BEND_DOWN)
    plr.playSound(Sound.DOOR_OPEN)
    change(gameObject, 2445, 5)
    climbDown(plr, Position(2491, 9864), message = null, bend = false, delay = 2)
}
