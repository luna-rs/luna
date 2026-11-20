package engine.bot.coordinator

import api.predef.*
import api.predef.ext.schedule
import io.luna.game.event.impl.ServerStateChangedEvent.ServerReadyEvent
import io.luna.game.model.mob.bot.Bot

on(ServerReadyEvent::class) {
    val coverage = PkCoverage.live
    world.schedule(10, instant = true) {
        val bots = world.players.filterIsInstance<Bot>()
        val now = System.currentTimeMillis()
        coverage.review(bots, now)?.let { logger.info(it) }
        WildernessProfitCoverage.review(bots, now)?.let { logger.info(it) }
    }
}
