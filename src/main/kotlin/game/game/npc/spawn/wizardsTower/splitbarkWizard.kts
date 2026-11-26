package game.npc.spawn.wizardsTower

import api.predef.*

npc1(SplitbarkArmour.WIZARD) { SplitbarkArmour.openMenu(plr) }

for (material in listOf(SplitbarkArmour.BARK, SplitbarkArmour.FINE_CLOTH)) {
    useItem(material).onNpc(SplitbarkArmour.WIZARD) { SplitbarkArmour.openMenu(plr) }
}
