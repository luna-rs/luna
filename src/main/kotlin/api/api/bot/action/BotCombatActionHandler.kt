package api.bot.action

import api.bot.Suspendable.waitFor
import api.bot.zone.SubZone
import api.predef.*
import engine.controllers.Controllers.inWilderness
import engine.controllers.WildernessLocatableController.wildernessLevel
import game.bot.scripts.combat.PkBotScript.Companion.LOW_LEVEL_ANCHOR_POINTS
import game.skill.magic.Magic
import game.skill.magic.Staff
import io.luna.game.model.mob.Skill
import io.luna.game.model.def.CombatStyleDefinition
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.Weapon
import io.luna.game.model.mob.movement.NavigationResult
import kotlinx.coroutines.future.await
import kotlin.time.Duration.Companion.seconds

/**
 * Handles combat-related bot actions that are not part of the core combat engine itself.
 *
 * @author lare96
 */
class BotCombatActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    /**
     * Shared account-build policy for combat and incidental experience choices.
     *
     * @author lare96
     */
    companion object {
        /**
         * Determines whether training [skill] still advances this bot's permanent account build.
         * Non-combat skills are unrestricted; skiller templates reject every combat skill.
         * Static levels avoid temporary boosts or drains changing the decision.
         */
        @JvmStatic
        fun wantsToTrain(bot: Bot, skill: Int): Boolean {
            if (!Skill.isCombatSkill(skill)) return true
            if (bot.preferences.isSkiller) return false
            val target = bot.preferences.pk?.build()?.targetLevel(skill) ?: 99
            return bot.skill(skill).staticLevel < target
        }

        /**
         * Returns the combat skills still below their desired training levels.
         */
        @JvmStatic
        fun wantedCombatSkills(bot: Bot): Set<Int> = (0..6).filter { wantsToTrain(bot, it) }.toSet()

        /**
         * Allows incidental experience in unrestricted skills, including skills already at 99.
         * A deliberately capped skill must still be below its target. This selects actions;
         * it never removes native experience or resets levels.
         */
        @JvmStatic
        fun mayGainExperience(bot: Bot, skill: Int): Boolean {
            if (!Skill.isCombatSkill(skill)) return true
            if (bot.preferences.isSkiller) return false
            val target = bot.preferences.pk?.build()?.targetLevel(skill) ?: 99
            return target == 99 || bot.skill(skill).staticLevel < target
        }

        /**
         * Filters every XP-bearing skill, so controlled and defensive ranged modes cannot train a capped Defence.
         */
        @JvmStatic
        fun allowedStyles(bot: Bot, styles: Iterable<CombatStyleDefinition>): List<CombatStyleDefinition> =
            styles.filter { style -> style.exp.isNotEmpty() && style.exp.all { mayGainExperience(bot, it) } }
    }

    /**
     * Returns this bot's desired combat training skills for coordinators and content scripts.
     */
    fun wantedCombatSkills(): Set<Int> = wantedCombatSkills(bot)

    /**
     * Checks a training choice against the saved account build and skiller restriction.
     */
    fun wantsToTrain(skill: Int): Boolean = wantsToTrain(bot, skill)

    /**
     * Revalidates the equipped weapon after swaps or level changes, keeping a safe current style when possible.
     * Returns false when no style can respect the build; callers must stop attacking in that case.
     */
    fun prepareTrainingStyle(): Boolean {
        if (bot.preferences.pk == null && !bot.preferences.isSkiller) return true
        if (bot.combat.magic.isAutocasting || bot.combat.magic.selectedSpell != CombatSpellDefinition.NONE) {
            return mayGainExperience(bot, Skill.MAGIC)
        }
        val styles = allowedStyles(bot, bot.combat.weapon.typeDef.styles)
        if (styles.isEmpty()) return false
        val selected = styles.firstOrNull { it == bot.combat.weapon.styleDef } ?: styles.first()
        if (selected != bot.combat.weapon.styleDef) bot.combat.weapon.changeStyle(selected.button)
        return true
    }

    /**
     * Determines whether the given mob is considered an extreme combat threat to this bot.
     *
     * A mob is treated as threatening when its combat level is more than double the bot's combat level. This is a
     * simple fear check meant for obvious danger cases, such as a low-level bot encountering a much stronger enemy.
     *
     * @param mob The mob being evaluated.
     *
     * @return `true` if [mob] is more than twice this bot's combat level.
     */
    fun isThreat(mob: Mob)

            : Boolean {
        // TODO@.5.0 Expand by checking mob type, if it's a boss, equipment, skills, etc.
        return mob.combatLevel > bot.combatLevel * 2
    }

    /**
     * Configures [spell] as the bot's active autocast spell.
     *
     * The bot must be using the correct spellbook, wielding a valid staff, and currently meet all spell requirements.
     *
     * @return `true` if autocasting was configured successfully.
     */
    fun setAutocastSpell(spell: CombatSpellDefinition): Boolean {
        if (spell == CombatSpellDefinition.NONE || spell.spellbook != bot.spellbook) {
            return false
        }

        val weaponId = bot.equipment.weapon?.id ?: return false
        if (bot.combat.weapon.type != Weapon.STAFF) {
            return false
        }

        if (bot.spellbook == Spellbook.ANCIENT && weaponId !in Staff.AUTOCAST_ANCIENTS) {
            return false
        }

        if (Magic.checkRequirements(bot, spell, true) == null) {
            return false
        }

        val magic = bot.combat.magic
        magic.selectedSpell = CombatSpellDefinition.NONE
        magic.autocastSpell = spell
        magic.isAutocasting = true
        magic.refreshAutocast()
        return true
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
        bot.walking.isRunning = true

        if (bot.inWilderness()) {
            bot.isWandering = false
            bot.combat.isDisabled = true
            try {
                if (bot.wildernessLevel < 20) {
                    bot.output.sendCommand("home")
                    bot.combat.isDisabled = false
                    val success = waitFor(10.seconds) { SubZone.HOME in bot.subZones }
                    if (success) {
                        return true
                    }
                }

                val subZone = bot.subZones.firstOrNull()
                val outside = subZone?.outside?.invoke(bot)
                val parent = subZone?.parent?.invoke(bot)
                if (outside != null && parent != null) {
                    subZone.leave(bot, parent, outside)
                }

                // TODO@0.5.0 Fall back to reverse-pursuit action previously mentioned?
                if (SubZone.HOME in bot.subZones ||
                    bot.navigator.navigate(LOW_LEVEL_ANCHOR_POINTS.random(), true)
                        .await() == NavigationResult.REACHED
                ) {
                    bot.output.sendCommand("home")
                    return waitFor(10.seconds) { SubZone.HOME in bot.subZones }
                }
            } finally {
                bot.combat.isDisabled = false
            }
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
        bot.walking.isRunning = true

        if (bot.inWilderness()) {
            handler.combat.fleeWilderness()
        } else if (bot.healthPercent < 15) {
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