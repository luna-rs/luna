package api.bot.action

import engine.bot.gear.BotGearPurpose
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.Player
import io.luna.util.RandomUtils
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.math.roundToInt
import api.bot.Suspendable.waitFor
import api.bot.zone.Zone
import api.bot.webwalk.plan.BankAccess
import api.combat.death.PlayerDeathItems
import io.luna.Luna
import api.predef.*
import engine.controllers.Controllers.inWilderness
import engine.controllers.WildernessLocatableController.levelAt
import game.bot.scripts.combat.PkBotScript
import game.skill.magic.Magic
import game.skill.magic.Staff
import io.luna.game.model.mob.SkillSet
import io.luna.game.model.mob.Skill
import io.luna.game.model.def.CombatStyleDefinition
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.EntityState
import io.luna.game.model.Direction
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.mob.SkullIcon
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.state.CombatContext.COMBAT_TIMER_DURATION
import io.luna.game.model.mob.combat.Weapon
import java.time.Instant
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
            if (bot.preferences.isSkiller || bot.preferences.scavenging.isIntroductory) return false
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
            if (bot.preferences.isSkiller || bot.preferences.scavenging.isIntroductory) return false
            val target = bot.preferences.pk?.build()?.targetLevel(skill) ?: 99
            return target == 99 || bot.skill(skill).staticLevel < target
        }

        /**
         * Limits one native XP award to the remaining experience needed for a permanent build target.
         * Called by Skill.addExperience after multiplier selection, including for delayed attacks and rewards.
         * Capped skills stop exactly at their target's XP threshold. Existing excess XP is never removed;
         * level-99 targets and non-combat skills retain normal experience gains beyond the level threshold.
         * Skiller templates receive no combat XP, including incidental Hitpoints XP.
         */
        @JvmStatic
        fun limitExperience(bot: Bot, skill: Skill, amount: Double): Double {
            if (!Skill.isCombatSkill(skill.id)) return amount
            if (bot.preferences.isSkiller || bot.preferences.scavenging.isIntroductory) return 0.0
            val target = bot.preferences.pk?.build()?.targetLevel(skill.id) ?: return amount
            if (target == 99) return amount
            val remaining = (SkillSet.experienceForLevel(target) - skill.experience).coerceAtLeast(0.0)
            return amount.coerceAtMost(remaining)
        }

        /**
         * Filters every XP-bearing skill, so controlled and defensive ranged modes cannot train a capped Defence.
         */
        @JvmStatic
        fun allowedStyles(bot: Bot, styles: Iterable<CombatStyleDefinition>): List<CombatStyleDefinition> =
            styles.filter { style -> style.exp.isNotEmpty() && style.exp.all { mayGainExperience(bot, it) } }

        /**
         * Recognizes bots by their PKER template or active PK session, including a paused parent beneath combat.
         * A saved build alone does not imply current PK activity, and a bot's skull is not a substitute for intent.
         * Human players are recognized by their native skull icon.
         */
        @JvmStatic
        fun isPker(player: Player): Boolean = if (player is Bot) {
            !player.preferences.isSkiller && (player.personality.type == PersonalityTemplateType.PKER ||
                player.scriptStack.containsActive(PkBotScript::class.java))
        } else player.skullIcon != SkullIcon.NONE

        /**
         * Delivers a fresh native PvP attack to nearby protection and clan observers before combat timers reset.
         * Existing PvP cannot establish who started a fight. Prior NPC combat does not hide a new player aggressor;
         * NPC attacks themselves do not call this hook.
         */
        @JvmStatic
        fun observePvpAttack(attacker: Player, victim: Player) {
            if (attacker === victim ||
                (attacker.combat.inCombat() && attacker.combat.lastCombatWith !is Npc) ||
                (victim.combat.inCombat() && victim.combat.lastCombatWith !is Npc)) return
            for (player in attacker.world.locator.findViewablePlayers(attacker)) {
                val observer = player as? Bot ?: continue
                observer.scriptStack.findActive(PkBotScript::class.java).orElse(null)?.observeAttack(attacker, victim)
            }
        }

        /**
         * Samples protection and clan sessions each game cycle, including while patrol travel is suspended.
         * All GENERIC personalities may observe fleeing; kind and clan sessions can protect witnessed victims.
         */
        @JvmStatic
        fun observePvpMovement(bot: Bot) {
            val session = bot.scriptStack.findActive(PkBotScript::class.java).orElse(null) ?: return
            if (!session.observesEncounters()) return
            session.observeMovement(bot.world.locator.findViewablePlayers(bot))
            session.protectObservedPlayer()
        }

        /**
         * How far from its attacker, in tiles, a running bot tries to get. An NPC stops chasing a target it can no
         * longer see.
         */
        private const val ESCAPE_DISTANCE = Position.VIEWING_DISTANCE + 3

        /**
         * How far, in tiles, a running bot looks for a place to run to.
         */
        private const val ESCAPE_SEARCH_RADIUS = 32
    }

    /**
     * Rechecks player combat eligibility before selection and after suspendable equipment actions.
     * Wilderness level difference uses the shallower endpoint, matching the Lost City PvP rule.
     * Positional levels avoid relying on a cached controller attribute during boundary movement.
     * Outside Wilderness, native controllers retain ownership of rules such as duelling.
     * This checks legality only; the PK purpose and personality decide whether to initiate.
     */
    fun canAttackPlayer(target: Player): Boolean {
        if (target === bot || bot.preferences.isSkiller || !bot.isAlive || !target.isAlive ||
            bot.state != EntityState.ACTIVE || target.state != EntityState.ACTIVE ||
            bot.position.z != target.position.z || !target.isViewableFrom(bot) ||
            bot.combat.isDisabled || !bot.combat.isAttackable || !target.combat.isAttackable) return false
        val attackerLevel = levelAt(bot.position)
        val targetLevel = levelAt(target.position)
        if (attackerLevel > 0 || targetLevel > 0) {
            if (attackerLevel == 0 || targetLevel == 0 ||
                kotlin.math.abs(bot.combatLevel - target.combatLevel) > minOf(attackerLevel, targetLevel)) return false
        }
        return bot.combat.checkMultiCombat(target) && bot.controllers.checkCombat(target)
    }

    /**
     * Restricts a PK patrol candidate to two players currently inside the Wilderness.
     * Defensive combat elsewhere can use [canAttackPlayer] under its native controller instead.
     */
    fun canAttackWildernessPlayer(target: Player): Boolean = levelAt(bot.position) > 0 &&
        levelAt(target.position) > 0 && canAttackPlayer(target)

    /**
     * Recognizes a current attack attempt against this bot or its latest received attack during active combat.
     * A received hit must match the current opponent and the native combat timeout. Old hits cannot authorize
     * retaliation just because unrelated combat has started later. A current incoming attempt remains sufficient.
     * Callers must independently enforce native player combat legality.
     */
    fun canRetaliateAgainst(target: Player, now: Instant = Instant.now()): Boolean {
        if (target.combat.target === bot) return true
        if (!bot.combat.inCombat() || bot.combat.lastCombatWith !== target) return false
        val received = bot.combat.lastAttackReceived ?: return false
        val timestamp = received.timestamp ?: return false
        return received.attacker === target &&
            java.time.Duration.between(timestamp, now).seconds in 0..COMBAT_TIMER_DURATION.toLong()
    }

    /**
     * Counts visible advantages for opportunistic GENERIC target preference, with one point per signal.
     * Lower health percentage, lower combat level, lower worn equipment tier and observed fleeing each add one.
     * Ties retain the patrol's existing ordering. This is a preference, not a win prediction or a legality bypass.
     */
    fun pkTargetVulnerability(target: Player, fleeing: Boolean): Int =
        listOf(target.healthPercent < bot.healthPercent, target.combatLevel < bot.combatLevel,
            wornCombatTier(target) < wornCombatTier(bot), fleeing).count { it }

    /**
     * Uses the same highest-equipment-requirement tier proxy as the gear selector, once per worn slot.
     * Unknown definitions contribute zero; bank stock, inventory, ammunition quantity and item prices are ignored.
     */
    private fun wornCombatTier(player: Player): Int = player.equipment.filterNotNull().sumOf {
        EquipmentDefinition.ALL.get(it.id).orElse(null)?.highestRequirement ?: 0
    }

    /**
     * Returns this bot's desired combat training skills for coordinators and content scripts.
     */
    fun wantedCombatSkills(): Set<Int> = wantedCombatSkills(bot)

    /**
     * Values current death loss against this bot's personality-based share of its owned wealth.
     * Bank preparation and patrol entry must recheck after changes to stock, skulls or active Protect Item.
     */
    fun pkRiskBudget(): PkRiskBudget = PkRiskBudget.assess(bot,
        PlayerDeathItems.losesItems(bot, Luna.settings().game().betaMode()))

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
     * Chooses an armour counter from supply-ready disciplines allowed by the saved PK identity.
     * Callers supply only disciplines whose owned equipment, ammunition and damage spells are usable.
     * This policy does not equip items, initiate fights, select targets, or authorize risk from bank stock.
     * Lowest armour defence wins; ties retain the current discipline to avoid pointless switching.
     * Intelligence and dexterity reduce the chance of choosing a weaker available counter.
     * Reaction timing is supplied separately by [pkSwitchDelay] for the future PK session controller.
     */
    fun selectPkStyle(target: Player, available: Set<BotGearPurpose>, mistakeRoll: Double = RandomUtils.nextDouble()): BotGearPurpose? {
        if (bot.preferences.isSkiller) return null
        val styles = bot.preferences.pk?.styles() ?: return null
        val options = listOf(BotGearPurpose.MELEE, BotGearPurpose.RANGED, BotGearPurpose.MAGIC).filter { purpose ->
            purpose in available && when (purpose) {
                BotGearPurpose.MELEE -> styles.uses(Skill.ATTACK) &&
                    listOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE).any { mayGainExperience(bot, it) }
                BotGearPurpose.RANGED -> styles.uses(Skill.RANGED) && mayGainExperience(bot, Skill.RANGED)
                BotGearPurpose.MAGIC -> styles.uses(Skill.MAGIC) && mayGainExperience(bot, Skill.MAGIC)
                else -> false
            }
        }
        if (options.isEmpty()) return null
        val current = when {
            bot.combat.magic.isAutocasting -> BotGearPurpose.MAGIC
            bot.combat.weapon.isRanged -> BotGearPurpose.RANGED
            else -> BotGearPurpose.MELEE
        }
        fun defence(purpose: BotGearPurpose): Int = when (purpose) {
            BotGearPurpose.MELEE -> listOf(EquipmentBonus.STAB_DEFENCE, EquipmentBonus.SLASH_DEFENCE,
                EquipmentBonus.CRUSH_DEFENCE).minOf { target.equipment.getBonus(it) }
            BotGearPurpose.RANGED -> target.equipment.getBonus(EquipmentBonus.RANGED_DEFENCE)
            else -> target.equipment.getBonus(EquipmentBonus.MAGIC_DEFENCE)
        }
        val best = options.minWith(compareBy<BotGearPurpose> { defence(it) }.thenBy { it != current })
        val intelligence = bot.personality.intelligence.coerceIn(0.0, 1.0)
        val dexterity = bot.personality.dexterity.coerceIn(0.0, 1.0)
        val errorChance = 0.30 * (1.0 - intelligence) * (1.0 - dexterity / 2.0)
        return if (mistakeRoll < errorChance) options.filter { it != best }.randomOrNull() ?: best else best
    }

    /**
     * Proposed decision cadence: one to six native 600-ms ticks, scaled by intelligence and dexterity.
     * The session controller must retain the current choice until this reaction delay has elapsed.
     */
    fun pkSwitchDelay(): Duration {
        val competence = (bot.personality.intelligence.coerceIn(0.0, 1.0) +
                bot.personality.dexterity.coerceIn(0.0, 1.0)) / 2.0
        return ((1 + ((1.0 - competence) * 5).roundToInt()) * 600).milliseconds
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
     * Queues one collision-checked retreat step only when no navigator request or walking path already owns movement.
     * Prefer distance from the threat's centre, trying lateral steps when a direct retreat is blocked. Never step
     * closer or outside the caller's permitted area; a boxed-in bot waits instead of clipping or replacing its path.
     */
    fun stepAwayFrom(threat: Mob, accepts: (Position) -> Boolean = { true }): Boolean {
        if (bot.state != EntityState.ACTIVE || !bot.isAlive || bot.isLocked || bot.walking.isLocked ||
            bot.navigator.isActive || !bot.walking.isEmpty || threat.state != EntityState.ACTIVE || !threat.isAlive ||
            bot.position.z != threat.position.z || !threat.isViewableFrom(bot)) return false
        val origin = bot.position
        val centreX = threat.position.x * 2L + threat.size() - 1
        val centreY = threat.position.y * 2L + threat.size() - 1
        fun distance(position: Position): Long {
            val x = position.x * 2L - centreX
            val y = position.y * 2L - centreY
            return x * x + y * y
        }
        val before = distance(origin)
        return Direction.ALL_EXCEPT_NONE.map { it to origin.translate(1, it) }
            .filter { (_, next) -> accepts(next) && distance(next) > before }
            .sortedByDescending { (_, next) -> distance(next) }
            .any { (direction, _) -> bot.navigator.step(direction) }
    }

    /**
     * Returns the mob the bot is fighting: the last mob it traded blows with, while that mob is alive and still
     * fighting the bot. Both sides are set when an attack is made, so a new attacker is named before its first hit
     * lands. The last attack received only changes when a hit lands, so until then it still names the previous
     * attacker. A respawned NPC is the same object on its next life, but no longer fighting the bot.
     *
     * @return The opponent, or `null` if there is none.
     */
    fun opponent(): Mob? = bot.combat.lastCombatWith?.takeIf {
        it.isAlive && (it.combat.lastCombatWith == bot || it.combat.target == bot)
    }

    /**
     * Starts the bot running from [threat], to the nearest tile at least [ESCAPE_DISTANCE] tiles away, where it gives
     * up the chase. The bot walks the route that was searched rather than one from the pathfinder, which doesn't know
     * about [threat]: the route never comes closer to [threat] than the bot is now (up to 3 tiles), and passes next to
     * it only while the bot is still next to it, so the bot never doubles back past it.
     *
     * @param threat The mob to run from.
     * @return The tile the bot runs to, or `null` if there is none within [ESCAPE_SEARCH_RADIUS] tiles.
     */
    fun runFrom(threat: Mob): Position? {
        val start = bot.position
        val near = threat.position
        val farX = near.x + threat.size() - 1
        val farY = near.y + threat.size() - 1
        fun gap(tile: Position) = maxOf(near.x - tile.x, tile.x - farX, near.y - tile.y, tile.y - farY)
        val closest = minOf(gap(start), 3)
        val cameFrom = hashMapOf(start to start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            var tile = queue.removeFirst()
            if (!threat.isWithinDistance(tile, ESCAPE_DISTANCE - 1)) {
                val destination = tile
                val route = java.util.ArrayDeque<Position>()
                while (tile != start) {
                    route.addFirst(tile)
                    tile = cameFrom.getValue(tile)
                }
                bot.navigator.cancel()
                bot.walking.isRunning = true
                bot.walking.replacePath(route)
                return destination
            }
            for (direction in Direction.ALL_EXCEPT_NONE) {
                val next = tile.translate(1, direction)
                val gap = gap(next)
                if (gap >= closest && (gap >= 2 || gap(tile) <= 1) && next !in cameFrom &&
                    next.isWithinDistance(start, ESCAPE_SEARCH_RADIUS) &&
                    world.collisionManager.traversable(tile, EntityType.PLAYER, direction)) {
                    cameFrom[next] = tile
                    queue.addLast(next)
                }
            }
        }
        return null
    }

    /**
     * Uses native home teleport checks at Wilderness level 20 or below, unless teleblocked. Otherwise reuses the
     * existing web-walker to reach the safe Edgeville anchor, without bank access or forced zone teleportation.
     * Actual Wilderness membership determines success. Combat and wandering flags are restored on every exit,
     * including cancellation; native attacks stay disabled throughout a pending escape.
     */
    suspend fun fleeWilderness(): Boolean {
        if (!bot.inWilderness()) return true
        fun active(): Boolean = bot.state == EntityState.ACTIVE && bot.isAlive
        if (!active()) return false
        val previousDisabled = bot.combat.isDisabled
        val previousWandering = bot.isWandering
        bot.walking.isRunning = true
        bot.isWandering = false
        bot.combat.target = null
        bot.combat.isDisabled = true
        suspend fun tryHome(): Boolean {
            if (!active() || levelAt(bot.position) > 20 || bot.status.isTeleBlocked()) return false
            bot.output.sendCommand("home")
            if (active() && !bot.inWilderness()) return true
            waitFor(10.seconds) { !active() || !bot.inWilderness() }
            return active() && !bot.inWilderness()
        }
        try {
            if (tryHome()) return true
            if (!active()) return false
            handler.webWalkTo(Zone.EDGEVILLE.anchor, bank = BankAccess.NONE)
            if (!active()) return false
            return !bot.inWilderness() || tryHome()
        } finally {
            bot.combat.isDisabled = previousDisabled
            bot.isWandering = previousWandering
        }
    }

    /**
     * Attempts to flee from the bot's current combat situation.
     *
     * Wilderness combat uses the dedicated Wilderness escape behaviour. Non-Wilderness combat either teleports home
     * when critically low on health, or falls back to future reverse-pursuit movement logic.
     */
    suspend fun fleeCombat() {
        bot.walking.isRunning = true
        bot.combat.target = null

        if (bot.inWilderness()) {
            handler.combat.fleeWilderness()
        } else if (bot.healthPercent < 15) {
            bot.output.sendCommand("home")
        } else {
            (opponent() ?: bot.combat.lastAttackReceived?.attacker)?.let { stepAwayFrom(it) }
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
