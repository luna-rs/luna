package game.skill.magic.bonesToItems

import api.predef.*

for (type in BonesToItemsType.entries) {
    button(type.spellId) { plr.submitAction(BonesToItemsAction(plr, type)) }
}
