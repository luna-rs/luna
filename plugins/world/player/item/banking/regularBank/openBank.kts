package world.player.item.banking.regularBank

import api.predef.*
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent

/**
 * A set of banking objects.
 */
val bankObjects: Set<Int> = hashSetOf(3193, 2213, 3095)

/**
 * Open the banking interface.
 */
on(ObjectFirstClickEvent::class)
    .match(bankObjects)
    .then { plr.bank.open() }
