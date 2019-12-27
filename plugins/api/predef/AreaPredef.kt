package api.predef

import api.area.AreaReceiver
import api.area.ListeningAreaReceiver
import io.luna.game.model.Area
import io.luna.game.model.Entity
import io.luna.game.model.mob.Player
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

/**
 * Dynamically constructs and registers a new implementation of [parentType]. The [swX], [swY], [neX], and [neY]
 * integer properties need to be defined in the parent's primary constructor and in the receiver function.
 *
 * Usage looks like as follows
 *
 * ```
 * newArea(MyArea::class) {
 *     swX = <x1>; swY = <y1>; neX = <x2>; neY = <y2>
 * }
 * ```
 */
fun <E : Area> area(parentType: KClass<E>, receiver: AreaReceiver.() -> Unit): E {
    // TODO Unit tests
    // TODO find a different way to do this.
   val primaryConstructor = parentType.primaryConstructor
    require(primaryConstructor != null) { "Parent area must have a primary constructor." }

    val parameters = primaryConstructor.parameters
    require(parameters.size == 4) { "Parent area's primary constructor must be 4 Int values." }
    for (param in parameters) {
        require(param.type.jvmErasure == Int::class) { "Parent area's primary constructor must be 4 Int values." }
    }

    val receiverInstance = AreaReceiver()
    receiver(receiverInstance)
    receiverInstance.validate()

    var areaInstance = primaryConstructor.call(receiverInstance.swX,
                                               receiverInstance.swY,
                                               receiverInstance.neX,
                                               receiverInstance.neY)
    world.areas.register(areaInstance)
    return areaInstance
}

/**
 * Dynamically constructs and registers a new [Area]. The [swX], [swY], [neX], [neY] integer properties need to be
 * defined in the receiver function.
 *
 * Usage looks like as follows
 *
 * ```
 * newArea {
 *     swX = <x1>; swY = <y1>; neX = <x2>; neY = <y2>
 *
 *     // The following below are optional.
 *     enter = {
 *         sendMessage("You have entered <area>.")
 *     }
 *     exit = {
 *         sendMessage("You have exited <area>.")
 *     }
 *     move = {
 *         sendMessage("You have moved within <area>.")
 *     }
 * }
 * ```
 */
fun area(receiver: ListeningAreaReceiver.() -> Unit): Area {
    // TODO Unit tests
    val receiverInstance = ListeningAreaReceiver()
    receiver(receiverInstance)
    receiverInstance.validate()

    val areaInstance = object : Area(receiverInstance.swX!!, receiverInstance.swY!!,
                                     receiverInstance.neX!!, receiverInstance.neY!!) {
        override fun exit(player: Player) {
            receiverInstance.exit?.invoke(player)
        }

        override fun enter(player: Player) {
            receiverInstance.enter?.invoke(player)
        }

        override fun move(player: Player) {
            receiverInstance.move?.invoke(player)
        }
    }
    world.areas.register(areaInstance)
    return areaInstance
}

/**
 * Returns `true` if this player is inside an [areaType] area.
 */
fun Entity.inside(areaType: KClass<out Area>) =
    world.areas.stream().anyMatch { it.contains(this) && areaType.isInstance(it) }