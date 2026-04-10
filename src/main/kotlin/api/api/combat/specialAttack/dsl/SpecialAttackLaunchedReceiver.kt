package api.combat.specialAttack.dsl

import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.attack.CombatAttack

/**
 * Receiver passed to special attack launch callbacks.
 *
 * This context is created once the special attack has successfully launched, allowing scripts to react immediately
 * before the hit arrives.
 *
 * For activation-only specials, [attack] will be `null` because no concrete combat attack instance is created.
 *
 * @property attacker The player who launched the special attack.
 * @property victim The mob targeted by the special attack.
 * @property attack The launched combat attack instance, or `null` for activation-only specials.
 * @author lare96
 */
class SpecialAttackLaunchedReceiver(
    val attacker: Player,
    val victim: Mob,
    val attack: CombatAttack<Player>?
)