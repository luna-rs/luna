package game.content.barrows

import api.predef.*

for (index in 0..2) {
    button(4550 + index) {
        plr.overlays.getOverlay(BarrowsPuzzleInterface::class.java)?.answer(plr, index)
    }
}
