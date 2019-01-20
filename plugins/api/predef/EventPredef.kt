package api.predef

import api.event.InterceptBy
import api.event.Matcher
import io.luna.game.event.Event
import io.luna.game.event.EventListener
import io.luna.game.event.button.ButtonClickEvent
import io.luna.game.event.command.CommandEvent
import io.luna.game.event.item.ItemClickEvent.*
import io.luna.game.event.entity.player.NpcClickEvent.*
import io.luna.game.event.entity.player.ObjectClickEvent.*
import io.luna.game.model.mob.PlayerRights
import kotlin.reflect.KClass

/**
 * The player dedicated event listener consumer alias.
 */
private typealias Action<E> = E.() -> Unit

/**
 * The main event interception function. Forwards to the [InterceptBy].
 */
fun <E : Event> on(eventClass: KClass<E>) = InterceptBy(eventClass)

/**
 * The main event interception function. Runs the action without any forwarding.
 */
fun <E : Event> on(eventClass: KClass<E>, action: Action<E>) {
    scriptListeners.add(EventListener(eventClass.java, action))

}

/**
 * The [CommandEvent] interception function.
 */
fun cmd(name: String, rights: PlayerRights, action: Action<CommandEvent>) {
    val matcher = Matcher.get<CommandEvent, String>()
    matcher[name] = {
        if (plr.rights >= rights) {
            action(this)
        }
    }
}

/**
 * The [ButtonClickEvent] interception function.
 */
fun button(id: Int, action: Action<ButtonClickEvent>) =
    Matcher.get<ButtonClickEvent, Int>().set(id, action)


/** The [NpcFirstClickEvent] interception function.*/
fun npc1(id: Int, action: Action<NpcFirstClickEvent>) =
    Matcher.get<NpcFirstClickEvent, Int>().set(id, action)

/** The [NpcSecondClickEvent] interception function.*/
fun npc2(id: Int, action: Action<NpcSecondClickEvent>) =
    Matcher.get<NpcSecondClickEvent, Int>().set(id, action)

/** The [NpcThirdClickEvent] interception function.*/
fun npc3(id: Int, action: Action<NpcThirdClickEvent>) =
    Matcher.get<NpcThirdClickEvent, Int>().set(id, action)

/** The [NpcFourthClickEvent] interception function.*/
fun npc4(id: Int, action: Action<NpcFourthClickEvent>) =
    Matcher.get<NpcFourthClickEvent, Int>().set(id, action)

/** The [NpcFifthClickEvent] interception function.*/
fun npc5(id: Int, action: Action<NpcFifthClickEvent>) =
    Matcher.get<NpcFifthClickEvent, Int>().set(id, action)


/** The [ItemFirstClickEvent] interception function.*/
fun item1(id: Int, action: Action<ItemFirstClickEvent>) =
    Matcher.get<ItemFirstClickEvent, Int>().set(id, action)

/** The [ItemSecondClickEvent] interception function.*/
fun item2(id: Int, action: Action<ItemSecondClickEvent>) =
    Matcher.get<ItemSecondClickEvent, Int>().set(id, action)

/** The [ItemThirdClickEvent] interception function.*/
fun item3(id: Int, action: Action<ItemThirdClickEvent>) =
    Matcher.get<ItemThirdClickEvent, Int>().set(id, action)

/** The [ItemFourthClickEvent] interception function.*/
fun item4(id: Int, action: Action<ItemFourthClickEvent>) =
    Matcher.get<ItemFourthClickEvent, Int>().set(id, action)

/** The [ItemFifthClickEvent] interception function.*/
fun item5(id: Int, action: Action<ItemFifthClickEvent>) =
    Matcher.get<ItemFifthClickEvent, Int>().set(id, action)


/** The [ObjectFirstClickEvent] interception function.*/
fun object1(id: Int, action: Action<ObjectFirstClickEvent>) =
    Matcher.get<ObjectFirstClickEvent, Int>().set(id, action)

/** The [ObjectSecondClickEvent] interception function.*/
fun object2(id: Int, action: Action<ObjectSecondClickEvent>) =
    Matcher.get<ObjectSecondClickEvent, Int>().set(id, action)

/** The [ObjectThirdClickEvent] interception function.*/
fun object3(id: Int, action: Action<ObjectThirdClickEvent>) =
    Matcher.get<ObjectThirdClickEvent, Int>().set(id, action)