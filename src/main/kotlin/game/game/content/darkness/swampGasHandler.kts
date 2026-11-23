package game.content.darkness

import api.predef.*
import io.luna.game.event.impl.PlayerTickEvent

on(PlayerTickEvent::class) { SwampGas.tick(plr) }
