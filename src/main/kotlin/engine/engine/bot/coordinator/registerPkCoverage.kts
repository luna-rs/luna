package engine.bot.coordinator

import api.predef.*
import api.predef.ext.schedule
import io.luna.game.event.impl.ServerStateChangedEvent.ServerReadyEvent
import io.luna.game.model.mob.bot.Bot

on(ServerReadyEvent::class) {
    val coverage = PkCoverage.live
    world.schedule(10, instant = true) {
        coverage.review(world.players.filterIsInstance<Bot>())?.let { logger.info(it) }
    }
}
