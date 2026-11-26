package game.content.championsChallenge

import api.combat.death.DeathHookHandler
import api.combat.player.PlayerCombatHandler.playerStopAttack
import api.predef.*
import io.luna.game.event.impl.LoginEvent

on(LoginEvent::class) { ChampionsChallenge.login(plr) }

npc1(ChampionsChallenge.LARXUS) { ChampionsChallenge.talk(plr) }
for (champion in Champion.LOWER) {
    item1(champion.scroll) { ChampionsChallenge.read(plr, champion) }
    useItem(champion.scroll).onNpc(ChampionsChallenge.LARXUS) { ChampionsChallenge.handIn(plr, champion) }
}
object1(ChampionsChallenge.PORTCULLIS) { ChampionsChallenge.portcullis(plr) }

playerStopAttack { attack -> ChampionsChallenge.stopAttack(this, attack) }

for (champion in Champion.entries) {
    DeathHookHandler.addNpcHook(champion.npc) {
        DeathHookHandler.defaultNpcHook?.invoke(this)
        death { ChampionsChallenge.defeated(victim, source) }
    }
}
