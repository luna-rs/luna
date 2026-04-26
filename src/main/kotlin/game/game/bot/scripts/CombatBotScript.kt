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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Handles active combat behaviour for a bot.
 *
 * This script is pushed when a bot has a specific combat focus. It keeps the bot attacking that focus, eats food,
 * drinks useful potions, activates prayers, attempts special attacks, and loots nearby valuable items when the target
 * is no longer valid.
 *
 * @param bot The bot controlled by this combat script.
 * @param focus The current combat target this bot should prioritize.
 */
class CombatBotScript(bot: Bot, private var focus: Mob) : DynamicBotScript(bot) {

    /**
     * Tracks whether this bot has already failed to find food.
     *
     * TODO@0.5.0 This is currently unused. Use it to avoid repeatedly scanning the inventory every low-health cycle.
     */
    private var noFood = false

    /**
     * Cached special attack weapon id.
     *
     * A value of `null` means the script has not checked yet. A value of `-1` means the check already happened and no
     * special attack weapon was found.
     */
    private var specialWeapon: Int? = null

    /**
     * The weapon equipped before swapping to a special attack weapon.
     *
     * TODO@0.5.0 This is currently never assigned before the special weapon swap, so the script cannot reliably switch
     *  back to the previous weapon after special attack energy is too low.
     */
    private var previousWeapon: Int? = null

    /**
     * The shield equipped before swapping to a two-handed special attack weapon.
     *
     * TODO@0.5.0 This is currently never assigned before the special weapon swap, so shield restoration will not work.
     */
    private var previousShield: Int? = null

    override suspend fun init() {
        bot.combat.attack(focus)
        delay(600.milliseconds)
    }

    override suspend fun run(): Boolean {
        targetEnemy()
        delay(600.milliseconds, 1200.milliseconds)

        if (!bot.combat.inCombat()) {
            return true
        }

        eatFood()
        drinkPotion()
        activatePrayer()
        useSpecialAttack()

        delay(600.milliseconds, 1200.milliseconds)
        return false
    }

    /**
     * Attacks the current focus if it is still valid.
     *
     * In PvP, the bot has a small chance to switch to a nearby low-health player in multi-combat. If the current focus
     * is dead, unreachable, out of view, or no longer valid for multi-combat, the bot looks for nearby loot instead.
     */
    private suspend fun targetEnemy() {
        if (focus.isAlive && focus.isViewableFrom(bot) && bot.combat.checkMultiCombat(focus)) {
            if (focus is Player && rand(1 of 5)) {
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

        lootItems()
    }

    /**
     * Eats food when the bot is low on health.
     *
     * If no usable food is found, the bot attempts to flee combat. After eating or fleeing, the bot refreshes its
     * target so it can continue fighting if still safe enough to do so.
     */
    private suspend fun eatFood() {
        if (bot.healthPercent < 30) {
            if (!handler.combat.eatFood()) {
                // TODO@1.0 Fleeing should depend on bot intelligence, enemy health, combat level, risk, gear value,
                //  Wilderness depth, and available escape options.
                handler.combat.fleeCombat()
            }

            targetEnemy()
        }
    }

    /**
     * Drinks one useful potion for the bot's current combat state.
     *
     * The bot builds a set of acceptable potion doses based on unboosted stats and low prayer points, then drinks the
     * first matching potion found in its inventory.
     */
    private suspend fun drinkPotion() {
        // TODO@0.5.0 Incorporate Saradomin brews, super restores, anti-poison, anti-dragonfire, and other potion types.
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
                handler.inventory.clickItem(1, item.id, index)
                targetEnemy()
                return
            }
        }
    }

