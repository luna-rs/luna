package game.content.brimhavenArena

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.LogoutEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

on(ServerLaunchEvent::class) {
    ArenaTraps.rebuildPlanks()
    world.schedule(1) { BrimhavenArena.tick() }
}
on(LoginEvent::class) { BrimhavenArena.login(plr) }
on(LogoutEvent::class) { BrimhavenArena.leave(plr, clearPayment = false) }
npc1(437) { BrimhavenArena.talk(plr, targetNpc) }
npc3(437) { BrimhavenArena.pay(plr, targetNpc) }
npc1(1055) { BrimhavenArena.openRewards(plr, targetNpc) }
npc3(1055) { BrimhavenArena.openRewards(plr, targetNpc) }
for (id in listOf(3617, 3618, 3610)) object1(id) { BrimhavenArena.ladder(plr, gameObject) }
for (id in listOf(3608, 3581)) object1(id) { BrimhavenArena.tag(plr, gameObject) }
for (id in ArenaObstacles.ids) object1(id, { _, obj -> ArenaObstacles.policy(obj) }) {
    ArenaObstacles.cross(plr, gameObject)
}
for (reward in BrimhavenArena.Reward.values()) for (id in reward.buttons) button(id) {
    BrimhavenArena.exchange(plr, reward)
}
