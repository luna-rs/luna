package engine.bot.speech;

import api.predef.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

// Load the reaction speech pool.
on(ServerLaunchEvent::class) {
    taskPool.execute {
        BotReactionSpeechPool.load()
        BotPkingSpeechPool.load()
    }
}

// Register context injector for OTHER_LEVEL_UP reactions.
BotReactions.reactToOtherLevelUp()
