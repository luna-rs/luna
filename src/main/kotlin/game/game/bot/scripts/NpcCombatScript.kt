package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.Suspendable.naturalDelay
import api.bot.Suspendable.waitFor
import api.bot.script.TargetingZonedBotScript
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
 *
 * @property purpose The combat discipline retained throughout this session.
 * @property spell The elemental spell selected for a Magic session, preserved when saving.
 * @author lare96
 */
open class NpcCombatScript(bot: Bot,
                           duration: Duration,
                           zones: MutableList<SubZone>,
                           private val names: SetMultimap<SubZone, String> = ImmutableSetMultimap.of(),
                           val purpose: BotGearPurpose = BotGearPurpose.MELEE,
                           private var spell: CombatSpell? = null) :
    TargetingZonedBotScript<Npc>(bot, duration, zones) {

    override val actionSafety = ActionSafety.COMBAT

    // todo zone selection should happen BEFORE equipment/banking. so that we can prepare for the zone to travel to
    companion object {

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

            override fun load(data: JsonObject) {
                super.load(data)
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
                data.addProperty("purpose", purpose.name)
                spell?.let { data.addProperty("spell", it.name) }
                val savedNames = JsonObject()
                names.asMap().forEach { (zone, npcs) ->
                    savedNames.add(zone.name, GsonUtils.toJsonTree(npcs))
                }
                data.add("names", savedNames)
            }
        }

        // when looting sort by wanted item, etc etc value
        //  add all noted versions as well
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
                                                           (data as? NpcCombatData)?.spell)

    /** Selected owned gear; resumed sessions validate their actual equipment before attacking. */
    private var loadout: NpcCombatLoadout? = null
    /** Style selection happens after gear is equipped, and is retained until the next bank visit. */
    private var combatConfigured = false

    /**
     * Optional cannon supplies are selected once at each bank visit and never manufactured by this session.
     */
    private var cannonPrepared = false

    override suspend fun equipment(): BotGearLocator = BotGearLocator(bot, loadout!!.gear)

    override suspend fun onEquipmentFailed(equipmentLocator: BotGearLocator) {
        stop()
    }

    override suspend fun onInit(resumed: Boolean): Boolean {
        if (!CannonBotSupport.recover(bot)) return false
        if(bot.personality.type == PersonalityTemplateType.SKILLER) {
            // Skillers do not engage in combat.
            handler.combat.fleeCombat()
            return false
        }

        bot.reflex.isDisableCombatReflex = true
        bot.sendVarp(PersistentVarp.AUTO_RETALIATE, 1)

        loadout = NpcCombatLoadout.find(bot, purpose, spell) ?: return false
        if (SubZone.KBD_LAIR in originalZones) {
            val weapon = loadout!!.gear[Equipment.WEAPON] ?: return false
            if (bot.combatLevel < 100 || purpose != BotGearPurpose.MELEE ||
                equipDef(weapon).isTwoHanded || !bot.itemTracker.contains(1540)) return false
            loadout!!.gear[Equipment.SHIELD] = 1540
        }
        spell = loadout!!.spell?.spell
        if (resumed && !prepareCombat()) forceBanking = true
        return true
    }


    override suspend fun finish() {
        bot.reflex.isDisableCombatReflex = false
        // Keep the spell through a pause, so the combat reflex can finish the fight with it.
        if (!isPaused()) {
            clearMagic()
        }
        if (coroutineContext.isActive) CannonBotSupport.recover(bot)
    }

    override suspend fun beforeLeavingZone(): Boolean {
        bot.combat.target = null
        return CannonBotSupport.recover(bot)
    }

    override suspend fun find(searchBase: Position, searchRadius: Int): MutableCollection<Npc> {
        eatFood()
        if (!prepareCombat()) return mutableListOf()
        if (bot.combat.inCombat() && bot.combat.lastCombatWith is Npc) {
            return mutableListOf(bot.combat.lastCombatWith as Npc)
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
        if (!prepareCombat()) {
            stop()
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
        cannonPrepared = CannonBotSupport.prepare(bot, originalZones, purpose)
    }

    override suspend fun onNewActiveZone(lastZone: SubZone?) {
        if (activeZone in DRAGON_ZONES && 1540 !in bot.equipment &&
            (1540 !in bot.inventory || !handler.equipment.equip(1540) || bot.equipment.weapon == null)) {
            stop()
        }
    }
    // todo auto bury bones after each kill
    // todo use prayers, etc. when available and needed
    // todo dumb bots collect all loot, smart bots only collect good loot
    // todo safespotting (map of combat sub-zones and all known safespots??)
    //  ^^ SAFESPOT_MAGIC, SAFESPOT_RANGED, MAGIC, RANGED, MELEE
    //  safespot logic: select npc, within 10 squares of safespot, attack it, once in combat, immediately run to safespot and attack from there
    //  random chance to safespot based on intelligence (highest intelligence)

    // todo discard focus if not reachable.
    override suspend fun onExecuteInZone(searching: Boolean) {
        if (!prepareCombat()) return
        eatFood()
        if (bot.combat.lastCombatWith?.isAlive == true && bot.combat.inCombat() &&
            !bot.actions.contains(CombatAction::class.java)
        ) {
            handler.interactions.interact(3, bot.combat.lastCombatWith)
            bot.naturalDelay()
        } else if (focus?.isAlive != true) {
            lootItems()
        }
    }

    override suspend fun refocus(): Boolean {
        if (!prepareCombat()) return true
        eatFood()
        if (cannonPrepared && !forceBanking) {
            val zone = activeZone
            if (zone != null && !CannonBotSupport.maintain(bot, zone)) {
                forceBanking = true
                return true
            }
        }
        if (focus?.isAlive != true) {
            lootItems()
            return true
        }
        if (bot.combat.target == focus && bot.actions.contains(CombatAction::class.java)) {
            return false
        }
        if (!bot.combat.checkMultiCombat(focus)) {
            lastOptions.clear()
            if (bot.combat.lastCombatWith is Npc && bot.combat.inCombat()) {
                lastOptions += bot.combat.lastCombatWith as Npc
            } else {
                handler.combat.fleeCombat()
            }
            return true
        }
        // Retry the current target through the same click path as a human, and abandon it on failure.
        return !handler.interactions.interact(3, focus)
    }

    override suspend fun onAssignFocus(newFocus: Npc): Boolean {
        val allowed = bot.combat.checkMultiCombat(newFocus)
        if (!allowed) {
            bot.log("Rejected combat target id=${newFocus.id} at ${newFocus.position}: multi-combat check failed.")
        }
        return allowed
    }

    override val targetSearchTimeout: Duration = 180.seconds

    override suspend fun interactionOption(target: Npc): Int? {
        eatFood()
        return if (prepareCombat()) 3 else null
    }

    override fun snapshot(): NpcCombatData {
        val data = NpcCombatData()
        data.duration = duration
        data.zones = originalZones.toMutableList()
        data.names = ImmutableSetMultimap.copyOf(names)
        data.purpose = purpose
        data.spell = spell
        return data
    }

    // todo static set of valid npcs
    private fun canAttack(npc: Npc): Boolean {
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
        return item is DeathGroundItem || item.id in ALL_LOOT_ITEMS.value
    }

    private suspend fun lootItems() {
        eatFood()
        delay(2.seconds, 4.seconds)
        val groundItems = world.locator.findViewableItems(bot) { isLoot(it) && it.isVisibleTo(bot) }
        for (item in groundItems) {
            val inventoryItem = item.toItem()
            val countBefore = bot.itemTracker.count(inventoryItem.id)
            if (!bot.inventory.hasSpaceFor(inventoryItem)) {
                break
            }
            handler.interactions.interact(1, item)
            waitFor { countBefore < bot.itemTracker.count(inventoryItem.id) }
            eatFood()
            bot.naturalDelay()
        }
        for ((index, item) in bot.inventory.withIndex()) {
            if (activeZone != SubZone.KBD_LAIR && item != null && Bone.ID_TO_BONE.containsKey(item.id)) {
                handler.inventory.clickItem(1, item.id, index)
                break
            }
        }
    }

    private suspend fun eatFood() {
        if (activeZone == SubZone.KBD_LAIR &&
            (bot.equipment.shield?.id != 1540 || !handler.inventory.hasAnyFood())) {
            forceBanking = true
        }
        if (bot.emotions.isNervousAboutHp && !handler.inventory.eatAnyFood()) {
            forceBanking = true
        }
    }

    /** Clears pending manual spells and autocast so another script cannot inherit this session's spell. */
    private fun clearMagic() {
        bot.combat.magic.selectedSpell = CombatSpellDefinition.NONE
        bot.combat.magic.autocastSpell = CombatSpellDefinition.NONE
        bot.combat.magic.isAutocasting = false
        bot.combat.magic.refreshAutocast()
    }

    /** Validates actual equipment and one attack's supplies before permitting any attack click. */
    internal fun prepareCombat(): Boolean {
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
        if (!combatConfigured) {
            bot.combat.weapon.changeStyle(selectCombatStyle().button)
            combatConfigured = true
        }
        return true
    }

    private fun selectCombatStyle(): CombatStyleDefinition {

        val styles = bot.combat.weapon.typeDef.styles
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
