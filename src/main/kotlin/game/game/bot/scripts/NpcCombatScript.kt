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
import game.player.item.consume.food.Food
import game.player.item.consume.potion.Potion
import game.skill.slayer.Slayer.activeSlayerTask
import game.skill.slayer.SlayerMaster
import game.skill.slayer.SlayerTaskType
import game.bot.scripts.combat.NpcSafespotCombat
import game.combat.npcHooks.giantMole.GiantMole
import game.combat.npcHooks.zygomite.Zygomites
import game.combat.npcHooks.giantMole.GiantMole.burrowing
import engine.combat.prayer.CombatPrayer
import io.luna.game.model.EntityState
import game.skill.fishing.catchFish.Fish
import game.skill.herblore.identifyHerb.Herb
import game.skill.magic.Magic
import game.skill.magic.SpellCost
import game.skill.magic.Staff
import game.skill.mining.Ore
import game.skill.prayer.Bone
import game.skill.smithing.BarType
import game.skill.smithing.smithBar.SmithingTable
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.model.Position
import io.luna.game.model.def.CombatStyleDefinition
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.def.AmmoDefinition
import io.luna.game.model.def.WeaponDefinition
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
import io.luna.game.model.mob.combat.Weapon
import io.luna.game.model.mob.interact.InteractionPolicy
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
 * Dragon sessions require a one-handed melee loadout and an owned anti-dragon shield, equipped before travel.
 * Losing that protection blocks further attack clicks and requests banking. KBD additionally requires combat
 * level 100 and up to twenty food items healing at least sixteen Hitpoints. Bones collected in the lair
 * are retained for profit instead of buried.
 * Specialist assignments replace ordinary outfit slots with mandatory owned protection: earmuffs for banshees
 * and a nose peg for aberrant spectres. Protection is equipped before travel and rechecked before every attack,
 * including after banking and restored sessions.
 * Gargoyle visits also retain an owned rock hammer in inventory. Weakened stone forms use native adjacent
 * finishing reach, releasing any ranged or magic safespot without changing the saved combat discipline.
 * Zygomite visits pick the dormant fungi in place, retain owned charged sprays and refills, and use the native
 * adjacent item finish without replacing the NPC or its assignment binding.
 * Fremennik assignments retain owned mirror shields, salt and antipoison as needed. Turoth and kurask trips select
 * their native spear, broad-arrow or Magic Dart loadout before equipment is chosen, with no ordinary-ammo fallback.
 * Giant Mole sessions retain a spade and a sealed lit light, wait through the native burrow and reacquire its live
 * position across the full lair. Their banking uses the stronger food policy shared with other boss trips.
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
            SlayerTaskType.BANSHEE to mapOf(Equipment.HEAD to 4166),
            SlayerTaskType.ABERRANT_SPECTRE to mapOf(Equipment.HEAD to 4168),
            SlayerTaskType.COCKATRICE to mapOf(Equipment.SHIELD to 4156),
            SlayerTaskType.BASILISK to mapOf(Equipment.SHIELD to 4156)
        )

        /**
         * Returns mandatory equipment slots for a supported specialist assignment, or none for ordinary combat.
         */
        internal fun requiredSlayerEquipment(task: SlayerTaskType?): Map<Int, Int> =
            task?.let { SLAYER_EQUIPMENT[it] }.orEmpty()

        /**
         * Minimum carried tool quantities for native specialist finishes. Rock hammers are reusable, while salt is
         * replenished in owned batches and rechecked after every native consumption.
         */
        private val SLAYER_INVENTORY = mapOf(
            SlayerTaskType.GARGOYLE to mapOf(4162 to 1),
            SlayerTaskType.ROCKSLUG to mapOf(4161 to 1)
        )

        /**
         * Owned cure doses for poisonous cave crawlers, strongest first. Active immunity can finish its remaining
         * duration, but a fresh unprotected visit must carry a real dose before any attack is allowed.
         */
        private val SLAYER_ANTIPOISONS = listOf(Potion.ANTIDOTE_PLUS_PLUS, Potion.ANTIDOTE_PLUS,
            Potion.SUPER_ANTIPOISON, Potion.ANTIPOISON_POTION).flatMap { it.doses.reversed() }

        /**
         * These dungeon assignments use the supplied Fremennik camp and require their real selected weapon to be
         * owned and usable even when a caller supplies an explicit loadout for admission.
         */
        private val FREMENNIK_TASKS = setOf(SlayerTaskType.CAVE_CRAWLER, SlayerTaskType.ROCKSLUG,
            SlayerTaskType.COCKATRICE, SlayerTaskType.PYREFIEND, SlayerTaskType.BASILISK,
            SlayerTaskType.JELLY, SlayerTaskType.TUROTH, SlayerTaskType.KURASK)

        /**
         * Validates the shared assignment kit before factory dispatch and script initialization. Supplies are owned;
         * this check never grants items, cancels assignments or replaces a selected weapon to make a shield fit.
         */
        internal fun acceptsSlayerLoadout(bot: Bot, purpose: BotGearPurpose, task: SlayerTaskType?,
                                         loadout: CombatLoadout): Boolean {
            if (task == SlayerTaskType.ZYGOMITE && !ownsFungicide(bot)) return false
            if (requiredSlayerEquipment(task).any { (slot, id) -> loadout.gear[slot] != id ||
                    !bot.itemTracker.contains(id) || !equipDef(id).meetsAllRequirements(bot) }) return false
            if (requiredSlayerInventory(task).any { (id, amount) -> bot.itemTracker.count(id) < amount }) return false
            if (task == SlayerTaskType.CAVE_CRAWLER && !bot.status.isAntiPoison() &&
                SLAYER_ANTIPOISONS.none { bot.itemTracker.contains(it) }) return false
            val weapon = loadout.gear[Equipment.WEAPON]
            if (task in FREMENNIK_TASKS && (weapon == null || !bot.itemTracker.contains(weapon) ||
                    !equipDef(weapon).meetsAllRequirements(bot))) return false
            if (task in FREMENNIK_TASKS) {
                val definition = WeaponDefinition.ALL.get(weapon!!).orElse(null) ?: return false
                when (purpose) {
                    BotGearPurpose.MAGIC -> {
                        val cast = loadout.spell ?: return false
                        if (definition.type != Weapon.STAFF || bot.spellbook != cast.spellbook ||
                            !BotCombatActionHandler.mayGainExperience(bot, SKILL_MAGIC) ||
                            Magic.spellCost(bot.magic.level, bot.inventory + bot.bank + bot.equipment,
                                { it in loadout.gear }, weapon, cast.level, cast.required) !is SpellCost.Castable) return false
                    }
                    BotGearPurpose.RANGED -> {
                        val ammo = loadout.gear[Equipment.AMMUNITION] ?: weapon
                        if (!bot.itemTracker.contains(ammo) ||
                            (definition.type !in CombatLoadout.thrown &&
                                AmmoDefinition.ALL.values.none { ammo in it.ammo && weapon in it.weapons })) return false
                        if (BotCombatActionHandler.allowedStyles(bot, definition.typeDef.styles).isEmpty()) return false
                    }
                    BotGearPurpose.MELEE -> if (definition.type in CombatLoadout.thrown ||
                        definition.type in setOf(Weapon.SHORTBOW, Weapon.LONGBOW, Weapon.CROSSBOW) ||
                        BotCombatActionHandler.allowedStyles(bot, definition.typeDef.styles).isEmpty()) return false
                    else -> return false
                }
            }
            if (Equipment.SHIELD in requiredSlayerEquipment(task) &&
                (weapon == null || equipDef(weapon).isTwoHanded)) return false
            if (task !in setOf(SlayerTaskType.TUROTH, SlayerTaskType.KURASK)) return true
            return when (purpose) {
                BotGearPurpose.MELEE -> weapon == 4158
                BotGearPurpose.RANGED -> weapon != null && loadout.gear[Equipment.AMMUNITION] == 4160 &&
                    bot.itemTracker.contains(4160) && equipDef(4160).meetsAllRequirements(bot) &&
                    AmmoDefinition.ALL.values.any { 4160 in it.ammo && weapon in it.weapons }
                BotGearPurpose.MAGIC -> weapon == 4170 && loadout.spell?.spell == CombatSpell.MAGIC_DART &&
                    loadout.runes.all { (id, amount) -> bot.itemTracker.count(id) >= amount }
                else -> false
            }
        }

        /**
         * Selects a compatible owned assignment loadout through the ordinary gear resolver. Mirror shields restrict
         * weapons before scoring. Turoth and kurask sessions explicitly select their spear, broad arrows or native
         * Magic Dart staff and rune requirements. Salt demand is a standing stock target, independent of trip size.
         */
        internal fun slayerLoadout(bot: Bot, purpose: BotGearPurpose, task: SlayerTaskType?,
                                   requested: CombatSpell? = null): CombatLoadout? {
            if (task == SlayerTaskType.ZYGOMITE) {
                if (!ownsFungicide(bot)) bot.preferences.addWantedItem(Zygomites.FULL_SPRAY, 3)
                if (bot.itemTracker.count(Zygomites.FUNGICIDE) < 10)
                    bot.preferences.addWantedItem(Zygomites.FUNGICIDE, 100)
            }
            if (task == SlayerTaskType.ROCKSLUG && bot.itemTracker.count(4161) < 50) {
                bot.preferences.addWantedItem(4161, 500)
            }
            val restricted = task in setOf(SlayerTaskType.TUROTH, SlayerTaskType.KURASK)
            val dart = restricted && purpose == BotGearPurpose.MAGIC
            if (dart && requested != null && requested != CombatSpell.MAGIC_DART) return null
            val equipment = requiredSlayerEquipment(task) + if (dart) mapOf(Equipment.WEAPON to 4170) else emptyMap()
            val loadout = CombatLoadout.find(bot, purpose, if (dart) CombatSpell.MAGIC_DART else requested,
                requiredEquipment = equipment,
                requiredAmmo = 4160.takeIf { restricted && purpose == BotGearPurpose.RANGED },
                allowedWeapons = if (restricted && purpose == BotGearPurpose.MELEE) setOf(4158) else emptySet())
                ?: return null
            return loadout.takeIf { acceptsSlayerLoadout(bot, purpose, task, it) }
        }

        /**
         * A charged spray, or an empty spray and refill, must exist in accessible owned stock.
         */
        private fun ownsFungicide(bot: Bot): Boolean {
            val stock = bot.inventory + bot.bank
            return stock.any { it != null && Zygomites.charges(it.id) > 0 } ||
                stock.any { it?.id == Zygomites.EMPTY_SPRAY } && stock.any { it?.id == Zygomites.FUNGICIDE }
        }

        /**
         * Shares carried-tool prerequisites between factory admission and the live inventory lifecycle.
         */
        internal fun requiredSlayerInventory(task: SlayerTaskType?): Map<Int, Int> =
            task?.let { SLAYER_INVENTORY[it] }.orEmpty()

        /**
         * Camps that require an equipped anti-dragon shield before fighting.
         */
        val DRAGON_ZONES = setOf(
            SubZone.GREEN_DRAGONS,
            SubZone.TAVERLY_DUNGEON_BLUE_DRAGONS,
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
        (slayerTask == null || (Zygomites.FORMS[npc.id] ?: npc.id) in slayerTask.npcs)

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
     * Temporary positioning state is rebuilt after banking or restoration and never persisted as a trusted spot.
     */
    val safespotCombat = NpcSafespotCombat(bot) {
        isRunning() && !isPaused() && !forceBanking && activeZone != null
    }

    /**
     * Saved assignment metadata selects the same protection after restoration; equipped items are checked live.
     */
    private val requiredSlayerGear = requiredSlayerEquipment(slayerTask)

    /**
     * Native finishing tools must remain carried, including after inventory deposits or saved-session restoration.
     */
    private val requiredSlayerItems = requiredSlayerInventory(slayerTask)

    private val queenTrip: Boolean get() = SubZone.KALPHITE_QUEEN in originalZones

    /**
     * Mole outings keep their owned light and digging tool through every bank visit and restored session.
     */
    private val moleTrip: Boolean get() = SubZone.GIANT_MOLE_LAIR in originalZones

    /**
     * Prepares dragon protection for the entire candidate set, before a work zone is selected.
     */
    private val dragonTrip: Boolean get() = originalZones.any { it in DRAGON_ZONES }

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

        loadout = slayerLoadout(bot, purpose, slayerTask, spell) ?: return false
        if (queenTrip && (originalZones.size != 1 || purpose != BotGearPurpose.MELEE ||
                !KalphiteQueenTrip.levelsReady(bot) || !KalphiteQueenTrip.gearReady(loadout!!.gear) ||
                (!resumed && !KalphiteQueenTrip.ready(bot, loadout!!)))) return false
        if (moleTrip && (originalZones.size != 1 || !GiantMoleTrip.suppliesOwned(bot) ||
                (!resumed && !GiantMoleTrip.ready(bot)))) return false
        if (SubZone.KBD_LAIR in originalZones && bot.combatLevel < 100) return false
        if (dragonTrip) {
            val weapon = loadout!!.gear[Equipment.WEAPON] ?: return false
            if (purpose != BotGearPurpose.MELEE ||
                equipDef(weapon).isTwoHanded || !bot.itemTracker.contains(1540)) return false
            loadout!!.gear[Equipment.SHIELD] = 1540
        }
        spell = loadout!!.spell?.spell
        if (resumed && !configureCombat()) forceBanking = true
        return true
    }


    override suspend fun finish() {
        safespotCombat.release()
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
        safespotCombat.release()
        bot.combat.target = null
        if (!CannonBotSupport.recover(bot)) return false
        campBehavior.leave()
        return true
    }

    override suspend fun find(searchBase: Position, searchRadius: Int): MutableCollection<Npc> {
        attend()
        if (!prepareCombat()) return mutableListOf()
        if (bot.combat.inCombat() && bot.combat.lastCombatWith is Npc &&
            ((!queenTrip && !moleTrip) || canAttack(bot.combat.lastCombatWith as Npc))) {
            return (bot.combat.lastCombatWith as Npc).takeIf { acceptsAssignment(it) }
                ?.let { mutableListOf(it) } ?: mutableListOf()
        }
        val options = world.locator.findNpcs(searchBase, searchRadius, true) {
            canAttack(it) && (slayerTask == SlayerTaskType.ZYGOMITE && it.id in Zygomites.FORMS || it.combat.isAttackable)
                    && bot.combat.checkMultiCombat(it)
                    && !ctx.cache.mapIndexTable.getTile(it.position).isBlocked
            // todo incorporate checks for ranged/magic, this is for stuff like ducks that move on water
        }

        return options
    }

    override suspend fun onBankRequestedTargeting(initial: Boolean): Boolean {
        safespotCombat.release()
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

    /**
     * Dragon protection is withdrawn from owned stock rather than generated during banking.
     */
    private val antiDragonShield = item("Anti-dragon shield")
    override suspend fun onBankOpen(initial: Boolean) {

        if (dragonTrip && 1540 !in bot.equipment &&
            !handler.banking.withdraw(antiDragonShield)) {
            bot.preferences.addWantedItem(1540, 3)
            stop()
            return
        }
        val selected = loadout ?: return
        if (slayerTask == SlayerTaskType.ZYGOMITE && !collectFungicide()) {
            stop()
            return
        }
        if (moleTrip && !GiantMoleTrip.collect(bot)) {
            stop()
            return
        }
        for ((id, amount) in requiredSlayerItems) {
            val stock = if (slayerTask == SlayerTaskType.ROCKSLUG && id == 4161)
                minOf(50, assignment?.remaining ?: 1, bot.itemTracker.count(id)).coerceAtLeast(amount) else amount
            val missing = stock - bot.inventory.computeAmountForId(id)
            if (missing > 0 && (!handler.banking.withdraw(Item(id, missing)) ||
                    bot.inventory.computeAmountForId(id) < amount)) {
                if (id == 4161) bot.preferences.addWantedItem(id, 500)
                stop()
                return
            }
        }
        if (slayerTask == SlayerTaskType.CAVE_CRAWLER && !bot.status.isAntiPoison() &&
            SLAYER_ANTIPOISONS.none { bot.inventory.contains(it) }) {
            val cure = SLAYER_ANTIPOISONS.firstOrNull { bot.bank.contains(it) }
            if (cure == null || !handler.banking.withdraw(Item(cure)) || !bot.inventory.contains(cure)) {
                stop()
                return
            }
        }
        for ((slot, id) in requiredSlayerGear) {
            if (bot.equipment[slot]?.id != id && id !in bot.inventory && !handler.banking.withdraw(Item(id))) {
                stop()
                return
            }
        }
        val weapon = selected.gear[Equipment.WEAPON]
        val thrown = purpose == BotGearPurpose.RANGED && selected.gear[Equipment.AMMUNITION] == null
        if (weapon != null && bot.equipment.weapon?.id != weapon &&
            !handler.banking.withdraw(Item(weapon, if (thrown) 500 else 1))) {
            bot.preferences.addWantedItem(weapon, if (thrown) 1000 else 3)
            stop()
            return
        }
        val ammo = if (purpose == BotGearPurpose.RANGED) selected.gear[Equipment.AMMUNITION] ?: weapon else null
        if (purpose == BotGearPurpose.RANGED) {
            if (ammo == null || (bot.equipment.computeAmountForId(ammo) == 0 && ammo !in bot.inventory &&
                    !handler.banking.withdraw(Item(ammo, 500)))) {
                ammo?.let { bot.preferences.addWantedItem(it, 1000) }
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
        if (dragonTrip) handler.potions.withdrawAntifire()
        if (queenTrip) {
            for ((id, amount) in KalphiteQueenTrip.supplies) {
                if (!handler.banking.withdraw(Item(id, amount)) || bot.inventory.computeAmountForId(id) < amount) {
                    bot.preferences.addWantedItem(id, amount)
                    stop()
                    return
                }
            }
        } else {
            val strongFood = SubZone.KBD_LAIR in originalZones || moleTrip
            if (strongFood) {
                val carried = strongFoodCount()
                if (carried < 20 && (!handler.banking.withdrawAnyFood(amount = 20 - carried,
                        minimumHeal = 16, retry = false) || strongFoodCount() < 20)) {
                    stop()
                    return
                }
            } else if (!handler.inventory.hasAnyFood()) {
                handler.banking.withdrawAnyFood(amount = restockFood())
            }
            if (!handler.inventory.hasAnyFood() && tactics.needsFood()) {
                // Without food it would turn back for the bank as soon as it arrived.
                bot.log("No food in the bank and too hurt to fight without it. Ending this session.")
                stop()
                return
            }
            cannonPrepared = slayerTask == null && CannonBotSupport.prepare(bot, originalZones, purpose)
        }
        if (!wieldAtBank(weapon, ammo, thrown) ||
            (slayerTask == SlayerTaskType.ZYGOMITE && !refillFungicide()) || !configureCombat()) {
            stop()
            return
        }
        if (queenTrip && !KalphiteQueenTrip.equipped(bot)) stop()
    }

    /**
     * Food for one trip: what the bot keeps back for getting away, plus some to eat while fighting. A fixed amount can
     * be all reserve, which sends the bot back to the bank as soon as it arrives.
     */
    private fun restockFood(): Int {
        val heal = bot.bank.firstNotNullOfOrNull { it?.let { item -> Food.ID_TO_FOOD[item.id]?.heal } } ?: 0
        return tactics.foodToCarry(heal, fighting = 8)
    }

    /**
     * Counts physically carried food meeting the shared boss-trip healing threshold; withdrawal success alone may
     * represent a partial transfer and cannot prove that the required reserve arrived.
     */
    private fun strongFoodCount(): Int = bot.inventory.filterNotNull()
        .filter { (Food.ID_TO_FOOD[it.id]?.heal ?: 0) >= 16 }.sumOf { it.amount }

    /**
     * Wields the session's weapon, ammunition, task protection and dragon shield after everything is withdrawn: an
     * equip click closes the bank, so a player closes it first and wields after.
     *
     * @return `false` if something couldn't be wielded, or the shield took the place of a two-handed weapon.
     */
    private suspend fun wieldAtBank(weapon: Int?, ammo: Int?, thrown: Boolean): Boolean {
        val shield = dragonTrip && bot.equipment.shield?.id != 1540
        val wield = listOfNotNull(weapon?.takeIf { bot.equipment.weapon?.id != it },
                                  ammo?.takeIf { it != weapon && bot.equipment.computeAmountForId(it) == 0 }) +
                requiredSlayerGear.filter { (slot, id) -> bot.equipment[slot]?.id != id }.values +
                listOfNotNull(1540.takeIf { shield })
        val needsLight = moleTrip && !GiantMoleTrip.carried(bot)
        if (wield.isEmpty() && !needsLight) return true
        if (!handler.widgets.clickCloseInterface()) return false
        for (id in wield) {
            if (!handler.equipment.equip(id)) {
                if (id == weapon) bot.preferences.addWantedItem(id, if (thrown) 1000 else 3)
                if (id == ammo) bot.preferences.addWantedItem(id, 1000)
                return false
            }
        }
        if (moleTrip && !GiantMoleTrip.ensureLit(bot)) return false
        return !shield || bot.equipment.weapon != null
    }

    override suspend fun onNewActiveZone(lastZone: SubZone?) {
        safespotCombat.release()
        if (activeZone in DRAGON_ZONES && 1540 !in bot.equipment &&
            (1540 !in bot.inventory || !handler.equipment.equip(1540) || bot.equipment.weapon == null)) {
            stop()
        }
    }
    // todo auto bury bones after each kill
    // todo use potions, etc. when available and needed
    // todo dumb bots collect all loot, smart bots only collect good loot

    // todo discard focus if not reachable.
    override suspend fun onExecuteInZone(searching: Boolean) {
        if (!prepareCombat()) return
        attend()
        if (forceBanking || (queenTrip && waitingForQueen()) || waitingForMole()) return
        if (bot.combat.lastCombatWith?.isAlive == true &&
            ((!queenTrip && !moleTrip) || bot.combat.lastCombatWith?.let { it is Npc && canAttack(it) } == true) && bot.combat.inCombat() &&
            !bot.actions.contains(CombatAction::class.java)
        ) {
            val opponent = bot.combat.lastCombatWith
            if (opponent is Npc && acceptsAssignment(opponent)) {
                interactWithTarget(3, opponent)
            } else if (slayerTask == null) {
                handler.interactions.interact(3, opponent)
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

    /**
     * Keeps the known boss focus through its short native underground phase, then follows its updated position.
     */
    private fun waitingForMole(): Boolean = moleTrip && focus?.let {
        it.id == GiantMole.ID && it.burrowing
    } == true

    /**
     * Withdraws an owned spray and a small trip's refill stock; no finishing consumable is manufactured.
     */
    private suspend fun collectFungicide(): Boolean {
        if (Zygomites.carriedSpray(bot) == null && !bot.inventory.contains(Zygomites.EMPTY_SPRAY)) {
            val spray = (Zygomites.FULL_SPRAY..Zygomites.EMPTY_SPRAY).firstOrNull { bot.bank.contains(it) }
                ?: return false
            if (!handler.banking.withdraw(Item(spray)) || !bot.inventory.contains(spray)) return false
        }
        val refills = minOf(3, ((assignment?.remaining ?: 10) + 9) / 10,
            bot.itemTracker.count(Zygomites.FUNGICIDE))
        val missing = refills - bot.inventory.computeAmountForId(Zygomites.FUNGICIDE)
        if (missing > 0 && !handler.banking.withdraw(Item(Zygomites.FUNGICIDE, missing))) return false
        return Zygomites.carriedSpray(bot) != null ||
            bot.inventory.contains(Zygomites.EMPTY_SPRAY) && bot.inventory.contains(Zygomites.FUNGICIDE)
    }

    /**
     * Refills a real empty spray through the native item-on-item packet and checks its new charge state.
     */
    private suspend fun refillFungicide(): Boolean {
        if (Zygomites.carriedSpray(bot) != null) return true
        if (!bot.inventory.contains(Zygomites.EMPTY_SPRAY) || !bot.inventory.contains(Zygomites.FUNGICIDE) ||
            !handler.inventory.useItem(Zygomites.FUNGICIDE).onItem(Zygomites.EMPTY_SPRAY)) return false
        return waitFor(3.seconds) { Zygomites.carriedSpray(bot) != null }
    }

    override suspend fun onPrepareTargeting(): Boolean {
        if (slayerTask == SlayerTaskType.ZYGOMITE && !refillFungicide()) {
            forceBanking = true
            bot.combat.target = null
            return false
        }
        return true
    }

    override suspend fun refocus(): Boolean {
        if (!prepareCombat()) {
            safespotCombat.release()
            return true
        }
        attend()
        if (forceBanking) safespotCombat.release()
        if (queenTrip && (forceBanking || waitingForQueen())) return false
        if (waitingForMole()) {
            safespotCombat.release()
            return false
        }
        if (cannonPrepared && !forceBanking && !safespotCombat.isHolding) {
            val zone = activeZone
            if (zone != null && !CannonBotSupport.maintain(bot, zone)) {
                forceBanking = true
                return true
            }
        }
        val weakened = focus
        if (weakened != null && weakened.isAlive && weakened.id in Zygomites.FORMS.values &&
            weakened.health <= Zygomites.FINISH_HEALTH) {
            safespotCombat.release()
            bot.combat.target = null
            return !interactWithTarget(3, weakened)
        }
        if (focus?.isAlive != true || !acceptsAssignment(focus!!)) {
            safespotCombat.release()
            lootItems()
            return true
        }
        if (needsFinishingReach(focus)) safespotCombat.release()
        if (safespotCombat.isHolding && !safespotCombat.maintain(focus, activeZone) {
                isActionSafe() && !forceBanking && acceptsAssignment(focus!!) && prepareCombat() &&
                    bot.combat.checkMultiCombat(focus) && (!socialCamp || campBehavior.acceptsTarget(focus!!))
            }) return true
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
        if ((queenTrip || moleTrip) && !canAttack(newFocus)) return false
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
        return if (prepareCombat() && acceptsAssignment(target))
            if (target.id in Zygomites.FORMS) 1 else 3 else null
    }

    override fun targetApproachPolicy(target: Npc): InteractionPolicy =
        if (needsFinishingReach(target)) InteractionPolicy.STANDARD_SIZE
        else if (purpose == BotGearPurpose.RANGED || purpose == BotGearPurpose.MAGIC)
            bot.combat.getDefaultAttack(target).interactionPolicy else InteractionPolicy.STANDARD_SIZE

    /**
     * Native activation and finishing items require adjacent reach regardless of the saved combat discipline.
     */
    private fun needsFinishingReach(target: Npc?): Boolean = target?.let {
        it.id == 1611 || it.id in Zygomites.FORMS ||
            it.id in Zygomites.FORMS.values && it.health <= Zygomites.FINISH_HEALTH
    } == true

    override suspend fun interactWithTarget(option: Int, target: Npc): Boolean {
        val expectedId = target.id
        val active = Zygomites.FORMS[expectedId]
        val life = target.spawnCount
        val nativeInteraction = active != null ||
            expectedId in Zygomites.FORMS.values && target.health <= Zygomites.FINISH_HEALTH
        val allowed = {
            isActionSafe() && !forceBanking && acceptsAssignment(target) && prepareCombat() &&
                (!moleTrip || canAttack(target)) &&
                bot.combat.checkMultiCombat(target) && (!socialCamp || campBehavior.acceptsTarget(target)) &&
                (!nativeInteraction || target.spawnCount == life &&
                    (target.id == expectedId || active != null && target.id == active && target.combat.target === bot))
        }
        if (nativeInteraction) {
            safespotCombat.release()
            if (target.combat.isExecutionPending) return true
            if (!handler.interactions.approach(target, InteractionPolicy.STANDARD_SIZE,
                    canContinue = allowed) || !allowed()) return false
            if (active != null && target.id == active) return true
            val sent = if (active != null) {
                handler.interactions.interact(1, target, InteractionPolicy.STANDARD_SIZE, allowed)
            } else {
                val spray = Zygomites.carriedSpray(bot) ?: return false
                val index = bot.inventory.computeIndexForId(spray)
                if (!allowed() || index < 0) return false
                bot.output.useItemOnNpc(index, spray, target)
                true
            }
            val completed = sent && waitFor(6.seconds) {
                !assignmentActive() || target.spawnCount != life || !target.isAlive ||
                    (active != null && target.id == active) || target.combat.isExecutionPending
            }
            return completed && assignmentActive() && target.spawnCount == life &&
                (active == null || target.id == active && target.combat.target === bot)
        }
        if (option == 3 && needsFinishingReach(target)) {
            safespotCombat.release()
            return handler.interactions.interact(option, target, InteractionPolicy.STANDARD_SIZE, allowed)
        }
        val zone = activeZone
        if (option == 3 && !queenTrip && zone != null &&
            purpose in setOf(BotGearPurpose.RANGED, BotGearPurpose.MAGIC)) {
            safespotCombat.engage(target, zone, allowed)?.let { return it }
        }
        if (slayerTask == null && !socialCamp && !moleTrip) return super.interactWithTarget(option, target)
        return handler.interactions.interact(option, target, allowed)
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
        if (npc.id in Zygomites.FORMS) return slayerTask == SlayerTaskType.ZYGOMITE
        if (queenTrip) return npc.id in setOf(1158, 1160) && KalphiteQueenTrip.canContinue(bot)
        if (moleTrip) return npc.id == GiantMole.ID && !npc.burrowing
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
        safespotCombat.release()
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
        if (forceBanking || !bot.isAlive || !isRunning()) safespotCombat.release()
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

    /**
     * Validates actual equipment, dragon protection and one attack's supplies before permitting attack clicks.
     */
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
        if (activeZone in DRAGON_ZONES && (purpose != BotGearPurpose.MELEE ||
                bot.equipment.shield?.id != 1540 || bot.equipment.weapon?.let {
                    equipDef(it.id).isTwoHanded
                } != false)) {
            bot.combat.target = null
            clearMagic()
            forceBanking = true
            bot.reflex.isDisableCombatReflex = false
            return false
        }
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
        if ((moleTrip && !GiantMoleTrip.carried(bot)) ||
            requiredSlayerGear.any { (slot, id) -> bot.equipment[slot]?.id != id } ||
            requiredSlayerItems.any { (id, amount) -> bot.inventory.computeAmountForId(id) < amount } ||
            (slayerTask == SlayerTaskType.ZYGOMITE && Zygomites.carriedSpray(bot) == null) ||
            (slayerTask == SlayerTaskType.CAVE_CRAWLER && !bot.status.isAntiPoison() &&
                SLAYER_ANTIPOISONS.none { bot.inventory.contains(it) })) {
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
