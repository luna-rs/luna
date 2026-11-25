package game.skill.magic.bonesToItems

import api.predef.*
import game.content.mageTrainingArena.MageTrainingArena.bonesToPeachesUnlocked

for (type in BonesToItemsType.entries) {
    button(type.spellId) {
        // Bones to Peaches is bought from the Mage Training Arena's Rewards Guardian.
        if (type == BonesToItemsType.PEACHES && !plr.bonesToPeachesUnlocked) {
            plr.sendMessage("You need to learn the spell from the Mage Training arena")
            return@button
        }
        plr.submitAction(BonesToItemsAction(plr, type))
    }
}
