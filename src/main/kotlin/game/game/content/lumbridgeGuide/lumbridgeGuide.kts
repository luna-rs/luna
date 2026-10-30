package game.content.lumbridgeGuide

import api.bot.action.trading.BotMarketplace
import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.area.SimpleBoxArea
import io.luna.game.model.mob.movement.wandering.DumbWanderingAction
import io.luna.game.model.mob.movement.wandering.WanderingFrequency

npc1(2244) {
    LumbridgeGuideDialogue(plr, targetNpc.id).open()
}

// Add a guide at every marketplace city anchor.
on(ServerLaunchEvent::class) {
    for (marketplace in BotMarketplace.entries) {
        val anchor = marketplace.parent.anchor
        val guideNpc = world.addNpc(id = 2244, x = anchor.x, y = anchor.y, z = anchor.z)
        guideNpc.submitAction(DumbWanderingAction(guideNpc, SimpleBoxArea.of(anchor, 3), WanderingFrequency.NORMAL))
    }
}
