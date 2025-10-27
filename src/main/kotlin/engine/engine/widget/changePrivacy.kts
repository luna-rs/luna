package engine.widget

import api.predef.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.PrivacyModeChangedEvent

/**
 * Sets the new privacy options.
 */
on(PrivacyModeChangedEvent::class, EventPriority.HIGH) {
    plr.privacyOptions = newPrivacy
}