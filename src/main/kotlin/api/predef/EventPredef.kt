package api.predef

import api.event.Matcher
import api.event.dsl.InterceptBy
import api.event.dsl.InterceptUseItem
import io.luna.game.event.Event
import io.luna.game.event.EventListener
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.CommandEvent
import io.luna.game.event.impl.GroundItemClickEvent.GroundItemSecondClickEvent
import io.luna.game.event.impl.ItemClickEvent.*
import io.luna.game.event.impl.NpcClickEvent.*
import io.luna.game.event.impl.ObjectClickEvent.*
import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent
import io.luna.game.event.impl.UseItemEvent.ItemOnObjectEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerRights
import java.util.*
import kotlin.reflect.KClass

/**
 * The player dedicated event listener consumer alias.
 */
typealias EventAction<E> = E.() -> Unit

/**
 * The command key, used to match [CommandEvent]s.
 */
class CommandKey(val name: String, val rights: PlayerRights) {
    override fun hashCode() = Objects.hash(name)
    override fun equals(other: Any?) =
        when (other) {
            is CommandKey -> name == other.name
            else -> false
        }
}

/**
 * The main event interception function. Forwards to [InterceptBy].
 */
fun <E : Event> on(eventClass: KClass<E>) = InterceptBy(eventClass)

/**
 * The main event interception function. Runs the action without any forwarding.
 */
fun <E : Event> on(eventClass: KClass<E>, action: EventAction<E>) {
    scriptListeners += EventListener(eventClass.java, action)
}

/**
 * The [ItemOnItemEvent] and [ItemOnObjectEvent] matcher function. Forwards to [InterceptUseItem].
 */
fun useItem(id: Int) = InterceptUseItem(id)

/**
 * The [ButtonClickEvent] matcher function.
 */
fun button(id: Int, action: EventAction<ButtonClickEvent>) =
    Matcher.get<ButtonClickEvent, Int>().set(id, action)


/** The [NpcFirstClickEvent] matcher function.*/
fun npc1(id: Int, action: EventAction<NpcFirstClickEvent>) =
    Matcher.get<NpcFirstClickEvent, Int>().set(id, action)

/** The [NpcSecondClickEvent] matcher function.*/
fun npc2(id: Int, action: EventAction<NpcSecondClickEvent>) =
    Matcher.get<NpcSecondClickEvent, Int>().set(id, action)

/** The [NpcThirdClickEvent] matcher function.*/
fun npc3(id: Int, action: EventAction<NpcThirdClickEvent>) =
    Matcher.get<NpcThirdClickEvent, Int>().set(id, action)

/** The [NpcFourthClickEvent] matcher function.*/
fun npc4(id: Int, action: EventAction<NpcFourthClickEvent>) =
    Matcher.get<NpcFourthClickEvent, Int>().set(id, action)

/** The [NpcFifthClickEvent] matcher function.*/
fun npc5(id: Int, action: EventAction<NpcFifthClickEvent>) =
    Matcher.get<NpcFifthClickEvent, Int>().set(id, action)

/**
 * The [GroundItemSecondClickEvent] matcher function.
 */
fun groundItem2(id: Int, action: EventAction<GroundItemSecondClickEvent>) =
    Matcher.get<GroundItemSecondClickEvent, Int>().set(id, action)

/** The [ItemFirstClickEvent] matcher function.*/
fun item1(id: Int, action: EventAction<ItemFirstClickEvent>) =
    Matcher.get<ItemFirstClickEvent, Int>().set(id, action)

/** The [ItemSecondClickEvent] matcher function.*/
fun item2(id: Int, action: EventAction<ItemSecondClickEvent>) =
    Matcher.get<ItemSecondClickEvent, Int>().set(id, action)

/** The [ItemThirdClickEvent] matcher function.*/
fun item3(id: Int, action: EventAction<ItemThirdClickEvent>) =
    Matcher.get<ItemThirdClickEvent, Int>().set(id, action)

/** The [ItemFourthClickEvent] matcher function.*/
fun item4(id: Int, action: EventAction<ItemFourthClickEvent>) =
    Matcher.get<ItemFourthClickEvent, Int>().set(id, action)

/** The [ItemFifthClickEvent] matcher function.*/
fun item5(id: Int, action: EventAction<ItemFifthClickEvent>) =
    Matcher.get<ItemFifthClickEvent, Int>().set(id, action)


/** The [ObjectFirstClickEvent] matcher function.*/
fun object1(id: Int, action: EventAction<ObjectFirstClickEvent>) =
    Matcher.get<ObjectFirstClickEvent, Int>().set(id, action)

/** The [ObjectSecondClickEvent] matcher function.*/
fun object2(id: Int, action: EventAction<ObjectSecondClickEvent>) =
    Matcher.get<ObjectSecondClickEvent, Int>().set(id, action)

/** The [ObjectThirdClickEvent] matcher function.*/
fun object3(id: Int, action: EventAction<ObjectThirdClickEvent>) =
    Matcher.get<ObjectThirdClickEvent, Int>().set(id, action)

/**
 * The [CommandEvent] matcher function.
 */
fun cmd(name: String, rights: PlayerRights = RIGHTS_PLAYER, action: CommandEvent.() -> Unit) {
    val matcher = Matcher.get<CommandEvent, CommandKey>()
    matcher[CommandKey(name, rights)] = {
        if (plr.rights >= rights) {
            action(this)
        }
    }
}

/**
 * Performs a lookup for a player based on the arguments from [index] onwards. By default, it starts from index 0.
 */
fun getPlayer(msg: CommandEvent, index: Int = 0, action: (Player) -> Unit) =
    world.getPlayer(msg.getInputFrom(index)).ifPresent(action)