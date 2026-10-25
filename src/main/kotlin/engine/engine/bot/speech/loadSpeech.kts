package engine.bot.speech;

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ChatEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerReadyEvent

// Load the reaction speech pool.
on(ServerLaunchEvent::class) {
    taskPool.execute {
        BotReactionSpeechPool.load()
        BotPkingSpeechPool.load()
        BotPriceCheckSpeechPool.load()
    }
}

// Register context injector for OTHER_LEVEL_UP reactions.
BotReactions.reactToOtherLevelUp()

// Bots answer price checks in public chat, and ask them at the markets.
BotChatRouter.add(BotPriceChecks)
on(ChatEvent::class) { BotChatRouter.route(this) }
on(ServerReadyEvent::class) {
    world.schedule(10) { BotPriceChecks.askAtMarkets() }
}
