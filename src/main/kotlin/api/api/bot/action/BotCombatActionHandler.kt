package api.bot.action

import api.bot.Suspendable
import engine.controllers.Controllers.inWilderness
import engine.controllers.WildernessLocatableController.wildernessLevel
import game.bot.scripts.PkBotScript.Companion.LOW_LEVEL_ANCHOR_POINTS
import game.player.item.consume.food.Food
import game.skill.magic.Magic
import io.luna.Luna
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.movement.NavigationResult
import kotlinx.coroutines.future.await
import kotlin.time.Duration.Companion.seconds

/**
 * Handles combat-related bot actions that are not part of the core combat engine itself.
 *
 * @author lare96
 */
class BotCombatActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    // TODO@0.5.0 More functions like get/equip(Strongest)Weapon, get/equip(Strongest)Armor, etc.

    /**
     * Attempts to eat a food item from the bot's inventory.
     *
     * If [id] is `-1`, the first available food item found in the inventory is eaten. If [id] is supplied, only that
     * specific food item will be used.
     *
     * @param id The food item id to eat, or `-1` to eat the first available food item.
     * @return `true` if a food item was found and clicked, otherwise `false`.
     */
    fun eatFood(id: Int = -1): Boolean {
        if (id == -1) {
            var matching: Pair<Int, Int>? = null

            for ((index, item) in bot.inventory.withIndex()) {
                if (item == null) {
                    continue
                }

                if (Food.ID_TO_FOOD.containsKey(item.id)) {
                    matching = index to item.id
                    break
                }
            }

            if (matching != null) {
                bot.output.sendInventoryItemClick(1, matching.first, matching.second)
                return true
            }
        } else if (Food.ID_TO_FOOD.containsKey(id)) {
            val index = bot.inventory.computeIndexForId(id)

            if (index != -1) {
                bot.output.sendInventoryItemClick(1, index, id)
                return true
            }
        }

        return false
    }

    /**
     * Attempts to escape from the Wilderness.
     *
     * Low-level Wilderness bots teleport home immediately. Higher-level Wilderness bots first attempt to navigate to
     * one of the configured low-level anchor points before teleporting home.
     *
     * Combat is temporarily disabled while the bot is fleeing so it does not keep re-engaging targets during escape.
     *
     * @return `true` if the bot is no longer in the Wilderness or successfully reached home, otherwise `false`.
     */
    suspend fun fleeWilderness(): Boolean {
        // TODO@1.0 Bots need to support zones and area recognition. Specialized cases such as KBD lair, Mage Arena,
        //  resource area, and Wilderness agility should not blindly path to generic Wilderness anchors.
        handler.widgets.clickRunning(true)

        if (bot.inWilderness()) {
            bot.isWandering = false
            bot.combat.isDisabled = true

            val home = Luna.settings().game().startingPosition()

            if (bot.wildernessLevel < 20 ||
                bot.navigator.navigate(LOW_LEVEL_ANCHOR_POINTS.random(), true).await() == NavigationResult.REACHED) {
                bot.output.sendCommand("home")
                bot.combat.isDisabled = false

                return Suspendable.waitFor(10.seconds) {
                    bot.isViewableFrom(home)
                }
            }
            // TODO@0.5.0 Fall back to reverse-pursuit action previously mentioned.
            bot.combat.isDisabled = false
            return false
        }

        return true
    }

    /**
     * Attempts to flee from the bot's current combat situation.
     *
     * Wilderness combat uses the dedicated Wilderness escape behaviour. Non-Wilderness combat either teleports home
     * when critically low on health, or falls back to future reverse-pursuit movement logic.
     */
    suspend fun fleeCombat() {
        handler.widgets.clickRunning(true)

        if (bot.inWilderness()) {
            handler.combat.fleeWilderness()
        } else if (bot.healthPercent < 15) {
            // TODO@1.0 Only smart bots should always flee using ::home. Less intelligent bots should sometimes
            //  panic-run, misclick, hesitate, or continue fighting too long.
            bot.output.sendCommand("home")
        } else {
            // TODO@0.5.0 Add a reverse-pursuit action for bots. First check nearby tiles in the opposite direction
            //  of the threat. For example, if the attacker is east of the bot, prefer west, north-west, and
            //  south-west tiles. If none are pathable, widen the search to lateral directions, then finally allow
            //  less safe directions if the bot is boxed in.
        }
    }

    /**
     * Selects a combat spell for the bot if the bot meets the spell requirements.
     *
     * This only selects the spell on the bot's combat magic state. It does not cast the spell by itself.
     *
     * @param spell The combat spell to select.
     * @return `true` if the spell was selected, otherwise `false`.
     */
    fun selectAndUseSpell(spell: CombatSpell): Boolean {
        if (Magic.checkRequirements(bot, spell.def, false) != null) {
            bot.combat.magic.selectedSpell = spell.def
            return true
        }
        return false
    }
}