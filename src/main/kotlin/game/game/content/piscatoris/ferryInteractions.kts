package game.content.piscatoris

import api.predef.*

for (id in PiscatorisFerry.CREW) {
    npc1(id) { PiscatorisFerry.talk(plr, targetNpc) }
}

// The pre-quest child 3830 only offers Talk-to; the raw morph parent can display the Travel form.
for (id in setOf(3829, 3831)) {
    npc3(id) { PiscatorisFerry.sail(plr, targetNpc) }
}
