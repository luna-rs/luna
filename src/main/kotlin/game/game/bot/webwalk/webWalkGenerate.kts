package game.bot.webwalk

import api.bot.webwalk.generate.WebWalkGenerationRunner
import api.predef.*
import api.predef.ext.*
import io.luna.Luna
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerReadyEvent

/**
 * Generates the data every time the server starts, so that it always matches the cache and the world as they are. The server
 * waits for it, so the data is there before the first player or bot is on, but it makes every start slower. This is skipped
 * when there are no bots, because only they use the data. This is not done for a [ServerLaunchEvent] because the other
 * listeners of it still change the world, such as by removing objects and loading the banking objects, which the data
 * depends on.
 */
on(ServerReadyEvent::class) {
    if (Luna.settings().bots().baseOnlineCount() < 1) {
        return@on
    }
    logger.info("Generating the web-walker data. The server will go online once it is done, which takes a while.")
    WebWalkGenerationRunner.generate(taskPool)
}
