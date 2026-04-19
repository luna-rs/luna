package game.bot.scripts

import api.bot.DynamicBotScript
import api.bot.Suspendable.delay
import api.combat.specialAttack.SpecialAttackHandler
import api.combat.specialAttack.SpecialAttackHandler.specialAttackData
import api.predef.*
import api.predef.ext.*
import engine.combat.prayer.CombatPrayer
import engine.controllers.Controllers.inWilderness
import game.player.item.consume.food.Food
import game.player.item.consume.potion.Potion
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.damage.CombatDamageType
import kotlin.math.floor
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CombatBotScript(bot: Bot, private var focus: Mob) : DynamicBotScript(bot) {

    private var noFood = false
    private var specialWeapon: Int? = null
    private var previousWeapon: Int? = null
    private var previousShield: Int? = null

    override suspend fun run(): Boolean {
        targetEnemy()

        if (!bot.combat.inCombat()) {
            // End script if bot isn't in combat.
            return true
        }
        eatFood()
        drinkPotion()
        activatePrayer()
        targetEnemy()
        useSpecialAttack()
        delay(600.milliseconds, 1200.milliseconds)
        return false
    }

    private suspend fun targetEnemy() {
        if (focus.isAlive && focus.isViewableFrom(bot) && bot.combat.checkMultiCombat(focus)) {
            if (focus is Player && rand(1 of 5)) {
                // If in PvP, random chance to change targets to low health ones in multi-combat areas.
                val local = world.locator.findViewablePlayers(bot) {
                    bot.combat.checkMultiCombat(it) &&
                            it.healthPercent < 25 &&
                            it.isWithinDistance(bot, 10)
                }
                if (local.size > 0) {
                    focus = local.first()
                }
            }
            bot.combat.attack(focus)
            delay(1200.milliseconds, 3.seconds)
            return
        }
        // Target either got away or is dead, either way look for loot nearby just in case.
        lootItems()
    }

    private suspend fun eatFood() {
        if (bot.healthPercent < 30) {
            if (noFood) {
                // TODO Only intelligent bots flee.
                if (bot.inWilderness()) {
                    handler.combat.fleeWilderness()
                } else {
                    handler.combat.fleeCombat()
                }
            } else {
                var clicked = false
                for ((index, item) in bot.inventory.withIndex()) {
                    if (item != null && Food.ID_TO_FOOD.containsKey(item.id)) {
                        handler.inventory.clickItem(1, item.id, index)
                        clicked = true
                    }
                }
                if (!clicked) {
                    noFood = true
                }
            }
        }
    }

    private fun drinkPotion() {
        // TODO Saradomin brew, other types of potions.
        val potions = HashSet<Int>()

        if (!bot.attack.isBoosted) {
            potions += Potion.ATTACK_POTION.doses
            potions += Potion.SUPER_ATTACK.doses
        }

        if (!bot.defence.isBoosted) {
            potions += Potion.DEFENCE_POTION.doses
            potions += Potion.SUPER_DEFENCE.doses
        }

        if (!bot.strength.isBoosted) {
            potions += Potion.STRENGTH_POTION.doses
            potions += Potion.SUPER_ATTACK.doses
        }

        if (!bot.ranged.isBoosted) {
            potions += Potion.RANGING_POTION.doses
            potions += Potion.SUPER_ATTACK.doses
        }

        if (!bot.magic.isBoosted) {
            potions += Potion.MAGIC_POTION.doses
        }

        if (bot.prayer.level < 30) {
            potions += Potion.PRAYER_POTION.doses
        }

        for ((index, item) in bot.inventory.withIndex()) {
            if (item != null && item.id in potions) {
                // Drink one potion per combat cycle.
                handler.inventory.clickItem(1, item.id, index)
                return
            }
        }
    }

    private suspend fun useSpecialAttack() {
        // Resolve special attack weapon.
        val weaponId = bot.equipment.weapon?.id
        if (specialWeapon == null) {
            specialWeapon = -1
            if (weaponId in SpecialAttackHandler.getAllWeaponIds()) {
                specialWeapon = weaponId
            }
            for (item in bot.inventory) {
                if (item != null && item.id in SpecialAttackHandler.getAllWeaponIds()) {
                    specialWeapon = weaponId
                    break
                }
            }
        }
        if (specialWeapon == -1 || specialWeapon == null) {
            // Special attack weapon was already checked, we don't have one.
            return
        }
        if (weaponId != specialWeapon) {
            if (bot.inventory.isFull && equipDef(weaponId!!).isTwoHanded) {
                // Don't have enough inventory space to swap to a two-handed weapon.
                return
            }
            if (!handler.equipment.equip(specialWeapon!!).await()) {
                // Could not equip item.
                return
            }
            if (bot.combat.weapon.specialAttackType != null) {
                // Special attack type wasn't resolved.
                return
            }
        }
        if (bot.combat.specialBar.energy >= bot.combat.specialAttackData().drain) {
            // Toggle on special bar.
            bot.combat.specialBar.toggleOn()
        } else {
            // Re-equip previous weapon and shield.
            if (previousShield != null && handler.equipment.equip(previousShield!!).await()) {
                previousShield = null
            }
            if (previousWeapon != null && handler.equipment.equip(previousWeapon!!).await()) {
                previousWeapon = null
            }
        }
    }

    private fun activatePrayer() {
        if (bot.prayer.level > 0) {
            // TODO Intelligent bots use smite, retribution, and redemption strategically.
            val prayerPercent = floor(bot.prayer.level.toDouble() / bot.prayer.staticLevel.toDouble())
            if (prayerPercent < 0.15 && bot.combat.prayers.active.entrySet().size > 2) {
                // Deactivate all but two low-cost prayers when prayer points fall below 15%
                bot.combat.prayers.deactivateAll()
                bot.combat.prayers.forceActivate(CombatPrayer.STEEL_SKIN)
                bot.combat.prayers.forceActivate(CombatPrayer.PROTECT_ITEM)
                return
            }

            val protectionPrayer =
                when (bot.combat.lastDamageReceived?.type) {
                    CombatDamageType.MELEE -> CombatPrayer.PROTECT_FROM_MELEE
                    CombatDamageType.RANGED -> CombatPrayer.PROTECT_FROM_MISSILES
                    CombatDamageType.MAGIC -> CombatPrayer.PROTECT_FROM_MAGIC
                    else -> null
                }
            if (protectionPrayer != null) {
                // Use protection prayer corresponding to last damage type.
                bot.combat.prayers.forceActivate(protectionPrayer)
            } else {
                // Couldn't resolve a last damage type? Deactivate all protection prayers.
                bot.combat.prayers.deactivate(CombatPrayer.PROTECT_FROM_MELEE, true)
                bot.combat.prayers.deactivate(CombatPrayer.PROTECT_FROM_MISSILES, true)
                bot.combat.prayers.deactivate(CombatPrayer.PROTECT_FROM_MAGIC, true)
            }

            // Always keep these main two low-cost prayers active.
            bot.combat.prayers.forceActivate(CombatPrayer.STEEL_SKIN)
            bot.combat.prayers.forceActivate(CombatPrayer.PROTECT_ITEM)
            if (!bot.combat.weapon.isRanged && !bot.combat.magic.isAutocasting) {
                // Use melee prayers if we're not ranging.
                bot.combat.prayers.forceActivate(CombatPrayer.ULTIMATE_STRENGTH)
                bot.combat.prayers.forceActivate(CombatPrayer.INCREDIBLE_REFLEXES)
            }
        }
    }

    private suspend fun lootItems() {
        // TODO This should also be its own dynamic system. Because a lot of what you want to loot depends on your
        //      current intentions (Pking: maybe a little food, mostly valuables, PvM: almost everything stackable or of value,
        //      but should still change depending on inventory space)
        // TODO Sort by value, pickup most expensive first.
        // TODO Value also depends on available inventory space. Different filters for different space.
        val local = world.locator.findViewableItems(bot) {
            it.isWithinDistance(bot, 10) &&
                    (Food.ID_TO_FOOD.containsKey(it.id) || isValuable(it.id) || Potion.DOSE_TO_POTION.containsKey(it.id) ||
                         it.def().value > 5000)
        }
        var remainingSlots = bot.inventory.computeRemainingSize()
        for(groundItem in local) {
            val item = groundItem.toItem()
            if(bot.inventory.hasSpaceFor(item)) {
                if(!handler.interactions.interact(1, groundItem)) {
                    // Could not loot item, stop here.
                    return
                }
                if(--remainingSlots < 1) {
                    // We don't have enough inventory space to keep looting.
                    break
                }
            }
        }
    }

    private fun isValuable(id: Int): Boolean {
        // TODO In the future, this will become its own dynamic system with bots changing what they find valuable
        //  depending on game resources, popularity, intrinsic value, fashion, etc.
        return when(id) {
            995 -> true
            else -> false
        }
    }
}