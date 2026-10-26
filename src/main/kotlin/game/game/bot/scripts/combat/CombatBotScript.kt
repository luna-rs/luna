package game.bot.scripts.combat

import api.bot.action.BotCombatActionHandler
import api.bot.action.PkCombatSwitcher
import api.combat.specialAttack.SpecialAttackHandler
import api.bot.Suspendable.delay
import api.bot.Suspendable.waitFor
import io.luna.game.model.mob.bot.Bot
import api.bot.script.BotEscape
import api.bot.script.DynamicBotScript
import api.bot.script.ZonedBotScript
import api.bot.script.lastEscape
import api.predef.*
import api.predef.ext.*
import engine.bot.speech.BotPkingSpeechPool
import engine.bot.speech.BotPkingSpeechPool.PkingSpeech
import engine.combat.prayer.CombatPrayer
import engine.controllers.Controllers.inWilderness
import game.player.item.consume.food.Food
import game.player.item.consume.potion.Potion
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.brain.BotPersonalityManager
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType
import io.luna.game.model.mob.bot.speech.BotSpeech
import io.luna.game.model.mob.combat.damage.CombatDamageType
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
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
 * @param initialState The initial state of this combat script (if the bot should attack the focus or run from it).
 * @param pkSession Optional patrol owner whose hunting boundary applies to this fight, including reflex children.
 * @param targetAllowed Optional encounter guard, rechecked before attacks and suspendable equipment switches.
 * @param onCombatFinished Cleanup after final completion or termination; a combat pause retains encounter ownership.
 * @param lootOnVictory Whether to wait briefly for native death processing and attempt visible ground loot on victory.
 * @author lare96
 */
