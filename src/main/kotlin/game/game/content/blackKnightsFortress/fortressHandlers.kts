package game.content.blackKnightsFortress

import api.predef.*
import io.luna.game.event.impl.LoginEvent

on(LoginEvent::class) { BlackKnightsFortress.refresh(plr) }
button(BlackKnightsFortress.JOURNAL_BUTTON) { BlackKnightsFortress.journal(plr) }
npc1(BlackKnightsFortress.SIR_AMIK) { BlackKnightsFortress.speakToSirAmik(plr, targetNpc) }
npc1(BlackKnightsFortress.FORTRESS_GUARD) { BlackKnightsFortress.speakToGuard(plr) }
object1(BlackKnightsFortress.GRILL) { BlackKnightsFortress.listen(plr) }

for (cabbage in listOf(BlackKnightsFortress.CABBAGE, BlackKnightsFortress.DRAYNOR_CABBAGE)) {
    useItem(cabbage).onObject(BlackKnightsFortress.HOLE) { BlackKnightsFortress.sabotage(plr, cabbage) }
}
