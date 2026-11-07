package engine.widget

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.NumberInputEvent
import io.luna.game.event.impl.TextInputEvent
import io.luna.game.model.mob.overlay.NumberInput
import io.luna.game.model.mob.overlay.OverlayType
import io.luna.game.model.mob.overlay.TextInput

/**
 * Handles the text input interface.
 */
on(TextInputEvent::class, EventPriority.HIGH) {
    val input = plr.overlays[TextInput::class]
    plr.overlays.overlayMap.remove(OverlayType.INPUT)
    if (text.isNotEmpty()) {
        input?.input(plr, text)
    }
}

/**
 * Handles the number input interface.
 */
on(NumberInputEvent::class, EventPriority.HIGH) {
    val input = plr.overlays[NumberInput::class]
    plr.overlays.overlayMap.remove(OverlayType.INPUT)
    input?.input(plr, number)
}