    /**
     * Attempts to use the bot's special attack weapon.
     *
     * The special weapon is resolved once and cached. If the bot is not already wielding that weapon, it attempts to
     * equip it from inventory. Once equipped, the special attack bar is toggled on if enough energy is available.
     */
    private suspend fun useSpecialAttack() {
        val weaponId = bot.equipment.weapon?.id

        if (specialWeapon == null) {
            specialWeapon = -1

            if (weaponId in SpecialAttackHandler.getAllWeaponIds()) {
                specialWeapon = weaponId
            }

            for (item in bot.inventory) {
                if (item != null && item.id in SpecialAttackHandler.getAllWeaponIds()) {
                    specialWeapon = item.id
                    break
                }
            }
        }

        if (specialWeapon == -1 || specialWeapon == null) {
            return
        }

        if (weaponId != specialWeapon) {
            if (bot.inventory.isFull && equipDef(weaponId!!).isTwoHanded) {
                return
            }

            if (!handler.equipment.equip(specialWeapon!!).await()) {
                return
            }

            if (bot.combat.weapon.specialAttackType == null) {
                return
            }
        }

        if (bot.combat.specialBar.energy >= bot.combat.specialAttackData().drain) {
            bot.combat.specialBar.toggleOn()
        } else {
            if (previousShield != null && handler.equipment.equip(previousShield!!).await()) {
                previousShield = null
            }

            if (previousWeapon != null && handler.equipment.equip(previousWeapon!!).await()) {
                previousWeapon = null
            }
        }
    }

    /**
     * Activates defensive and offensive prayers based on the current combat situation.
     *
     * The bot mirrors its protection prayer against the last received damage type. It also keeps low-cost defensive
     * prayers active and uses melee offensive prayers when not ranging or autocasting.
     */
    private fun activatePrayer() {
        if (bot.prayer.level > 0) {
            // TODO@0.5.0 Intelligent bots should use Smite, Retribution, and Redemption strategically.
            val prayerPercent = bot.prayer.level.toDouble() / bot.prayer.staticLevel.toDouble()

            if (prayerPercent < 0.15 && bot.combat.prayers.active.entrySet().size > 2) {
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
                bot.combat.prayers.forceActivate(protectionPrayer)
            } else {
                bot.combat.prayers.deactivate(CombatPrayer.PROTECT_FROM_MELEE, true)
                bot.combat.prayers.deactivate(CombatPrayer.PROTECT_FROM_MISSILES, true)
                bot.combat.prayers.deactivate(CombatPrayer.PROTECT_FROM_MAGIC, true)
            }

            bot.combat.prayers.forceActivate(CombatPrayer.STEEL_SKIN)
            bot.combat.prayers.forceActivate(CombatPrayer.PROTECT_ITEM)

            if (!bot.combat.weapon.isRanged && !bot.combat.magic.isAutocasting) {
                bot.combat.prayers.forceActivate(CombatPrayer.ULTIMATE_STRENGTH)
                bot.combat.prayers.forceActivate(CombatPrayer.INCREDIBLE_REFLEXES)
            }
        }
    }

    /**
     * Loots nearby food, potions, coins, and valuable items.
     *
     * Looting currently uses simple filters and stops once inventory space runs out or an item interaction fails.
     */
    private suspend fun lootItems() {
        // TODO@1.0 Move looting into its own dynamic system. What a bot wants to loot should depend on its current
        //  intent. PK bots may prefer food and valuables, while PvM bots may prefer stackables, resources, and alchables.
        // TODO@0.5.0 Sort loot by value so the most expensive items are picked up first.
        // TODO@0.5.0 Account for remaining inventory space when choosing between stackables, food, potions, and gear.
        val local = world.locator.findViewableItems(bot) {
            it.isWithinDistance(bot, 10) &&
                    (Food.ID_TO_FOOD.containsKey(it.id) ||
                            isValuable(it.id) ||
                            Potion.DOSE_TO_POTION.containsKey(it.id) ||
                            it.def().value > 5000)
        }

        var remainingSlots = bot.inventory.computeRemainingSize()

        for (groundItem in local) {
            val item = groundItem.toItem()

            if (bot.inventory.hasSpaceFor(item)) {
                if (!handler.interactions.interact(1, groundItem)) {
                    return
                }

                if (--remainingSlots < 1) {
                    break
                }
            }
        }
    }

    /**
     * Checks whether an item should always be considered valuable by this combat script.
     *
     * This is currently a small hardcoded filter. More advanced bot logic should eventually use economy data,
     * popularity, intrinsic value, fashion demand, resource scarcity, and the bot's current goals.
     *
     * @param id The item id to check.
     * @return `true` if the item should be treated as valuable, otherwise `false`.
     */
    private fun isValuable(id: Int): Boolean {
        // TODO@1.0 Move item valuation into a dynamic economy-aware system.
        return when (id) {
            995 -> true
            else -> false
        }
    }
}