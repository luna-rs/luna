package world.player.item.banking.depositBox

import api.predef.*
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent

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