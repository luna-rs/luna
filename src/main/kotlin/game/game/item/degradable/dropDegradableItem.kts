package game.item.degradable

import api.predef.*
import api.predef.ext.*

// Drops and fully degrades the item if the dialogue is open.
button(14175) {
    plr.overlays[DegradableDropWarningDialogue::class]?.dropItem(plr)
}