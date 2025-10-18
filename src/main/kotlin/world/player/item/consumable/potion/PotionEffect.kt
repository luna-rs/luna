package world.player.item.consumable.potion

import api.attr.Attr
import io.luna.game.model.mob.Player

/**
 * Represents special potion effects that apply timed benefits to a player.
 *
 * Each effect is represented by a [PotionEffect] enum constant, and active potion effects are tracked in a [HashMap]
 * attached to the player using the attribute system. Each entry maps a [PotionEffect] to its
 * [PotionCountdownTimer], allowing effects to expire automatically after a set duration.
 *
 * @author lare96
 */
enum class PotionEffect {

    /**
     * Prevents poison damage.
     */
    ANTIPOISON,

    /**
     * Reduces dragonfire damage.
     */
    ANTIFIRE;

    companion object {

        /**
         * An attribute storing all currently active potion effects for a player.
         */
        val Player.potionEffects: HashMap<PotionEffect, PotionCountdownTimer> by Attr.map()

        /**
         * Returns whether the player currently has an antipoison effect active.
         */
        fun Player.hasAntiPoison(): Boolean {
            return potionEffects.containsKey(ANTIPOISON)
        }

        /**
         * Returns whether the player currently has an antifire effect active.
         */
        fun Player.hasAntiFire(): Boolean {
            return potionEffects.containsKey(ANTIFIRE)
        }
    }
}
