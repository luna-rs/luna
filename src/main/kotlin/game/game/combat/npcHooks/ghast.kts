package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import api.predef.ext.*
import game.skill.cooking.cookFood.Food
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.util.RandomUtils
import io.luna.util.Rational

/**
 * The chance that a piece of food will be spoiled and bonus damage applied.
 */
val ROT_FOOD_CHANCE = 3 of 10

/**
 * The rotten food item.
 */
val ROTTEN_FOOD = Item(2959)

/**
 * The bonus damage range.
 */
val BONUS_DAMAGE_RANGE = 1..5

combat(1053, 3609, 3610, 3611) {
    attack {
        melee {
            if (rand(ROT_FOOD_CHANCE)) {
                // Not 100% faithful to old rs, but good enough?
                other.damage(rand(BONUS_DAMAGE_RANGE))
                if (other is Player) {
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