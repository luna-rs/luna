import api.*
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent

/**
 * A set of objects used for banking.
 */
private val bankObjects = immutableSetOf(3193, 2213, 3095)

/**
 * Open the banking interface.
 */
on(ObjectFirstClickEvent::class)
    .condition { bankObjects.contains(it.id) }
    .run {
        it.plr.bank.open()
        it.terminate()
    }