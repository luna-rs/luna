package api.combat.player

import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType

class VoidCombatAttack(attacker: Player, victim: Mob) : CombatAttack<Player>(attacker,
                                                                          victim,
                                                                          InteractionPolicy(InteractionType.LINE_OF_SIGHT,
                                                                                         Position.VIEWING_DISTANCE),
                                                                          1) {
    override fun attack() { // todo documentation
        attacker.combat.target = null
    }

    override fun calculateDamage(other: Mob?): CombatDamage? = null
}