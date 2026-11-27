package game.content.vampireSlayer

import api.combat.npc.NpcCombatHandler
import api.combat.npc.dsl.NpcCombatReceiver
import api.predef.*
import io.luna.game.event.impl.LoginEvent

on(LoginEvent::class) { VampireSlayer.refresh(plr) }
button(VampireSlayer.JOURNAL_BUTTON) { VampireSlayer.journal(plr) }
npc1(VampireSlayer.MORGAN) { VampireDialogue.morgan(plr) }
npc1(VampireSlayer.HARLOW) { VampireDialogue.harlow(plr) }

object1(VampireProps.CUPBOARD) { VampireProps.openCupboard(gameObject) }
object1(VampireProps.CUPBOARD_OPEN) { VampireProps.searchCupboard(plr) }
object2(VampireProps.CUPBOARD_OPEN) { VampireProps.close(plr, gameObject, VampireProps.CUPBOARD) }
object1(VampireProps.COFFIN) { VampireProps.openCoffin(plr, gameObject) }
object1(VampireProps.COFFIN_OPEN) { plr.newDialogue().text("There's only a pillow in here...").open() }
object2(VampireProps.COFFIN_OPEN) { VampireProps.close(plr, gameObject, VampireProps.COFFIN) }

NpcCombatHandler.typeMap[CountDraynor::class] = NpcCombatReceiver().apply {
    // Environmental damage and recoil use the native finishing-item cap.
    poisonCanKill = false
    attack {
        (npc as CountDraynor).weaken()
        default()
    }
    defend { damage = (npc as CountDraynor).defend(damage) }
    afterDamage { (this as CountDraynor).afterHit(it) }
}
