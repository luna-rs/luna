/*
 * Adapted from LostCityRS/Content scripts/quests/quest_zanaris and areas/area_lumbridge,
 * revision 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
 */
package game.content.lostCity

import api.combat.death.DeathHookHandler
import api.predef.*
import game.skill.woodcutting.cutTree.Axe
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.LogoutEvent
import io.luna.game.model.mob.block.Animation

npc1(LostCity.WARRIOR) { LostCityDialogue.warrior(plr, targetNpc) }
npc1(LostCity.SHAMUS) { LostCityDialogue.shamus(plr, targetNpc) }
npc1(EntranaCaveMonk.NPC) { EntranaCaveMonk.talk(plr, targetNpc) }
object1(EntranaCaveMonk.LADDER) { EntranaCaveMonk.ladder(plr, gameObject) }
object1(LostCity.SHAMUS_TREE) { LostCity.shakeTree(plr, gameObject) }
object1(LostCity.DRAMEN_TREE) { LostCity.cutDramen(plr, gameObject) }
button(LostCity.JOURNAL_BUTTON) { LostCity.journal(plr) }
useItem(LostCity.KNIFE).onItem(LostCity.BRANCH) { LostCity.carve(plr) }
for (axe in Axe.entries) {
    useItem(axe.id).onObject(LostCity.SHAMUS_TREE) { LostCity.shakeTree(plr, gameObject) }
    useItem(axe.id).onObject(LostCity.DRAMEN_TREE) { LostCity.cutDramen(plr, gameObject) }
}
for (herring in listOf(345, 347)) useItem(herring).onObject(LostCity.SHAMUS_TREE) {
    plr.sendMessage("This is not the mightiest tree in the forest.")
}
on(LoginEvent::class) { LostCity.refresh(plr) }
on(LogoutEvent::class) { LostCity.clear(plr) }

DeathHookHandler.addNpcHook(LostCity.SPIRIT) {
    val spirit = victim
    if (spirit is DramenSpirit) {
        preDeath { spirit.animation(Animation(spirit.combatDef().deathAnimation)) }
        death { LostCity.defeated(spirit, source) }
    } else DeathHookHandler.defaultNpcHook?.invoke(this)
}
