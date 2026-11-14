package game.content.minecart

import api.predef.*
import io.luna.game.event.impl.LoginEvent

on(LoginEvent::class) { KeldagrimMinecart.restore(plr) }
for (route in KeldagrimMinecart.Route.entries) for (id in route.crew) {
    npc1(id) { KeldagrimMinecart.talk(plr, targetNpc) }
}
object1(KeldagrimMinecart.ICE_CART) { KeldagrimMinecart.useCart(plr, gameObject) }
