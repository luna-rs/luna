package game.bot.scripts

import api.bot.action.CombatLoadout
import api.bot.action.BotCombatActionHandler
import api.bot.action.PvmTactics
import api.bot.action.BotPotionActionHandler
import api.bot.Suspendable.delay
import api.bot.Suspendable.naturalDelay
import api.bot.Suspendable.waitFor
import api.bot.script.TargetingZonedBotScript
import api.bot.social.CombatCampBehavior
import api.bot.social.SocialBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.*
import com.google.common.collect.ImmutableSetMultimap
import com.google.common.collect.SetMultimap
import com.google.gson.JsonObject
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotGearSelector
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.slayer.Slayer.activeSlayerTask
import game.skill.slayer.SlayerMaster
import game.skill.slayer.SlayerTaskType
import engine.combat.prayer.CombatPrayer
import io.luna.game.model.EntityState
import game.skill.fishing.catchFish.Fish
import game.skill.herblore.identifyHerb.Herb
import game.skill.magic.Magic
import game.skill.magic.Staff
import game.skill.mining.Ore
import game.skill.prayer.Bone
import game.skill.smithing.BarType
import game.skill.smithing.smithBar.SmithingTable
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.model.Position
import io.luna.game.model.def.CombatStyleDefinition
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.item.DeathGroundItem
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType
import io.luna.game.model.mob.combat.CombatAction
import io.luna.game.model.mob.combat.CombatStance
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.util.GsonUtils
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Fights configured NPCs using owned melee, ranged or elemental autocast supplies.
 * The existing targeting and banking lifecycle handles travel, replenishment and bounded target searches.
 *
 * Sessions including KBD require combat level 100, a one-handed melee loadout and an owned anti-dragon
 * shield. Banking equips that shield before travel and withdraws up to twenty food items healing at
 * least sixteen Hitpoints. Missing protection or food requests another bank visit. Bones collected
 * in the lair are retained for profit instead of buried.
 * Specialist assignments replace ordinary outfit slots with mandatory owned protection. Banshee visits equip
 * earmuffs before travel and recheck them before every attack, including after banking and restored sessions.
 *
 * @property purpose The combat discipline retained throughout this session.
 * @property spell The elemental spell selected for a Magic session, preserved when saving.
 * @property slayerTask Optional native assignment restricting this session to exact task NPC IDs.
 * @property slayerMaster The original assignee; an assignment change ends this combat session.
 * @author lare96
 */
