package engine.combat.status

import com.google.common.collect.ImmutableList
import engine.combat.status.hooks.PoisonStatusEffect
import engine.combat.status.hooks.TeleBlockStatusEffect
import game.combat.specialAttacks.DragonScimitar
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player

/**
 * Registered combat status effect types.
 *
 * Each type can optionally define a [loadFunction] used to recreate a placeholder effect during player login. The
 * effect's saved data is loaded afterward, allowing constructors to use dummy values such as `Duration.ZERO`.
 *
 * Status effects without a [loadFunction] are not restored from saved player data.
 *
 * @property loadFunction Creates a placeholder status effect for a player during save restoration.
 * @author lare96
 */
enum class StatusEffectType(val loadFunction: (Player) -> StatusEffect<out Mob>? = { null }) {

    /**
     * Poison damage over time.
     */
    POISONED({ PoisonStatusEffect(it) }),

    /**
     * Prevents movement for a fixed duration.
     */
    IMMOBILIZED,

    /**
     * Temporarily disables actions.
     */
    STUNNED,

    /**
     * Prevents teleporting for a fixed duration.
     */
    TELEBLOCK({ TeleBlockStatusEffect(it) }),

    /**
     * Protects against poison damage while active.
     */
    ANTIPOISON({ PotionStatusEffect(it, ANTIPOISON) }),

    /**
     * Protects against dragonfire-related damage checks while active.
     */
    ANTIFIRE({ PotionStatusEffect(it, ANTIFIRE) }),

    /**
     * Marks the player as prayer-disabled from a dragon scimitar special attack.
     */
    DRAGON_SCIMITAR({ DragonScimitar.DragonScimitarStatusEffect(it) });

    companion object {

        /**
         * Shared immutable list of all status effect types.
         */
        val ALL = ImmutableList.copyOf(values())
    }
}