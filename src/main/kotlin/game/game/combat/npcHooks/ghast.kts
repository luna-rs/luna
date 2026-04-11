package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import game.skill.cooking.cookFood.Food
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.util.RandomUtils
import io.luna.util.Rational

/**
 * The chance that a piece of food will be spoiled and bonus damage applied.
 */
val ROT_FOOD_CHANCE = Rational(3, 10)

/**
 * The rotten food item.
 */
val ROTTEN_FOOD = Item(2959)

/**
 * The bonus damage range.
 */
val BONUS_DAMAGE_RANGE = 1..5

// A small chance to deal extra damage and spoil food upon attack.
combat(1053, 3609, 3610, 3611) {
    // Determine how the ghast will attack.
    attack {
        // Use a melee attack.
        melee {
            // Right when the attack launches, try to spoil some enemy food and deal bonus damage.
            if (RandomUtils.roll(ROT_FOOD_CHANCE)) {
                // Not 100% faithful to old rs, but good enough?
                other.damage(rand(BONUS_DAMAGE_RANGE))
                if (other is Player) {
                    println(other)
                    for ((index, item) in other.inventory.withIndex()) {
                        if (item != null && Food.COOKED_TO_FOOD.containsKey(item.id)) {
                            other.inventory[index] = ROTTEN_FOOD
                            other.sendMessage("Some of your food goes bad!")
                            break
                        }
                    }
                }
            }
            it // Damage is unchanged.
        }
    }
}