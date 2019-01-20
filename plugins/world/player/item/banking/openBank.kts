import api.predef.*
import io.luna.game.event.entity.player.ObjectClickEvent.ObjectFirstClickEvent

/**
 * A set of objects used for banking.
 */
val bankObjects: Set<Int> = hashSetOf(3193, 2213, 3095)

/**
 * Open the banking interface.
 */
on(ObjectFirstClickEvent::class)
    .match(bankObjects)
    .then { plr.bank.open() }
