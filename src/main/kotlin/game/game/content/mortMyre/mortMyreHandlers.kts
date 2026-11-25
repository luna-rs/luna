package game.content.mortMyre

import api.combat.death.DeathHookHandler
import api.predef.*
import api.predef.ext.*
import game.content.mortMyre.MortMyre.BLESSED_SICKLE
import game.content.mortMyre.MortMyre.Blossom
import game.content.mortMyre.MortMyre.DRUID_POUCH
import game.content.mortMyre.MortMyre.EMPTY_POUCH
import game.content.mortMyre.MortMyre.SILVER_SICKLE
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.mob.Player

item3(BLESSED_SICKLE) { MortMyre.castBloom(plr) }

object2(Blossom.LOG.bloomed) { MortMyre.pick(plr, gameObject, Blossom.LOG) }
object2(Blossom.BRANCH.bloomed) { MortMyre.pick(plr, gameObject, Blossom.BRANCH) }
object2(Blossom.BUSH.bloomed) { MortMyre.pick(plr, gameObject, Blossom.BUSH) }

for (pouch in listOf(EMPTY_POUCH, DRUID_POUCH)) {
    item1(pouch) { MortMyre.fillPouch(plr) }
    for (blossom in Blossom.entries) {
        useItem(blossom.item).onItem(pouch) { MortMyre.fillPouch(plr) }
    }
}

object1(MortMyre.GROTTO_TREE) {
    plr.sendMessage("It looks like a tree on a large rock with roots trailing down to the ground.")
}
object2(MortMyre.GROTTO_TREE) { plr.sendMessage("You find nothing interesting.") }
object1(MortMyre.NATURE_ALTAR) { MortMyre.prayAtAltar(plr) }
for (water in listOf(MortMyre.GROTTO_POOL, MortMyre.NATURE_ALTAR)) {
    useItem(SILVER_SICKLE).onObject(water) { MortMyre.blessSickle(plr, usedItemIndex) }
}

object1(MortMyre.BRIDGE) { MortMyre.jumpBridge(plr, gameObject) }

DeathHookHandler.addNpcHook(MortMyreGhasts.VISIBLE) {
    death { MortMyreGhasts.onKill(victim, source as? Player) }
    DeathHookHandler.defaultNpcHook?.invoke(this)
}

on(ServerLaunchEvent::class) {
    world.schedule(1) { MortMyreGhasts.process() }
}
