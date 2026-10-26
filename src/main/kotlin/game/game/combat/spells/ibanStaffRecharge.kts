package game.combat.spells

import api.predef.*

useItem(IbanStaff.STAFF).onObject(IbanStaff.WELL) {
    IbanStaff.recharge(plr, usedItemIndex)
}
