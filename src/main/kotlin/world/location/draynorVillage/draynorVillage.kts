package world.location.draynorVillage

import api.predef.*
import api.predef.ext.*
import api.shop.dsl.ShopHandler
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag
import io.luna.game.model.mob.dialogue.Expression
import world.player.advanceLevel.LevelUpInterface

on(ServerLaunchEvent::class) {
    // Bankers
    world.addNpc(494, 3090, 3245)
    world.addNpc(494, 3090, 3243)
    world.addNpc(494, 3090, 3242)

    // Dark wizards
    world.addNpc(174, 3086, 3238)
    world.addNpc(174, 3084, 3235)
}