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
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotGearSelector
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.fishing.catchFish.Fish
import game.skill.herblore.identifyHerb.Herb
import game.skill.magic.Staff
import game.skill.mining.Ore
import game.skill.prayer.Bone
import game.skill.smithing.BarType
import game.skill.smithing.smithBar.SmithingTable
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.model.Position
import io.luna.game.model.def.CombatStyleDefinition
import io.luna.game.model.item.DeathGroundItem
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.CombatStance
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.path.PlayerPathfinder
import io.luna.net.msg.out.GameChatboxMessageWriter
import kotlinx.coroutines.future.await
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// TODO rename to fightnpcbotscript
class NpcCombatScript(bot: Bot,
                      duration: Duration,
                      zones: MutableList<SubZone>,
                      private val names: SetMultimap<SubZone, String> = ImmutableSetMultimap.of()) :
    TargetingZonedBotScript<Npc>(bot, duration, zones) {

    // todo zone selection should happen BEFORE equipment/banking. so that we can prepare for the zone to travel to
    companion object {

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

    constructor(bot: Bot, data: ZonedBotScriptData) : this(bot, data.duration, data.zones)

    override suspend fun equipment(): BotGearLocator {
        // Higher chance to train melee.
        // TODO Chance based on bot personality?
        // TODO Functions for magic and ranged combat (perfect melee first)
        return BotGearSelector.find(bot, setOf(BotGearPurpose.MELEE)).buildLocator()
    }

    override fun onInit(resumed: Boolean): Boolean {
        bot.reflex.isDisableCombatReflex = true
        bot.sendVarp(PersistentVarp.AUTO_RETALIATE, 1)

        bot.combat.weapon.changeStyle(selectCombatStyle().button)
        return true
    }


    override suspend fun finish() {
        bot.reflex.isDisableCombatReflex = false
    }

    override suspend fun find(searchBase: Position, searchRadius: Int): MutableCollection<Npc> {
        eatFood()
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
        // todo eat food, bury bones, drop junk, etc. then check again
        return true
    }

    override suspend fun onBankOpen(initial: Boolean) {
        if (!handler.inventory.hasAnyFood()) {
            // todo change minimum heal scaling to combat level
            handler.banking.withdrawAnyFood(amount = 8)
        }
    }

    override fun onNewActiveZone(lastZone: SubZone?) {
        if (lastZone != activeZone && activeZone == SubZone.GREEN_DRAGONS) {
            // todo ensure we have an anti-dragon shield
            // onBankOpen(true)
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
        if (bot.combat.weapon.isRanged) {
            val threshold = Instant.now().minusSeconds(15)
            if (bot.combat.lastAttackSent?.timestamp?.isBefore(threshold) ?: true) {
                val messages = bot.input.received[GameChatboxMessageWriter::class.java].reversed()
                for (inMsg in messages) {
                    val msg = inMsg.message
                    if (inMsg.timestamp.isAfter(threshold) && msg is GameChatboxMessageWriter) {
                        if (msg.message == "You have no arrows left in your quiver." ||
                            msg.message == "You can't use that type of ammunition with this weapon."
                        ) {
                            stop()
                            bot.log("No valid ammo left. Stopping script.")
                            return
                        }
                    }
                }
                bot.input.clear()
            }

        }

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

    override suspend fun interactionOption(target: Npc): Int? {
        eatFood()
        val reachable = bot.navigator.findPath(bot.position,
                                               target.position,
                                               PlayerPathfinder(bot.world.collisionManager, bot.z),
                                               true).await()?.peekLast()?.isWithinDistance(target.position, 2) == true
        if (!reachable) {
            return null
        }
        bot.combat.attack(target)
        bot.naturalDelay()
        return 3
    }

    override fun snapshot(): ZonedBotScriptData {
        val data = ZonedBotScriptData()
        data.duration = duration
        data.zones = originalZones.toMutableList()
        return data
    }

    // todo static set of valid npcs
    private val alwaysValid = setOf("Rock Crab")
    private fun canAttack(npc: Npc): Boolean {
        if (bot.combat.lastCombatWith == npc || npc.combat.target == bot || npc.def().name in alwaysValid) {
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

    private suspend fun lootItems() {
        eatFood()
        delay(2.seconds, 4.seconds)
        val groundItems = world.locator.findViewableItems(bot) {
            (it is DeathGroundItem || it.id in ALL_LOOT_ITEMS.value) && it.isVisibleTo(bot)
        }
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
            if (item != null && Bone.ID_TO_BONE.containsKey(item.id)) {
                handler.inventory.clickItem(1, item.id, index)
                break
            }
        }
    }

    private suspend fun eatFood() {
        if (bot.emotions.isNervousAboutHp && !handler.inventory.eatAnyFood()) {
            forceBanking = true
        }
    }

    // FINALIZED vv

    private fun selectCombatStyle(): CombatStyleDefinition {
        // todo Global bot "getstyle" method or "getValidSkills" function so we can do pures, zerkers, etc.
        // todo If need arrows but dont have, make the bot start a fletching script.
        // todo Magic and ranged.

        val styles = bot.combat.weapon.typeDef.styles

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