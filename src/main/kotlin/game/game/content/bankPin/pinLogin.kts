package game.content.bankPin

import api.predef.*
import io.luna.game.event.impl.LoginEvent

on(LoginEvent::class) {
    if (!plr.isBot) {
        BankPin.remindPending(plr)
    }
}
