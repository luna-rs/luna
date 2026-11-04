package game.content.ectofuntus

import api.predef.*
import io.luna.game.event.impl.LoginEvent

on(LoginEvent::class) { Ectofuntus.restore(plr) }

for (bone in EctofuntusBone.entries) {
    useItem(bone.boneId).onObject(Ectofuntus.LOADER) {
        Ectofuntus.load(plr, gameObject, usedItemIndex, bone)
    }
    item2(bone.bonemealId) { Ectofuntus.empty(plr, index, id, Ectofuntus.POT) }
}
object1(Ectofuntus.GRINDER) { Ectofuntus.grind(plr, gameObject) }
object2(Ectofuntus.GRINDER) { Ectofuntus.status(plr, gameObject) }
object1(Ectofuntus.BIN) { Ectofuntus.collect(plr, gameObject) }
for (pool in Ectofuntus.POOL_IDS) {
    useItem(Ectofuntus.BUCKET).onObject(pool) { Ectofuntus.fill(plr, gameObject, usedItemIndex) }
}
item4(Ectofuntus.SLIME) { Ectofuntus.empty(plr, index, id, Ectofuntus.BUCKET) }
object1(Ectofuntus.FOUNTAIN) { Ectofuntus.worship(plr, gameObject) }
for (disciple in Ectofuntus.DISCIPLE_IDS) npc1(disciple) { Ectofuntus.claim(plr, targetNpc) }
