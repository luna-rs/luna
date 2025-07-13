package world.player.skill.magic.bonesToItems

import api.predef.button

// Bones to bananas.
button(1159) { plr.submitAction(BonesToItemsAction(plr, BonesToItemsType.BANANAS)) }

// Bones to peaches.
button(15877) { plr.submitAction(BonesToItemsAction(plr, BonesToItemsType.PEACHES)) }