class CombatBotScript @JvmOverloads constructor(bot: Bot, private var focus: Mob,
    private val initialState: InitialState = InitialState.ATTACK, private val pkSession: PkBotScript? = null,
    private val targetAllowed: () -> Boolean = { true }, private val onCombatFinished: () -> Unit = {},
    private val lootOnVictory: Boolean = false) :
    DynamicBotScript(bot) {

    override val actionSafety = ActionSafety.COMBAT

    /**
     * One fight-owned switch controller retains decision timing across repeated attack and eating checks.
     */
    private val pkSwitcher = PkCombatSwitcher(bot, handler,
        acceptsTarget = { targetAllowed() && pkSession?.allowsPursuit(it) != false })
    private val combatPolicy = BotCombatActionHandler(bot, handler)

    /**
     * Only player fights with a saved PK identity use carried mixed-style switching.
     */
    private val usesPkSwitching: Boolean get() = focus is Player && bot.preferences.pk != null

    /**
     * Keeps waiting during reaction windows, and abandons fights with no usable permitted discipline.
     */
    private suspend fun prepareAttack(): Boolean {
        if (!targetAllowed()) {
            bot.combat.target = null
            stop()
            return false
        }
        if (focus is Player && pkSession?.allowsPursuit(focus as Player) == false) {
            bot.combat.target = null
            if (bot.navigator.currentTarget === focus) bot.navigator.cancel()
            bot.walking.clear()
            stop()
            return false
        }
        if (usesPkSwitching) {
            return when (pkSwitcher.prepare(focus as Player)) {
                PkCombatSwitcher.Result.READY -> true
                PkCombatSwitcher.Result.WAITING -> false
                PkCombatSwitcher.Result.UNAVAILABLE -> {
                    handler.combat.fleeCombat()
                    stop()
                    false
                }
            }
        }
        if (combatPolicy.prepareTrainingStyle()) return true
        bot.combat.target = null
        if (pkSession == null) {
            // Nothing the bot is wielding trains a skill its build wants, so it runs instead.
            running = true
        } else {
            stop()
        }
        return false
    }

    /**
     * An enumerated type representing the initial action this bot should perform.
     */
    enum class InitialState {
        ATTACK,
        RUN
    }

    companion object {

        /**
         * How long a fight may go on without any of the bot's attacks landing before the bot runs instead.
         */
        private val STALLED_FIGHT = 12.seconds

        /**
         * The least run energy a bot needs to get away. Walking is no faster than an NPC's chase.
         */
        private const val MIN_RUN_ENERGY = 10.0

        /**
         * How long after the last hit, in seconds, a bot that ran stays away while its attacker still has it as its
         * target.
         */
        private const val MAX_KEEP_AWAY = 30L

        /**
         * How many new routes a running bot may plan with its attacker still close behind before it stops running.
         */
        private const val MAX_CAUGHT_UP = 8

        /**
         * The run energy a bot that teleported home waits for before it goes back to work, so it can run if something
         * goes for it again. Waiting also gives the attacker time to wander off.
         */
        private const val REST_RUN_ENERGY = 40.0

        /**
         * The longest a bot waits at home for its run energy.
         */
        private val MAX_REST = 5.minutes
    }

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
     */
    private var previousWeapon: Int? = null

    /**
     * The shield equipped before swapping to a two-handed special attack weapon.
     */
    private var previousShield: Int? = null

    /**
     * The life of [focus] when it was given, for an NPC. A respawned NPC is the same object on its next life, and
     * this script does not follow it there.
     */
    private var focusSpawn = (focus as? Npc)?.spawnCount

    /**
     * Whether the bot is running from the fight instead of fighting.
     */
    private var running = false

    /**
     * Whether the bot gave up on the fight (none of its attacks were landing, or it was low with no food), so it won't
     * turn back to fight.
     */
    private var gaveUp = false

    /**
     * The tile the bot is running to, or `null` if it isn't on its way anywhere.
     */
    private var escapeTile: Position? = null

    /**
     * How many routes the bot has planned with its attacker still close behind it.
     */
    private var caughtUp = 0

    /**
     * When this script last started or resumed, in milliseconds.
     */
    private var startedAt = 0L

    /**
     * Where the bot was when this fight started.
     */
    private val attackedAt = bot.position

    /**
     * When the bot teleported home to get away, while it waits there for its run energy, or `null` if it isn't waiting.
     */
    private var restingSince: Long? = null

    override suspend fun init(resumed: Boolean): Boolean {
        startedAt = System.currentTimeMillis()
        if (running) {
            // Whatever paused this script may have moved the bot or left its own walk in the queue.
            escapeTile = null
            return false
        }
        if(bot.personality.type == PersonalityTemplateType.SKILLER) {
            // Skillers do not engage in combat.
            running = true
            return false
        }
        when (initialState) {
            InitialState.ATTACK -> {
                // A resumed script may find its focus dead, or an NPC on its next life: nothing to attack then.
                if (focusAlive()) {
                    bot.combat.attack(focus)
                }
                bot.speechStack.setDisableGeneral(true)
                if (!prepareAttack()) return isTerminated()
                if (!targetAllowed()) return true
                bot.combat.attack(focus)
                delay(600.milliseconds)

                // Twice as likely to say something when starting combat.
                if (focus is Player && rand(bot.personality.social * 2)) {
                    val text = BotPkingSpeechPool.take(bot,
                                                       if (rand(bot.personality.kindness)) PkingSpeech.POSITIVE_START
                                                       else PkingSpeech.NEGATIVE_START)
                    val delay = rand(2, 5)
                    bot.speechStack.pushHead(BotSpeech(text, delay))
                }
            }

            InitialState.RUN -> running = true
        }

        return false
    }

    override suspend fun run(): Boolean {
        if (running) {
            return runAway()
        }
        if (usesPkSwitching) {
            if (!focus.isAlive && lootOnVictory) {
                waitFor(5.seconds) { focus.health > 0 || focus.state != io.luna.game.model.EntityState.ACTIVE }
                if (isActionSafe()) lootItems()
                return true
            }
            if (!focus.isAlive || !focus.isViewableFrom(bot)) return true
            targetEnemy()
            if (isTerminated() || !focus.isAlive) return true
            talk()
            eatFood()
            if (isTerminated()) return true
            drinkPotion()
            activatePrayer()
            useSpecialAttack()
            delay(600.milliseconds)
            return isTerminated()
        }
        if (!combatPolicy.prepareTrainingStyle()) {
            bot.combat.target = null
            if (pkSession == null) {
                running = true
                return false
            }
            handler.combat.fleeCombat()
            return true
        }
        targetEnemy()
        delay(600.milliseconds, 1200.milliseconds)

        if (!bot.combat.inCombat()) {
            return true
        }
        if (!running && pkSession == null && isStalled()) {
            bot.log("My attacks aren't landing, running from the fight.")
            gaveUp = true
            running = true
        }
        if (running) {
            return false
        }

        talk()
        eatFood()
        drinkPotion()
        activatePrayer()
        useSpecialAttack()

        delay(600.milliseconds, 1200.milliseconds)
        return false
    }

    override suspend fun finish() {
        if (running) {
            bot.combat.isDisabled = false
        }
        if (!isPaused()) onCombatFinished()
        bot.speechStack.clear()
        bot.speechStack.setDisableGeneral(false)
        if (usesPkSwitching && !isPaused()) {
            if (bot.combat.target === focus) bot.combat.target = null
            pkSwitcher.clear()
        }
    }

    override suspend fun completed() {
        restoreWeapons()
    }

    /**
     * Attacks the current focus if it is still valid.
     *
     * In PvP, the bot has a small chance to switch to a nearby low-health player in multi-combat. When the focus can't
     * be fought but another mob is still fighting the bot, that mob becomes the focus. If the current focus is dead,
     * unreachable, out of view, or no longer valid for multi-combat, the bot looks for nearby loot instead.
     */
    private suspend fun targetEnemy() {
        val opponent = if (pkSession == null) combatPolicy.opponent() else null
        if (opponent != null && (opponent != focus || !focusAlive()) && opponent.isViewableFrom(bot) &&
            (!focusAlive() || !focus.isViewableFrom(bot) || !bot.combat.checkMultiCombat(focus))) {
            bot.log("Switching focus from $focus to $opponent.")
            focus = opponent
            focusSpawn = (opponent as? Npc)?.spawnCount
        }
        if (focusAlive() && focus.isViewableFrom(bot) && bot.combat.checkMultiCombat(focus)) {
            if (focus is Player && !usesPkSwitching && rand(1 of 5)) {
                val local = world.locator.findViewablePlayers(bot) {
                    bot.combat.checkMultiCombat(it) &&
                            it.healthPercent < 25 &&
                            it.isWithinDistance(bot, 10)
                }

                if (local.size > 0) {
                    focus = local.first()
                    focusSpawn = null
                }
            }

            if (!prepareAttack()) return
            // Equipping can suspend; do not initiate against a dead, vanished or newly contested target.
            if (!focus.isAlive || !focus.isViewableFrom(bot) || !bot.combat.checkMultiCombat(focus)) return
            if (usesPkSwitching && !combatPolicy.canAttackPlayer(focus as Player)) return
            bot.combat.attack(focus)
            if (!usesPkSwitching) delay(1200.milliseconds, 3.seconds)
            return
        }

        lootItems()
    }

    /**
     * Whether [focus] is alive and, for an NPC, still on the life this script was given.
     */
    private fun focusAlive(): Boolean {
        val current = focus
        return current.isAlive && (current !is Npc || current.spawnCount == focusSpawn)
    }

    /**
     * Makes bots talk in a PKing context while the fight is ongoing.
     */
    private fun talk() {
        // Less chance to talk while fighting.
        if (focus is Player && rand(bot.personality.social / 2)) {
            val text =
                BotPkingSpeechPool.take(bot, if (rand(bot.personality.kindness)) PkingSpeech.POSITIVE_FIGHTING else
                    PkingSpeech.NEGATIVE_FIGHTING)
            bot.speechStack.pushHead(BotSpeech(text, 0))
        }
    }

    /**
     * Eats food when the bot is low on health.
     *
     * If no usable food is found, the bot attempts to flee combat. After eating or fleeing, the bot refreshes its
     * target so it can continue fighting if still safe enough to do so.
     */
    private suspend fun eatFood() {
        if (bot.healthPercent < 30) {
            if (!handler.inventory.eatAnyFood()) {
                // TODO@1.0 Fleeing should depend on bot intelligence, enemy health, combat level, risk, gear value,
                //  Wilderness depth, and available escape options.
                if (pkSession == null) {
                    gaveUp = true
                    running = true
                    return
                }
                handler.combat.fleeCombat()
                if (usesPkSwitching) stop()
                return
            }

            targetEnemy()
        }
    }

    /**
     * Runs from the fight for one cycle, to a tile far enough away that the attacker gives up the chase, then waits
     * there until the combat timer runs out and the attacker drops it. In the Wilderness, or when nearly dead, the bot
     * uses the default escape instead, which teleports it home. A bot that can't get away (nowhere to run, too tired to
     * run, or its attacker keeps up) fights back against an attacker that isn't much stronger, unless it already gave
     * up on the fight; otherwise it teleports home. Outside the Wilderness, a bot that teleports home remembers where it
     * was attacked so its work keeps it away from there for a while, and waits at home until it has the energy to run.
     *
     * @return `true` once the bot is out of combat and done waiting.
     */
    private suspend fun runAway(): Boolean {
        val threat = combatPolicy.opponent() ?: focus.takeIf { focusAlive() }
        // Going back while the attacker still has the bot as its target would start the fight again.
        if (!bot.combat.inCombat() &&
            (threat?.combat?.target != bot || bot.combat.timer.elapsed().seconds > MAX_KEEP_AWAY)) {
            if (!isResting()) return true
            delay(1.seconds)
            return false
        }
        if (bot.inWilderness()) {
            handler.combat.fleeCombat()
            return true
        }
        // Auto retaliate would turn the bot back toward whatever hits it.
        bot.combat.isDisabled = true
        if (bot.healthPercent < 15) {
            rememberEscape(threat ?: focus)
            handler.combat.fleeCombat()
        } else if (threat != null && threat.combat.target == bot && threat.isViewableFrom(bot)) {
            val tile = escapeTile
            if (tile == null || bot.position == tile || bot.walking.isEmpty ||
                threat.isWithinDistance(tile, Position.VIEWING_DISTANCE)) {
                val stuck = when {
                    bot.runEnergy < MIN_RUN_ENERGY -> "Too tired to run from $threat"
                    caughtUp >= MAX_CAUGHT_UP -> "Can't shake off $threat"
                    else -> null
                }
                val next = if (stuck == null) combatPolicy.runFrom(threat) else null
                escapeTile = next
                if (next != null) {
                    if (threat.isWithinDistance(bot, 3)) caughtUp++
                    bot.log("Running from $threat to $next.")
                } else if (!gaveUp && pkSession == null && bot.personality.type != PersonalityTemplateType.SKILLER &&
                    !combatPolicy.isThreat(threat) && combatPolicy.prepareTrainingStyle()) {
                    bot.log("${stuck ?: "Nowhere to run from $threat"}, fighting back.")
                    bot.combat.isDisabled = false
                    running = false
                    startedAt = System.currentTimeMillis()
                    focus = threat
                    focusSpawn = (threat as? Npc)?.spawnCount
                    return false
                } else {
                    bot.log("${stuck ?: "Nowhere to run from $threat"}, teleporting home.")
                    rememberEscape(threat)
                    output.sendCommand("home")
                }
            }
        }
        delay(600.milliseconds)
        return false
    }

    /**
     * Records that the bot is teleporting home to get away from [attacker], so the zoned script it goes back to and
     * the skilling factories keep it away from where it was attacked for a while, and makes the bot wait at home for
     * its run energy. PK sessions handle their own escapes and are left alone.
     */
    private fun rememberEscape(attacker: Mob) {
        if (pkSession != null) return
        val zone = bot.scriptStack.findActive(ZonedBotScript::class.java).orElse(null)?.interruptedZone
        bot.lastEscape = BotEscape(attackedAt, attacker.toString(), System.currentTimeMillis(), zone)
        if (restingSince == null && bot.runEnergy < REST_RUN_ENERGY) {
            bot.log("Will rest at home until run energy is back to $REST_RUN_ENERGY. runEnergy=${bot.runEnergy}")
            restingSince = System.currentTimeMillis()
        }
    }

    /**
     * Whether the bot, having teleported home, is still waiting there for its run energy. It waits until the energy is
     * back to [REST_RUN_ENERGY], or for at most [MAX_REST].
     */
    private fun isResting(): Boolean {
        val since = restingSince ?: return false
        if (bot.runEnergy >= REST_RUN_ENERGY || System.currentTimeMillis() - since >= MAX_REST.inWholeMilliseconds) {
            bot.log("Done resting. runEnergy=${bot.runEnergy}")
            restingSince = null
            return false
        }
        return true
    }

    /**
     * Determines if something keeps the bot in combat while none of its attacks land, like an attacker it can't reach.
     *
     * @return `true` if the fight has stalled.
     */
    private fun isStalled(): Boolean {
        val lastAttack = bot.combat.lastAttackSent?.timestamp?.toEpochMilli() ?: 0
        return combatPolicy.opponent() != null &&
            System.currentTimeMillis() - maxOf(lastAttack, startedAt) > STALLED_FIGHT.inWholeMilliseconds
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
            potions += Potion.SUPER_STRENGTH.doses
        }

        if (!bot.ranged.isBoosted) {
            potions += Potion.RANGING_POTION.doses
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
     * The special weapon is resolved once and cached. If enough energy is available and the bot is not already
     * wielding that weapon, it attempts to equip it from inventory, then toggles the special attack bar on. Otherwise,
     * the previous weapon and shield are re-equipped.
     */
    private suspend fun useSpecialAttack() {
        val weaponId = bot.equipment.weapon?.id
        if (usesPkSwitching) {
            // Tactical special-weapon swaps are deferred; never override a chosen ranged or Magic phase.
            if (!bot.combat.magic.isAutocasting && weaponId in SpecialAttackHandler.getAllWeaponIds() &&
                bot.combat.specialBar.energy >= SpecialAttackHandler.getDrain(weaponId!!) &&
                combatPolicy.prepareTrainingStyle()) bot.combat.specialBar.toggleOn()
            return
        }

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

        if (bot.combat.specialBar.energy < SpecialAttackHandler.getDrain(specialWeapon!!)) {
            restoreWeapons()
            return
        }

        if (weaponId != specialWeapon) {
            val shieldId = bot.equipment.shield?.id
            val twoHanded = equipDef(specialWeapon!!).isTwoHanded
            if (bot.inventory.isFull && twoHanded && weaponId != null && shieldId != null) {
                return
            }

            // Recorded before equipping, since pausing this script mid-equip doesn't stop the swap.
            previousWeapon = weaponId
            if (twoHanded && shieldId != null) {
                previousShield = shieldId
            }
            if (!handler.equipment.equip(specialWeapon!!)) {
                return
            }

            if (bot.combat.weapon.specialAttackType == null) {
                return
            }
        }

        if (!combatPolicy.prepareTrainingStyle()) {
            bot.combat.target = null
            stop()
            return
        }
        bot.combat.specialBar.toggleOn()
    }

    /**
     * Re-equips the weapon and shield that were worn before swapping to the special attack weapon.
     */
    private suspend fun restoreWeapons() {
        if (previousWeapon != null && handler.equipment.equip(previousWeapon!!)) {
            previousWeapon = null
        }

        if (previousShield != null && handler.equipment.equip(previousShield!!)) {
            previousShield = null
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

            if (prayerPercent < 0.15 && bot.combat.prayers.active.size > 2) {
                bot.combat.prayers.deactivateAll()
                bot.combat.prayers.activateIfOff(CombatPrayer.STEEL_SKIN)
                bot.combat.prayers.activateIfOff(CombatPrayer.PROTECT_ITEM)
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
                bot.combat.prayers.activateIfOff(protectionPrayer)
            } else {
                bot.combat.prayers.deactivate(CombatPrayer.PROTECT_FROM_MELEE, true)
                bot.combat.prayers.deactivate(CombatPrayer.PROTECT_FROM_MISSILES, true)
                bot.combat.prayers.deactivate(CombatPrayer.PROTECT_FROM_MAGIC, true)
            }

            bot.combat.prayers.activateIfOff(CombatPrayer.STEEL_SKIN)
            bot.combat.prayers.activateIfOff(CombatPrayer.PROTECT_ITEM)

            if (!bot.combat.weapon.isRanged && !bot.combat.magic.isAutocasting) {
                bot.combat.prayers.activateIfOff(CombatPrayer.ULTIMATE_STRENGTH)
                bot.combat.prayers.activateIfOff(CombatPrayer.INCREDIBLE_REFLEXES)
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
        // tODO when doing a "search for item" script and shops are being checked, every shop should have an "anchor"
        //  npc where applicable so the bot can find the npc (we can determine which zone the npc is in, if any, and travel there!)
        return when (id) {
            995 -> true
            else -> false
        }
    }
}
