package game.bot.webwalk

import api.bot.webwalk.walk.WebWalker
import api.predef.*
import api.predef.ext.*
import io.luna.Luna
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

/**
 * Loads the web and makes the planner in the first tick after the server has launched, so that the first bot to web-walk
 * doesn't hold up the game thread while the data is read. That tick comes after the server has generated the data (see
 * webWalkGenerate.kts), because the game thread waits for it. It is skipped when there are no bots.
 */
on(ServerLaunchEvent::class) {
    if (Luna.settings().bots().baseOnlineCount() >= 1) {
        world.scheduleOnce(1) {
            try {
                WebWalker.warmUp()
            } catch (e: Exception) {
                logger.error("Could not load the web-walker. Web walks will fail until the data is fixed.", e)
            }
        }
    }
}