open class NpcCombatScript(bot: Bot,
                           duration: Duration,
                           zones: MutableList<SubZone>,
                           private val names: SetMultimap<SubZone, String> = ImmutableSetMultimap.of(),
                           val purpose: BotGearPurpose = BotGearPurpose.MELEE,
                           private var spell: CombatSpell? = null,
                           private val slayerTask: SlayerTaskType? = null,
                           private val slayerMaster: SlayerMaster? = null) :
    TargetingZonedBotScript<Npc>(bot, duration, zones), SocialBotScript {

    override val actionSafety = ActionSafety.COMBAT

    // todo zone selection should happen BEFORE equipment/banking. so that we can prepare for the zone to travel to
    companion object {

        /**
         * Mandatory task equipment shared by assignment selection, banking and live attack checks.
         * Entries override ordinary outfit choices; native monster mechanics remain authoritative.
         */
        private val SLAYER_EQUIPMENT = mapOf(
            SlayerTaskType.BANSHEE to mapOf(Equipment.HEAD to 4166)
        )

        /**
         * Returns mandatory equipment slots for a supported specialist assignment, or none for ordinary combat.
         */
        internal fun requiredSlayerEquipment(task: SlayerTaskType?): Map<Int, Int> =
            task?.let { SLAYER_EQUIPMENT[it] }.orEmpty()

        /** Camps that require an equipped anti-dragon shield before fighting. */
        val DRAGON_ZONES = setOf(
            SubZone.GREEN_DRAGONS,
            SubZone.KBD_LAIR
        )

        /** Per-zone NPC restrictions must survive script persistence. */
        open class NpcCombatData : ZonedBotScriptData() {
            /** Exact NPC names accepted for deliberate target selection in each saved camp. */
            var names: SetMultimap<SubZone, String> = ImmutableSetMultimap.of()

            /** Legacy sessions default to melee; new sessions retain their discipline and spell. */
            var purpose: BotGearPurpose = BotGearPurpose.MELEE
            /** Optional elemental autocast spell; absent in older melee sessions. */
            var spell: CombatSpell? = null

            /**
             * Optional native Slayer restriction; absent from legacy ordinary combat sessions.
             */
            var slayerTask: SlayerTaskType? = null
            /**
             * Master that assigned the saved task, used to reject a changed assignment on restoration.
             */
            var slayerMaster: SlayerMaster? = null

            override fun load(data: JsonObject) {
                super.load(data)
                slayerTask = data.get("slayerTask")?.takeUnless { it.isJsonNull }?.asString?.let(SlayerTaskType::valueOf)
                slayerMaster = data.get("slayerMaster")?.takeUnless { it.isJsonNull }?.asString?.let(SlayerMaster::valueOf)
                purpose = data.get("purpose")?.asString?.let(BotGearPurpose::valueOf) ?: BotGearPurpose.MELEE
                spell = data.get("spell")?.takeUnless { it.isJsonNull }?.asString?.let(CombatSpell::valueOf)
                val builder = ImmutableSetMultimap.builder<SubZone, String>()
                val savedNames = data.get("names")?.takeUnless { it.isJsonNull }?.asJsonObject
                savedNames?.entrySet()?.forEach { (zone, npcs) ->
                    npcs.asJsonArray.forEach { builder.put(SubZone.valueOf(zone), it.asString) }
                }
                names = builder.build()
            }

            override fun save(data: JsonObject) {
                super.save(data)
                slayerTask?.let { data.addProperty("slayerTask", it.name) }
                slayerMaster?.let { data.addProperty("slayerMaster", it.name) }
                data.addProperty("purpose", purpose.name)
                spell?.let { data.addProperty("spell", it.name) }
                val savedNames = JsonObject()
                names.asMap().forEach { (zone, npcs) ->
                    savedNames.add(zone.name, GsonUtils.toJsonTree(npcs))
                }
                data.add("names", savedNames)
            }
        }

        val ALL_LOOT_ITEMS = lazyVal { // and if cache value > 10_000
            val items =
                HashSet<Int>() // todo optional filter if high level and intelligent and only want higher resources
            items += BarType.ID_TO_BAR.keys
            items += Ore.ITEM_MAP.keys
            items += Tree.ALL.keys
            items += Herb.UNID_TO_HERB.keys
            items += SmithingTable.ID_TO_ITEM.values.filter { it.barType.level >= BarType.MITHRIL.level }
                .map { it.item.id }
            items += 4151 // Abyssal whip.
            items += Staff.ID_TO_STAFF.keys
            items += Fish.entries.map { it.id }
            items += BotGearSelector.ALL_GEAR.value.values().map { it.id }

            // Should always be last -- maps all noted variants.
            items += items.filter { itemDef(it).notedId.isPresent }.map { itemDef(it).notedId.asInt }
            items
        }

    }

    // Retain the base-data constructor so existing persisted scripts still load.
    constructor(bot: Bot, data: ZonedBotScriptData) : this(bot, data.duration, data.zones,
                                                           (data as? NpcCombatData)?.names ?: ImmutableSetMultimap.of(),
                                                           (data as? NpcCombatData)?.purpose ?: BotGearPurpose.MELEE,
                                                           (data as? NpcCombatData)?.spell,
                                                           (data as? NpcCombatData)?.slayerTask,
                                                           (data as? NpcCombatData)?.slayerMaster)

    /**
     * Runtime identity rejects a replaced task even when its type and master happen to match.
     * Restored scripts bind to the independently persisted native assignment and validate its metadata.
     */
    private val assignment = if (slayerTask == null) null else bot.activeSlayerTask

    /**
     * Checks live assignment state without awarding credit or altering the native task.
     */
    internal fun assignmentActive(): Boolean = slayerTask == null ||
        (assignment != null && bot.activeSlayerTask === assignment && assignment.task == slayerTask &&
            assignment.assignee == slayerMaster && assignment.remaining > 0 && bot.slayer.level >= slayerTask.level)

    /**
     * Keeps task NPC IDs authoritative even when several monsters share a camp or display name.
     */
    internal fun acceptsAssignment(npc: Npc): Boolean = assignmentActive() &&
        (slayerTask == null || npc.id in slayerTask.npcs)

    /**
     * Eats and prays the way a player watching their hitpoints and prayer would; the Kalphite Queen trip keeps its own
     * supply and prayer rules.
     */
    protected val tactics = PvmTactics(bot)

    /** Selected owned gear; resumed sessions validate their actual equipment before attacking. */
    private var loadout: CombatLoadout? = null
    /** Style selection happens after gear is equipped, and is retained until the next bank visit. */
    private var combatConfigured = false

    /**
     * Saved assignment metadata selects the same protection after restoration; equipped items are checked live.
     */
    private val requiredSlayerGear = requiredSlayerEquipment(slayerTask)

    private val queenTrip: Boolean get() = SubZone.KALPHITE_QUEEN in originalZones

    /**
     * Optional cannon supplies are selected once at each bank visit and never manufactured by this session.
     */
    private var cannonPrepared = false

    private val socialCamp: Boolean get() = !queenTrip && activeZone != null && activeZone !in DRAGON_ZONES

    internal val campBehavior = CombatCampBehavior(this) { canAttack(it) }

    override fun canSocialize(): Boolean = canTalkWhileWorking()

    override fun canTalkWhileWorking(): Boolean = socialCamp && isRunning() && !forceBanking && combatConfigured &&
        activeZone!!.area.contains(bot) && isActionSafe() &&
        bot.healthPercent > maxOf(50.0, 75.0 * (1.0 - bot.personality.confidence * 0.75)) &&
        bot.combat.target !is Player && (!bot.combat.inCombat() || bot.combat.lastCombatWith is Npc)

    override fun shouldLeaveTargetZone(): Boolean = socialCamp && campBehavior.shouldLeave()

    // Combat uses actual competition and progress, rather than the shared twenty-player fallback.
    override fun shouldLeaveCrowdedZone(visiblePlayers: Int): Boolean = false

    override fun orderTargets(options: MutableList<Npc>) {
        if (socialCamp) campBehavior.order(options) else super.orderTargets(options)
    }

    override suspend fun equipment(): BotGearLocator = BotGearLocator(bot, loadout!!.gear)

    override suspend fun onEquipmentFailed(equipmentLocator: BotGearLocator) {
        stop()
    }

    override suspend fun onInit(resumed: Boolean): Boolean {
        // Gear selection and banking can suspend waiting for combat to end. Keep the normal
        // combat response available until this script is ready to handle an actual engagement.
        bot.reflex.isDisableCombatReflex = false
        if (!assignmentActive()) return false
        if (!cannonPrepared && !CannonBotSupport.recover(bot)) return false
        if(bot.personality.type == PersonalityTemplateType.SKILLER) {
            // Skillers do not engage in combat.
            handler.combat.fleeCombat()
            return false
        }

        bot.sendVarp(PersistentVarp.AUTO_RETALIATE, 1)

        loadout = CombatLoadout.find(bot, purpose, spell) ?: return false
        for ((slot, id) in requiredSlayerGear) {
            if (!bot.itemTracker.contains(id) || !equipDef(id).meetsAllRequirements(bot)) return false
            loadout!!.gear[slot] = id
        }
        if (queenTrip && (originalZones.size != 1 || purpose != BotGearPurpose.MELEE ||
                !KalphiteQueenTrip.levelsReady(bot) || !KalphiteQueenTrip.gearReady(loadout!!.gear) ||
                (!resumed && !KalphiteQueenTrip.ready(bot, loadout!!)))) return false
        if (SubZone.KBD_LAIR in originalZones) {
            val weapon = loadout!!.gear[Equipment.WEAPON] ?: return false
            if (bot.combatLevel < 100 || purpose != BotGearPurpose.MELEE ||
                equipDef(weapon).isTwoHanded || !bot.itemTracker.contains(1540)) return false
            loadout!!.gear[Equipment.SHIELD] = 1540
        }
        spell = loadout!!.spell?.spell
        if (resumed && !configureCombat()) forceBanking = true
        return true
    }


    override suspend fun finish() {
        campBehavior.close()
        bot.reflex.isDisableCombatReflex = false
        tactics.stopAttending()
        if (queenTrip && !bot.combat.inCombat()) bot.combat.prayers.deactivateAll()
        // A pause hands the fight to another script, which looks after the prayers from there.
        if (!queenTrip && !isPaused()) tactics.leaveFight()
        // Keep the spell through a pause, so the combat reflex can finish the fight with it.
        if (!isPaused()) {
            clearMagic()
        }
        if (coroutineContext.isActive) CannonBotSupport.recover(bot)
    }

    override suspend fun beforeLeavingZone(): Boolean {
        bot.combat.target = null
        if (!CannonBotSupport.recover(bot)) return false
        campBehavior.leave()
        return true
    }

    override suspend fun find(searchBase: Position, searchRadius: Int): MutableCollection<Npc> {
        attend()
        if (!prepareCombat()) return mutableListOf()
        if (bot.combat.inCombat() && bot.combat.lastCombatWith is Npc &&
            (!queenTrip || canAttack(bot.combat.lastCombatWith as Npc))) {
            return (bot.combat.lastCombatWith as Npc).takeIf { acceptsAssignment(it) }
                ?.let { mutableListOf(it) } ?: mutableListOf()
        }
        val options = world.locator.findNpcs(searchBase, searchRadius, true) {
            canAttack(it) && it.combat.isAttackable
                    && bot.combat.checkMultiCombat(it)
                    && !ctx.cache.mapIndexTable.getTile(it.position).isBlocked
            // todo incorporate checks for ranged/magic, this is for stuff like ducks that move on water
        }

        return options
    }

    override suspend fun onBankRequestedTargeting(initial: Boolean): Boolean {
        campBehavior.close()
        bot.reflex.isDisableCombatReflex = false
        if (!queenTrip) tactics.leaveFight()
        combatConfigured = false
        bot.combat.target = null
        clearMagic()
        if (CannonBotSupport.needsRecovery(bot) && bot.emotions.isNervousAboutHp && bot.combat.inCombat()) {
            handler.banking.travelToNearestBank()
            return false
        }
        return CannonBotSupport.recover(bot)
    }

    /** Dragon protection is withdrawn from owned stock rather than generated during banking. */
    private val antiDragonShield = item("Anti-dragon shield")
    override suspend fun onBankOpen(initial: Boolean) {

        if (DRAGON_ZONES.any { it in originalZones } && 1540 !in bot.equipment &&
            !handler.banking.withdraw(antiDragonShield)) {
            bot.preferences.addWantedItem(1540, 3)
            stop()
            return
        }
        val selected = loadout ?: return
        for ((slot, id) in requiredSlayerGear) {
            if (bot.equipment[slot]?.id != id &&
                (!handler.banking.withdraw(Item(id)) || !handler.equipment.equip(id))) {
                stop()
                return
            }
        }
        val weapon = selected.gear[Equipment.WEAPON]
        if (weapon != null && bot.equipment.weapon?.id != weapon &&
            (!handler.banking.withdraw(Item(weapon, if (purpose == BotGearPurpose.RANGED &&
                selected.gear[Equipment.AMMUNITION] == null) 500 else 1)) || !handler.equipment.equip(weapon))) {
            bot.preferences.addWantedItem(weapon, if (purpose == BotGearPurpose.RANGED &&
                selected.gear[Equipment.AMMUNITION] == null) 1000 else 3)
            stop()
            return
        }
        if (SubZone.KBD_LAIR in originalZones && bot.equipment.shield?.id != 1540 &&
            (!handler.equipment.equip(1540) || bot.equipment.weapon == null)) {
            stop()
            return
        }
        if (purpose == BotGearPurpose.RANGED) {
            val id = selected.gear[Equipment.AMMUNITION] ?: weapon
            if (id == null || (bot.equipment.computeAmountForId(id) == 0 &&
                    (!handler.banking.withdraw(Item(id, 500)) || !handler.equipment.equip(id)))) {
                id?.let { bot.preferences.addWantedItem(it, 1000) }
                stop()
                return
            }
        }
        for ((id, amount) in selected.runes) {
            if (!handler.banking.withdraw(Item(id, amount * 500)) || bot.inventory.computeAmountForId(id) < amount) {
                bot.preferences.addWantedItem(id, 1000)
                stop()
                return
            }
        }
        if (!configureCombat()) {
            stop()
            return
        }
        if (DRAGON_ZONES.any { it in originalZones }) handler.potions.withdrawAntifire()
        if (queenTrip) {
            for ((id, amount) in KalphiteQueenTrip.supplies) {
                if (!handler.banking.withdraw(Item(id, amount)) || bot.inventory.computeAmountForId(id) < amount) {
                    bot.preferences.addWantedItem(id, amount)
                    stop()
                    return
                }
            }
            if (!KalphiteQueenTrip.equipped(bot)) stop()
            return
        }
        if (!handler.inventory.hasAnyFood()) {
            // todo change minimum heal scaling to combat level
            val kbd = SubZone.KBD_LAIR in originalZones
            if (!handler.banking.withdrawAnyFood(amount = if (kbd) 20 else 8,
                    minimumHeal = if (kbd) 16 else 0, retry = !kbd) && kbd) {
                stop()
            }
        }
        cannonPrepared = slayerTask == null && CannonBotSupport.prepare(bot, originalZones, purpose)
    }

    override suspend fun onNewActiveZone(lastZone: SubZone?) {
        if (activeZone in DRAGON_ZONES && 1540 !in bot.equipment &&
            (1540 !in bot.inventory || !handler.equipment.equip(1540) || bot.equipment.weapon == null)) {
            stop()
        }
    }
    // todo auto bury bones after each kill
    // todo use potions, etc. when available and needed
    // todo dumb bots collect all loot, smart bots only collect good loot
    // todo safespotting (map of combat sub-zones and all known safespots??)
    //  ^^ SAFESPOT_MAGIC, SAFESPOT_RANGED, MAGIC, RANGED, MELEE
    //  safespot logic: select npc, within 10 squares of safespot, attack it, once in combat, immediately run to safespot and attack from there
    //  random chance to safespot based on intelligence (highest intelligence)

    // todo discard focus if not reachable.
    override suspend fun onExecuteInZone(searching: Boolean) {
        if (!prepareCombat()) return
        attend()
        if (forceBanking || (queenTrip && waitingForQueen())) return
        if (bot.combat.lastCombatWith?.isAlive == true &&
            (!queenTrip || bot.combat.lastCombatWith?.let { it is Npc && canAttack(it) } == true) && bot.combat.inCombat() &&
            !bot.actions.contains(CombatAction::class.java)
        ) {
            val opponent = bot.combat.lastCombatWith
            if (slayerTask == null) {
                handler.interactions.interact(3, opponent)
            } else if (opponent is Npc && acceptsAssignment(opponent)) {
                interactWithTarget(3, opponent)
            }
            if (queenTrip) {
                bot.naturalDelay()
            } else if (!tactics.watch()) {
                forceBanking = true
            }
        } else if (focus?.isAlive != true) {
            lootItems()
        }
    }

    override suspend fun refocus(): Boolean {
        if (!prepareCombat()) return true
        attend()
        if (queenTrip && (forceBanking || waitingForQueen())) return false
        if (cannonPrepared && !forceBanking) {
            val zone = activeZone
            if (zone != null && !CannonBotSupport.maintain(bot, zone)) {
                forceBanking = true
                return true
            }
        }
        if (focus?.isAlive != true || !acceptsAssignment(focus!!)) {
            lootItems()
            return true
        }
        if (bot.combat.target == focus && bot.actions.contains(CombatAction::class.java)) {
            return false
        }
        if (!bot.combat.checkMultiCombat(focus)) {
            if (socialCamp) campBehavior.lost(focus!!)
            lastOptions.clear()
            if (bot.combat.lastCombatWith is Npc && bot.combat.inCombat() &&
                (!queenTrip || canAttack(bot.combat.lastCombatWith as Npc))) {
                lastOptions += bot.combat.lastCombatWith as Npc
            } else {
                handler.combat.fleeCombat()
            }
            return true
        }
        // Retry the current target through the same click path as a human, and abandon it on failure.
        return !interactWithTarget(3, focus!!)
    }

    override suspend fun onAssignFocus(newFocus: Npc): Boolean {
        if (queenTrip && !canAttack(newFocus)) return false
        val allowed = acceptsAssignment(newFocus) && bot.combat.checkMultiCombat(newFocus)
        if (socialCamp) {
            if (!allowed) campBehavior.lost(newFocus)
            if (!campBehavior.acceptsTarget(newFocus)) return false
        }
        if (!allowed) {
            bot.log("Rejected combat target id=${newFocus.id} at ${newFocus.position}: multi-combat check failed.")
        }
        return allowed
    }

    override val targetSearchTimeout: Duration
        get() = if (socialCamp && campBehavior.holdingConversation) Duration.INFINITE else 180.seconds

    override suspend fun interactionOption(target: Npc): Int? {
        attend()
        return if (prepareCombat() && acceptsAssignment(target)) 3 else null
    }

    override suspend fun interactWithTarget(option: Int, target: Npc): Boolean {
        if (slayerTask == null && !socialCamp) return super.interactWithTarget(option, target)
        return handler.interactions.interact(option, target) {
            isActionSafe() && !forceBanking && acceptsAssignment(target) && prepareCombat() &&
                bot.combat.checkMultiCombat(target) && (!socialCamp || campBehavior.acceptsTarget(target))
        }
    }

    override fun snapshot(): NpcCombatData {
        val data = NpcCombatData()
        data.duration = duration
        data.zones = originalZones.toMutableList()
        data.names = ImmutableSetMultimap.copyOf(names)
        data.purpose = purpose
        data.spell = spell
        data.slayerTask = slayerTask
        data.slayerMaster = slayerMaster
        return data
    }

    // todo static set of valid npcs
    private fun canAttack(npc: Npc): Boolean {
        if (!acceptsAssignment(npc)) return false
        if (queenTrip) return npc.id in setOf(1158, 1160) && KalphiteQueenTrip.canContinue(bot)
        if (npc.id == 50) {
            return activeZone == SubZone.KBD_LAIR && bot.combatLevel >= 100 &&
                bot.equipment.shield?.id == 1540 && bot.equipment.weapon != null && handler.inventory.hasAnyFood()
        }
        if (npc.combat.target == bot) {
            return true
        }
        val zone = activeZone
        if (zone != null) {
            val zoneNames = names[zone]
            if (zoneNames.isNotEmpty()) {
                return npc.def().name in zoneNames
            }
        }

        return npc.combatLevel < 15 || npc.combatLevel / 2 < bot.combatLevel
    }

    /**
     * Determines if the bot should pick up [item] after a kill.
     */
    protected open fun isLoot(item: GroundItem): Boolean {
        return TreasureTrailBotScript.wantsClue(bot, item.id) || item is DeathGroundItem || item.id in ALL_LOOT_ITEMS.value
    }

    /**
     * Collects eligible drops in shared recovery, wanted-item and guide-value order, checking actual capacity per drop.
     * Supply checks still run between attempts; an item that will not fit cannot hide a later compatible stack.
     */
    private suspend fun lootItems() {
        attend()
        if (queenTrip && forceBanking) return
        // Wait for the drop to appear.
        if (queenTrip) {
            delay(2.seconds, 4.seconds)
        } else if (!tactics.watch(2.seconds, 4.seconds)) {
            forceBanking = true
        }
        val groundItems = world.locator.findViewableItems(bot) { isLoot(it) && it.isVisibleTo(bot) }
        for (item in handler.loot.ordered(groundItems)) {
            if (!isActionSafe() || forceBanking || !assignmentActive()) return
            if (!handler.loot.canPickup(item)) continue
            handler.loot.pickup(item) { isActionSafe() && !forceBanking && assignmentActive() }
            attend()
            if (queenTrip && forceBanking) return
            bot.naturalDelay()
        }
        for ((index, item) in bot.inventory.withIndex()) {
            if (activeZone != SubZone.KBD_LAIR && item != null && BotCombatActionHandler.wantsToTrain(bot, SKILL_PRAYER) && Bone.ID_TO_BONE.containsKey(item.id)) {
                handler.inventory.clickItem(1, item.id, index)
                break
            }
        }
    }

    /**
     * Looks after the bot's hitpoints and prayer, and sends it to the bank when it runs out of what it needs.
     */
    private suspend fun attend() {
        if (queenTrip) {
            if (bot.health <= 65) handler.inventory.eatAnyFood(minimumHeal = 20)
            if (!KalphiteQueenTrip.canContinue(bot)) forceBanking = true
            return
        }
        if (activeZone == SubZone.KBD_LAIR &&
            (bot.equipment.shield?.id != 1540 || !handler.inventory.hasAnyFood())) {
            forceBanking = true
        }
        if (!tactics.check()) {
            forceBanking = true
        }
        handler.potions.drink(fighting = bot.combat.inCombat(), dragons = activeZone in DRAGON_ZONES ||
            BotPotionActionHandler.breathesFire(bot.combat.lastCombatWith))
    }

    override suspend fun beforeZoneWork(): Boolean {
        if (!assignmentActive()) {
            bot.combat.target = null
            stop()
            return true
        }
        if (!queenTrip) {
            campBehavior.tick(activeZone, focus)
            if (campBehavior.holdingConversation && duration < 60.seconds) duration = 60.seconds
            return false
        }
        val inChamber = bot.position.z == 0 && SubZone.KALPHITE_QUEEN.area.contains(bot)
        bot.reflex.isDisableCombatReflex = inChamber
        if (bot.health <= 65) handler.inventory.eatAnyFood(minimumHeal = 20)
        handler.potions.drink(fighting = bot.combat.inCombat(), dragons = false, prayerBelow = 31)
        if (!KalphiteQueenTrip.canContinue(bot) || bot.inventory.isFull) forceBanking = true
        if (forceBanking) {
            bot.combat.target = null
            if (inChamber) {
                // Use the same home command as ordinary bot escape; do not re-engage while waiting to leave.
                bot.output.sendCommand("home")
                if (waitFor(6.seconds) { bot.position.y < 9000 }) bot.combat.prayers.deactivateAll()
                return true
            }
            return false
        }
        if (inChamber) bot.combat.prayers.activateIfOff(CombatPrayer.PROTECT_FROM_MAGIC)
        return inChamber && waitingForQueen()
    }

    /**
     * Retains the same NPC through its first death animation instead of treating it as a completed kill.
     */
    private fun waitingForQueen(): Boolean = focus?.let {
        it.id == 1158 && it.state == EntityState.ACTIVE && !it.isAlive
    } == true

    /** Clears pending manual spells and autocast so another script cannot inherit this session's spell. */
    private fun clearMagic() {
        bot.combat.magic.selectedSpell = CombatSpellDefinition.NONE
        bot.combat.magic.autocastSpell = CombatSpellDefinition.NONE
        bot.combat.magic.isAutocasting = false
        bot.combat.magic.refreshAutocast()
    }

    /** Validates actual equipment and one attack's supplies before permitting any attack click. */
    internal fun prepareCombat(): Boolean {
        if (!assignmentActive()) {
            bot.combat.target = null
            clearMagic()
            bot.reflex.isDisableCombatReflex = false
            stop()
            return false
        }
        val opponent = bot.combat.lastCombatWith
        if (slayerTask != null && bot.combat.inCombat() && opponent != null &&
            (opponent !is Npc || !acceptsAssignment(opponent))) {
            // Unrelated aggression belongs to the normal defensive reflex, not task target selection.
            bot.reflex.isDisableCombatReflex = false
            return false
        }
        if (bot.preferences.isSkiller) return false
        if (queenTrip && (forceBanking || !KalphiteQueenTrip.canContinue(bot))) {
            bot.combat.target = null
            forceBanking = true
            bot.reflex.isDisableCombatReflex = true
            return false
        }
        val ready = configureCombat()
        // A missing weapon, ammunition or spell cost sends us to a non-combat bank operation.
        // Let the existing combat reflex take over if we are attacked during that preparation.
        bot.reflex.isDisableCombatReflex = ready
        return ready
    }

    private fun configureCombat(): Boolean {
        if (requiredSlayerGear.any { (slot, id) -> bot.equipment[slot]?.id != id }) {
            combatConfigured = false
            bot.combat.target = null
            clearMagic()
            forceBanking = true
            return false
        }
        // Existing melee callers retain their click behavior before initialization.
        val selected = loadout ?: return purpose == BotGearPurpose.MELEE
        val weapon = selected.gear[Equipment.WEAPON]
        if (weapon == null && bot.equipment.weapon != null) {
            stop()
            return false
        }
        if (weapon != null && bot.equipment.weapon?.id != weapon) {
            combatConfigured = false
            clearMagic()
            forceBanking = true
            return false
        }
        if (purpose == BotGearPurpose.MAGIC) {
            if (!BotCombatActionHandler.mayGainExperience(bot, SKILL_MAGIC)) {
                bot.combat.target = null
                clearMagic()
                stop()
                return false
            }
            val definition = selected.spell ?: return false
            if (bot.spellbook != definition.spellbook || bot.magic.level < definition.level) {
                clearMagic()
                stop()
                return false
            }
            if (Magic.checkRequirements(bot, definition, true) == null) {
                combatConfigured = false
                clearMagic()
                forceBanking = true
                return false
            }
            if (!combatConfigured || !bot.combat.magic.isAutocasting || bot.combat.magic.autocastSpell != definition) {
                combatConfigured = handler.combat.setAutocastSpell(definition)
            }
            return combatConfigured
        }
        if (!combatConfigured) clearMagic()
        if (purpose == BotGearPurpose.RANGED) {
            val ammo = selected.gear[Equipment.AMMUNITION] ?: weapon
            if (ammo == null || bot.equipment.computeAmountForId(ammo) == 0) {
                forceBanking = true
                return false
            }
        }
        // Recheck after level changes as well as equipment changes.
        val allowed = BotCombatActionHandler.allowedStyles(bot, bot.combat.weapon.typeDef.styles)
        if (allowed.isEmpty()) {
            bot.combat.target = null
            stop()
            return false
        }
        if (!combatConfigured || bot.combat.weapon.styleDef !in allowed) {
            bot.combat.weapon.changeStyle(selectCombatStyle(allowed).button)
            combatConfigured = true
        }
        return true
    }

    private fun selectCombatStyle(styles: List<CombatStyleDefinition>): CombatStyleDefinition {
        if (queenTrip) {
            val weapon = bot.equipment.weapon?.id?.let(::equipDef)
            return styles.maxBy { weapon?.getBonus(it.bonus.index) ?: 0 }
        }
        if (purpose == BotGearPurpose.RANGED) {
            return styles.firstOrNull { Skill.RANGED in it.exp } ?: styles.first()
        }

        fun getDefaultStyle(): CombatStyleDefinition {
            return if (bot.personality.isIntelligent && randBoolean() && bot.strength.staticLevel < 99)
                styles.firstOrNull { it.exp.contains(Skill.STRENGTH) } ?: styles.random() else styles.random()
        }

        // Intelligent bots are more likely to use the controlled style.
        if (rand(bot.personality.intelligence) && randBoolean()) {
            val controlled = styles.firstOrNull { it.stance == CombatStance.CONTROLLED }
            if (controlled != null) {
                return controlled
            }
        }

        // Otherwise selection is based on current static levels if intelligent enough.
        if (bot.personality.isIntelligent || rand(bot.personality.intelligence)) {
            var (lowestSkill, level) = Pair(SKILL_ATTACK, bot.attack.staticLevel)
            if (level > bot.strength.staticLevel) {
                level = bot.strength.staticLevel
                lowestSkill = SKILL_STRENGTH
            }
            if (level > bot.defence.staticLevel) {
                lowestSkill = SKILL_DEFENCE
            }
            return styles.firstOrNull { it.exp.contains(lowestSkill) } ?: getDefaultStyle()
        }

        // Lastly, select the default style.
        return getDefaultStyle()
    }
}
