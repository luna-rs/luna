package game.skill.runecrafting.essenceMine

import api.predef.*
import io.luna.game.event.impl.LoginEvent

/*
 * Returns players through the mine's portals to the wizard who sent them.
 */
object1(EssenceMine.PORTAL) {
    EssenceMine.leave(plr)
}

on(LoginEvent::class) {
    EssenceMine.login(plr)
}
