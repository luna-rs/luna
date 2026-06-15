package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.Suspendable.naturalDelay
import api.bot.Suspendable.naturalMicroDelay
import api.bot.script.TargetingZonedBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.*
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotGearSelector
import game.skill.fishing.Fish
import game.skill.herblore.identifyHerb.Herb
import game.skill.magic.Staff
import game.skill.mining.Ore
import game.skill.prayer.Bone
import game.skill.smithing.BarType
import game.skill.smithing.smithBar.SmithingTable
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.model.Position
import io.luna.game.model.item.DeathGroundItem
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.future.await
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class CombatTrainingScript(bot: Bot, duration: Duration, zones: MutableList<SubZone>) :
    TargetingZonedBotScript<Npc>(bot, duration, zones) {

    companion object {

        // when looting sort by wanted item, etc etc value
        //  add all noted versions as well
        val ALL_LOOT_ITEMS = run { // and if cache value > 10_000
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

            /* TODO Add all gear set items. Loot items should also be wanted items. split this up into RESOURCES, EQUIPMENT, etc. and add it in BotSupplies class
            for (type in GearSetType.values()) {
                items += world.botManager.personalityManager.getGearSet(type).items
            }*/
            // high level
        }

    }

    constructor(bot: Bot, data: ZonedBotScriptData) : this(bot, data.duration, data.zones)

    override suspend fun equipment(): BotGearLocator {
        // Higher chance to train melee.
        // TODO Chance based on bot personality?
        // TODO Functions for magic and ranged combat (perfect melee first)
        val purposes =
            when (rand(4)) {
           //     0 -> setOf(BotGearPurpose.MAGIC)
             //   1 -> setOf(BotGearPurpose.RANGED)
                else -> setOf(BotGearPurpose.MELEE)
            }
        return BotGearSelector.find(bot, purposes).buildLocator()
    }

    override fun onInit(resumed: Boolean): Boolean {
        bot.reflex.isDisableCombatReflex = true
        handler.widgets.clickAutoRetaliate(true)
        val randomStyle = bot.combat.weapon.typeDef.styles.random()
        bot.combat.weapon.changeStyle(randomStyle.button)
        return true
    }

    override suspend fun finish() {
        bot.reflex.isDisableCombatReflex = false
    }

    override fun find(searchBase: Position, searchRadius: Int): MutableCollection<Npc> {
        if (bot.combat.inCombat() && bot.combat.lastCombatWith is Npc) {
            return mutableListOf(bot.combat.lastCombatWith as Npc)
        }
        return world.locator.findNpcs(searchBase, searchRadius, true) {
            canAttack(it) && it.combat.isAttackable
                    && bot.combat.checkMultiCombat(it)
                    && !ctx.cache.mapIndexTable.getTile(it.position).isBlocked // todo incorporate checks for ranged/magic, this is for stuff like ducks that move on water
        }
    }

    override suspend fun onBankRequestedTargeting(initial: Boolean): Boolean {
        // todo eat food, bury bones, drop junk, etc. then check again
        return true
    }

    override suspend fun onBankOpen(initial: Boolean) {
        if (!handler.inventory.hasAnyFood()) {
            // todo change minimum heal scaling to combat level
            handler.banking.withdrawAnyFood(amount = 8)
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
        eatFood()
        if (bot.combat.lastCombatWith != null && bot.combat.inCombat()) {
            handler.interactions.interact(3, bot.combat.lastCombatWith)
            bot.naturalDelay()
        } else if (focus?.isAlive != true) {
            lootItems()
        }
    }

    override suspend fun refocus(): Boolean {
        eatFood()
        if (focus?.isAlive != true || bot.combat.lastCombatWith == null) {
            lootItems()
            return true
        }
        if (bot.combat.inCombat() && bot.combat.target == focus && focus?.isAlive == true) {
            return false
        }
        if (!bot.combat.checkMultiCombat(focus)) {
            lastOptions.clear()
            if (bot.combat.lastCombatWith is Npc && bot.combat.inCombat()) {
                lastOptions += bot.combat.lastCombatWith as Npc
            } else {
                handler.combat.fleeCombat()
            }
        }
        if (bot.walking.isEmpty && !bot.combat.inCombat()) {
            bot.navigator.navigate(activeZone!!.inside, true)
                .await() // Temporary, pathfinding sucks sometimes bots get stuck
            return true
        }
        return false
    }

    override suspend fun onAssignFocus(newFocus: Npc): Boolean {
        return bot.combat.checkMultiCombat(newFocus)
    }

    override fun interactionOption(target: Npc): Int = 3

    override fun snapshot(): ZonedBotScriptData {
        val data = ZonedBotScriptData()
        data.duration = duration
        data.zones = originalZones.toMutableList()
        return data
    }

    private fun canAttack(npc: Npc): Boolean {
        return npc.combatLevel < 15 || npc.combatLevel < bot.combatLevel
    }

    private suspend fun lootItems() {
        delay(2.seconds, 4.seconds)
        val groundItems = world.locator.findViewableItems(bot) { it is DeathGroundItem && it.isVisibleTo(bot) }
        for (item in groundItems) {
            handler.interactions.interact(1, item)
            bot.naturalMicroDelay()
        }
        for ((index, item) in bot.inventory.withIndex()) {
            if (item != null && Bone.ID_TO_BONE.containsKey(item.id)) {
                handler.inventory.clickItem(1, item.id, index)
                break
            }
        }
    }

    private fun eatFood() {
        if (bot.emotions.isNervousAboutHp && !handler.inventory.eatAnyFood()) {
            forceBanking = true
        }
    }
}