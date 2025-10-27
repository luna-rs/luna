package engine.widget

import api.predef.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.TextInputEvent
import io.luna.game.event.impl.NumberInputEvent
import java.util.*

/**
 * Handles the text input interface.
 */
on(TextInputEvent::class, EventPriority.HIGH) {
    plr.interfaces.currentInput.ifPresent {
        it.applyInput(plr, OptionalInt.empty(), Optional.of(text))
        plr.interfaces.resetCurrentInput()
    }
}

/**
 * Handles the number input interface.
 */
on(NumberInputEvent::class, EventPriority.HIGH) {
    plr.interfaces.currentInput.ifPresent {
        it.applyInput(plr, OptionalInt.of(number), Optional.empty())
        plr.interfaces.resetCurrentInput()
    }
}