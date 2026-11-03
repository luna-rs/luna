/*
 * Adapted from LostCityRS/Content scripts/quests/quest_zanaris and areas/area_lumbridge,
 * revision 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
 * MIT License
 * Copyright (c) 2023-2026 Lost City
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
