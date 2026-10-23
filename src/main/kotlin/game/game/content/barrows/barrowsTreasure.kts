package game.content.barrows

import api.predef.*
import io.luna.game.event.impl.LoginEvent

on(LoginEvent::class) { BarrowsChest.restore(plr) }

// The map contains the morph parent. Both closed and open appearances use operation one.
object1(10284) {
    if (gameObject.position == BarrowsChest.position) BarrowsChest.search(plr)
}
