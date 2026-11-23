package game.content.bankPin

import api.predef.*
import game.player.Sound
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.item.Bank
import io.luna.game.model.mob.Player

on(ServerLaunchEvent::class) {
    Bank.setOpenCheck(BankPin::mayOpenBank)
}

PinEntryInterface.BUTTONS.forEach {
    button(it) {
        val keypad = plr.overlays.getOverlay(PinEntryInterface::class.java) ?: return@button
        plr.playSound(Sound.PIN_BEEP)
        if (keypad.press(plr, id)) {
            BankPin.entered(plr, keypad)
        }
    }
}
button(PinEntryInterface.EXIT_BUTTON) {
    if (plr.overlays.has(PinEntryInterface::class.java)) {
        plr.playSound(Sound.PIN_CANCEL)
        plr.overlays.closeWindows()
    }
}
button(PinEntryInterface.FORGOT_BUTTON) {
    if (plr.overlays.getOverlay(PinEntryInterface::class.java)?.purpose?.forgettable == true) {
        BankPin.forgot(plr)
    }
}

/**
 * Runs [action] on the open settings window, if there is one.
 */
fun settingsButton(id: Int, action: (Player, PinSettingsInterface) -> Unit) {
    button(id) { plr.overlays.getOverlay(PinSettingsInterface::class.java)?.let { action(plr, it) } }
}

settingsButton(PinSettingsInterface.SET_BUTTON) { plr, _ -> BankPin.openKeypad(plr, PinPurpose.NEW_PIN) }
settingsButton(PinSettingsInterface.CHANGE_BUTTON) { plr, _ -> BankPin.openKeypad(plr, PinPurpose.NEW_PIN) }
listOf(PinSettingsInterface.NO_PIN_DELAY_BUTTON, PinSettingsInterface.PIN_DELAY_BUTTON).forEach { delayButton ->
    settingsButton(delayButton) { plr, settings ->
        BankPin.toggleDelay(plr)
        settings.refresh(plr)
    }
}
settingsButton(PinSettingsInterface.DELETE_BUTTON) { plr, settings ->
    settings.ask(plr) {
        BankPin.delete(it)
        settings.refresh(it, "Your PIN has been deleted.")
    }
}
settingsButton(PinSettingsInterface.CANCEL_PENDING_BUTTON) { plr, settings ->
    settings.ask(plr) {
        BankPin.cancelPending(it)
        settings.refresh(it, "The PIN has been cancelled and will NOT be set.")
    }
}
settingsButton(PinSettingsInterface.YES_BUTTON) { plr, settings -> settings.pendingQuestion?.invoke(plr) }
settingsButton(PinSettingsInterface.NO_BUTTON) { plr, settings ->
    if (settings.pendingQuestion != null) {
        settings.refresh(plr)
    }
}
