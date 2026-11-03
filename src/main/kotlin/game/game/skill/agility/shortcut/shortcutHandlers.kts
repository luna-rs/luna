package game.skill.agility.shortcut

import api.predef.*

for (id in AgilityShortcut.IDS) {
    object1(id) { AgilityShortcut.use(plr, gameObject) }
}

for (id in SteppingStones.IDS) {
    object1(id) { SteppingStones.jump(plr, gameObject) }
}
