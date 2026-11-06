package engine.bot

import api.predef.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.LoginEvent

on(LoginEvent::class, EventPriority.LOW) {
    BotItemAcquisitionLog.attach(plr)
}
