package api.predef

import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.AbstractInterfaceSet
import io.luna.game.model.mob.inter.StandardInterface
import io.luna.game.task.Task
import io.luna.net.msg.out.ConfigMessageWriter
import java.util.*
import kotlin.reflect.KClass


/*****************************
 *                           *
 *  [Player] ext. functions  *
 *                           *
 ****************************/

/**
 * Queues a [ConfigMessageWriter] message.
 */
fun Player.sendConfig(id: Int, state: Int) = queue(ConfigMessageWriter(id, state))


/*****************************
 *                           *
 *  [World] ext. functions   *
 *                           *
 ****************************/

/**
 * Spawns an [Npc].
 */
fun World.addNpc(id: Int, x: Int, y: Int, z: Int = 0): Npc {
    val npc = Npc(ctx, id, Position(x, y, z))
    npcs.add(npc)
    return npc
}

/**
 * Spawns a local [GameObject] for [plr].
 */
fun World.addLocalObject(id: Int,
                         x: Int,
                         y: Int,
                         z: Int = 0,
                         type: ObjectType = ObjectType.DEFAULT,
                         direction: ObjectDirection = ObjectDirection.WEST,
                         plr: Player): GameObject {
    val localObject = GameObject(ctx, id, Position(x, y, z), type, direction, Optional.of(plr))
    return when {
        objects.add(localObject) -> localObject
        else -> throw IllegalStateException("$localObject Could not be spawned!");
    }
}

/**
 * Spawns a global [GameObject].
 */
fun World.addGlobalObject(id: Int,
                          x: Int,
                          y: Int,
                          z: Int = 0,
                          type: ObjectType = ObjectType.DEFAULT,
                          direction: ObjectDirection = ObjectDirection.WEST): GameObject {
    val globalObject = GameObject(ctx, id, Position(x, y, z), type, direction, Optional.empty())
    return when {
        objects.add(globalObject) -> globalObject
        else -> throw IllegalStateException("$globalObject Could not be spawned!");
    }
}

/**
 * Spawns a local [GroundItem] for [plr].
 */
fun World.addLocalItem(id: Int,
                       amount: Int,
                       x: Int,
                       y: Int,
                       z: Int = 0,
                       plr: Player): GroundItem {
    val localItem = GroundItem(ctx, id, amount, Position(x, y, z), Optional.of(plr))
    return when {
        items.add(localItem) -> localItem
        else -> throw IllegalStateException("$localItem Could not be spawned!");
    }
}

/**
 * Spawns a global [GroundItem].
 */
fun World.addGlobalItem(id: Int,
                        amount: Int,
                        x: Int,
                        y: Int,
                        z: Int = 0): GroundItem {
    val globalItem = GroundItem(ctx, id, amount, Position(x, y, z), Optional.empty())
    return when {
        items.add(globalItem) -> globalItem
        else -> throw IllegalStateException("$globalItem Could not be spawned!");
    }
}

/**
 * Schedules a recurring task.
 */
fun World.schedule(delay: Int, instant: Boolean = false, action: (Task) -> Unit) {
    schedule(object : Task(instant, delay) {
        override fun execute() {
            action(this)
        }
    })
}

/**
 * Schedules a task that suspends after one execution.
 */
fun World.scheduleOnce(delay: Int, action: (Task) -> Unit) {
    schedule(delay) {
        action(it)
        it.cancel()
    }
}


/*******************************************
 *                                         *
 *  [AbstractInterfaceSet] ext. functions  *
 *                                         *
 ******************************************/

/**
 * Returns the currently open [StandardInterface] if it matches [interClass].
 */
fun <T : StandardInterface> AbstractInterfaceSet.get(interClass: KClass<T>): T? {
    val jClass = interClass.java
    return currentStandard.filter { jClass.isInstance(it) }.map { jClass.cast(it) }.orElse(null)
}

/**
 * Determines if the currently open [StandardInterface] matches [interClass].
 */
fun <T : StandardInterface> AbstractInterfaceSet.isOpen(interClass: KClass<T>): Boolean {
    val jClass = interClass.java
    return currentStandard.filter { jClass.isInstance(it) }.isPresent
}


/****************************
 *                          *
 *  [Array] ext. functions  *
 *                          *
 ***************************/

/**
 * Randomizes the elements within the array. This function modifies the backing array.
 */
fun <T> Array<T>.shuffle() {
    var i = size - 1
    while (i > 0) {
        val index = rand(i)
        val obj = this[index]
        this[index] = this[i]
        this[i] = obj
        i--
    }
}


/**********************************
 *                                *
 *  [OptionalInt] ext. functions  *
 *                                *
 *********************************/

/**
 * Adds a map function to OptionalInt.
 */
fun <T> OptionalInt.map(mapper: (Int) -> T): Optional<T> {
    if (isPresent) {
        val newValue = mapper(asInt)
        return Optional.of(newValue)
    }
    return Optional.empty()
}

/**
 * Adds a mapToInt function to OptionalInt.
 */
fun OptionalInt.mapToInt(mapper: (Int) -> Int): OptionalInt {
    if (isPresent) {
        val newValue = mapper(asInt)
        return OptionalInt.of(newValue)
    }
    return OptionalInt.empty()
}
