package game.bot.webwalk

import api.bot.webwalk.generate.WebWalkGenerationRunner
import api.predef.*
import api.predef.ext.*
import io.luna.Luna
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerReadyEvent

/**
 * The system property that makes the server generate the web-walker data once it has launched, and then exit. Its value
 * is `check` to only report the files that are out of date, and anything else to write them. It is set by the
 * `generateWebWalk` Gradle task.
 */
val GENERATE_PROPERTY = "luna.webwalk.generate"

/**
 * Generates the data in the first tick after the server has launched, if the Gradle task asked for it.
 */
on(ServerLaunchEvent::class) {
    val mode = System.getProperty(GENERATE_PROPERTY)
    if (mode != null) {
        world.scheduleOnce(1) {
            WebWalkGenerationRunner.run(mode == "check")
        }
    }
}

/**
 * Generates the data if the server starts without it, which is the case for a new checkout because the files are not in the
 * repository. The server waits for it, so the data is there before the first player or bot is on, but it makes the first start
 * slower. This is skipped when there are no bots, because only they use the data, and when the Gradle task is generating it.
 * This is not done for a [ServerLaunchEvent] because the other listeners of it still change the world, such as by removing
 * objects and loading the banking objects, which the data depends on.
 */
on(ServerReadyEvent::class) {
    if (System.getProperty(GENERATE_PROPERTY) != null || Luna.settings().bots().baseOnlineCount() < 1) {
        return@on
    }
    val missing = WebWalkGenerationRunner.missingFiles()
    if (missing.isNotEmpty()) {
        logger.info("The web-walker data is missing ({}), so it is being generated. The server will go online once it is " +
                            "done, which takes a while.", missing.joinToString())
        WebWalkGenerationRunner.generateMissing(taskPool)
    }
}
