package game.content.randomEvents

import api.predef.*
import io.luna.game.event.impl.PlayerTickEvent

val chestGas = ChestGasRandomEvent
on(PlayerTickEvent::class) { chestGas.tick(plr) }
