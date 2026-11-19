package game.skill.crafting.shearing

import api.predef.useItem

SheepShearing.npcIds.forEach { id ->
    useItem(SheepShearing.SHEARS).onNpc(id) { SheepShearing.shear(plr, targetNpc) }
}
