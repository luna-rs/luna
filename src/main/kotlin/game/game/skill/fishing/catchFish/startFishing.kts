package game.skill.fishing.catchFish

import api.predef.*
import io.luna.game.model.Entity
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * Submits a [CatchFishAction] to start fishing.
 */
fun fish(plr: Player, target: Entity, tool: Tool) {
    plr.submitAction(CatchFishAction(plr, target, tool))
}

// Fishing spot options.
for (spot in FishingSpot.entries) {
    val secondOption = spot.secondOption
    for (id in spot.npcs) {
        npc1(id, false) { fish(plr, targetNpc, spot.firstOption) }
        if (secondOption != null) {
            npc2(id, false) { fish(plr, targetNpc, secondOption) }
        }
    }
}

// The Hemenster spots are only for the Fishing Contest.
for (id in 233..236) {
    npc1(id, false) {
        // TODO Fishing Contest: competitors fish here, and Bonzo assigns them a spot.
        if (world.locator.findNpcs(plr, 12) { it.id == 225 }.isNotEmpty()) {
            plr.newDialogue().npc(225, Expression.HAPPY, "Hey, you need to pay to enter the competition first!",
                                  "Only 5 gp entrance fee!").open()
            plr.sendMessage("Talk to Bonzo to pay the entrance fee.")
        }
    }
}
