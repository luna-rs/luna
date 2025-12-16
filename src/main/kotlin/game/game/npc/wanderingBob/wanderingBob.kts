package game.npc.wanderingBob

import api.predef.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.Position
import io.luna.game.model.area.Area
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.dialogue.Expression
import io.luna.game.model.mob.wandering.SmartWanderingAction
import io.luna.game.model.mob.wandering.WanderingFrequency

on(ServerLaunchEvent::class) {
    val spawnPosition = Position(3080, 3250)
    val wanderArea = Area.of(spawnPosition, 1500)
    val bob = Npc(ctx, 2636, spawnPosition)
    bob.submitAction(SmartWanderingAction(bob, wanderArea, WanderingFrequency.NORMAL))
    world.npcs.add(bob)
}

npc1(2636) {
    plr.newDialogue().npc(targetNpc.id, Expression.ANGRY, "What?!? How did you find me?").open()
}