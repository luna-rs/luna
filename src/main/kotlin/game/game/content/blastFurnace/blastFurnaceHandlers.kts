package game.content.blastFurnace

import api.predef.*
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.mob.overlay.StandardInterface

on(LoginEvent::class) { BlastFurnace.restore(plr) }

npc1(2553) {
    if (!BlastFurnace.present(plr)) return@npc1
    plr.newDialogue().options("Pay ${BlastFurnace.FEE} coins for ten minutes.", { BlastFurnace.pay(it) },
        "Show me the furnace plan.", { it.overlays.open(StandardInterface(14578)) },
        "No, thank you.", {}).title("Blast Furnace access (free at 60 Smithing)").open()
}
object1(9100) { BlastFurnace.deposit(plr) }
object1(9088) { BlastFurnace.collectCoke(plr) }
for (id in 9085..9087) object1(id) { BlastFurnace.refuel(plr) }
object1(9090) { BlastFurnace.operate(plr, BlastFurnaceWorkAction.Role.PUMP) }
object1(9097) { BlastFurnace.operate(plr, BlastFurnaceWorkAction.Role.PEDALS) }
object1(9089) {
    if (BlastFurnace.present(plr)) {
        BlastFurnace.publish(plr)
        plr.overlays.open(StandardInterface(14594))
    }
}
object1(9143) { BlastFurnace.fillBucket(plr) }
for (id in listOf(9092, 9093, 9095, 9096)) object1(id) { BlastFurnace.openStock(plr) }
for (damage in BlastFurnace.Damage.entries) object1(damage.broken) { BlastFurnace.repair(plr, damage) }
useItem(1929).onObject(9092) { BlastFurnace.cool(plr) }
useItem(1929).onObject(9095) { BlastFurnace.cool(plr) }
useItem(1925).onObject(9143) { BlastFurnace.fillBucket(plr) }
for (bar in BlastFurnaceInterface.order) {
    button(BlastFurnaceInterface.ID + 1 + BlastFurnaceInterface.single.getValue(bar)) {
        plr.overlays.getOverlay(BlastFurnaceInterface::class.java)?.take(plr, bar, false)
    }
    button(BlastFurnaceInterface.allButton(bar)) {
        plr.overlays.getOverlay(BlastFurnaceInterface::class.java)?.take(plr, bar, true)
    }
}
