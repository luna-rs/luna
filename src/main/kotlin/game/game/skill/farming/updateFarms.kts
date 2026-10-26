package game.skill.farming

import api.predef.*
import io.luna.game.event.impl.*

// Schedule task updating farms every minute
world.schedule(UpdateFarmsTask(world.players))

// Bring patches up to date and send farming state when logged in
on(LoginEvent::class) {
    Farming.update(plr)
    Farming.sendPatches(plr, true)
}
