import api.predef.*
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import world.player.item.banking.open.DepositBoxInterface

/**
 * A set of deposit boxes.
 */
val depositBoxObjects: Set<Int> = hashSetOf(9398)

/**
 * Open the deposit box.
 */
on(ObjectFirstClickEvent::class)
    .match(depositBoxObjects)
    .then { plr.interfaces.open(DepositBoxInterface()) }