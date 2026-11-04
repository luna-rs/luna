package game.skill.agility.shortcut

import api.predef.*

for (id in AgilityShortcut.IDS) {
    object1(id, { _, target -> AgilityShortcut.reach(target) }) { AgilityShortcut.use(plr, gameObject) }
}

for (id in AgilityShortcut.ROPE_IDS) {
    useItem(AgilityShortcut.ROPE).onObject(id) { AgilityShortcut.use(plr, gameObject) }
}

for (id in SteppingStones.IDS) {
    object1(id) { SteppingStones.jump(plr, gameObject) }
}

for (id in SpikyChains.IDS) {
    object1(id) { SpikyChains.climb(plr, gameObject) }
}
