package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.Suspendable.waitFor
import api.bot.action.BotCombatActionHandler
import api.bot.action.BotNpcDialogues
import api.bot.action.CombatLoadout
import api.bot.boss.BossFood
import api.bot.script.BotScript
import api.bot.webwalk.generate.EntranceStands
import api.bot.webwalk.plan.BankAccess
import api.bot.webwalk.walk.WebWalker
import api.predef.attack
import api.predef.defence
import api.predef.magic
import api.predef.ranged
import api.predef.strength
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import engine.combat.prayer.CombatPrayer
import engine.obj.SpecialLadder
import game.content.championsChallenge.Champion
import game.content.championsChallenge.ChampionRule
import game.content.championsChallenge.ChampionsChallenge as Challenge
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.def.NpcCombatDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Uses an owned challenge scroll or Leon's invitation, equips for the arena rule, and fights through native
 * interactions. Accepted challenges and rewards remain on the character when a trip is interrupted.
 *
 * @author lare96
 */
class ChampionsChallengeBotScript private constructor(bot: Bot, private val trip: Trip?) : BotScript(bot) {
    companion object {
        private val gallery = Position(3189, 9758)
        private val statue = Position(3184, 9758, 1)
        private val protection = CombatPrayer.PROTECT_FROM_MELEE
        private var Bot.championsRetryAt by Attr.long().persist("champions_challenge_bot_retry_at")

        /**
         * A scroll that the bot can still use, including scrolls with no trade price.
         */
        fun wantsScroll(bot: Bot, id: Int): Boolean {
            val champion = Champion.SCROLLS[id] ?: return false
            return !Challenge.defeated(bot, champion) && !bot.itemTracker.contains(id)
        }

        /**
         * Prioritizes the accepted challenge, then an earned invitation or an owned scroll with sufficient supplies.
         */
        fun select(bot: Bot): ChampionsChallengeBotScript? {
            if (!bot.isAlive || bot.isLocked || System.currentTimeMillis() < bot.championsRetryAt) return null
            if (Challenge.lowerRooms(bot.position)) return ChampionsChallengeBotScript(bot, null)
            if (bot.combat.inCombat() || bot.overlays.hasWindow() || bot.preferences.isSkiller ||
                bot.controllers.primary.javaClass != PlayerController::class.java) return null
            val accepted = Challenge.challenge(bot)
            val candidates = when {
                accepted != null -> listOf(accepted)
                Challenge.leonWaiting(bot) -> listOf(Champion.LEON)
                else -> Champion.LOWER
            }
            val owned = candidates.filter { !Challenge.defeated(bot, it) &&
                (it == Champion.LEON || bot.itemTracker.contains(it.scroll)) }
            return owned.firstNotNullOfOrNull { plan(bot, it) }?.let { ChampionsChallengeBotScript(bot, it) }
        }

        private fun plan(bot: Bot, champion: Champion): Trip? {
            val opponent = NpcCombatDefinition.ALL.get(champion.npc).orElse(null) ?: return null
            val healing = when (champion.rule) {
                ChampionRule.WEAPON_ONLY, ChampionRule.EMPTY_INVENTORY -> 0
                ChampionRule.UNARMED -> 240
                else -> maxOf(80, opponent.hitpoints)
            }
            val food = BossFood.Policy(minimumBiteHealing = 8, requiredHealing = healing)
            val minimumLevel = when (champion.rule) {
                ChampionRule.UNARMED -> 75
                ChampionRule.WEAPON_ONLY, ChampionRule.EMPTY_INVENTORY -> 65
                else -> if (opponent.hitpoints <= 40) 25 else 45
            }
            if (bot.totalHealth < maxOf(minimumLevel, opponent.maximumHit * 3) ||
                bot.health < bot.totalHealth * 3 / 4 || bot.defence.staticLevel < minimumLevel) return null
            if (healing > 0 && BossFood.plan(BossFood.stock(bot.inventory, bot.bank), food) == null) return null

            val purposes = when (champion.rule) {
                ChampionRule.MAGIC_ONLY -> listOf(BotGearPurpose.MAGIC)
                ChampionRule.RANGED_ONLY -> listOf(BotGearPurpose.RANGED)
                ChampionRule.NO_MELEE -> listOf(BotGearPurpose.RANGED, BotGearPurpose.MAGIC)
                else -> listOf(BotGearPurpose.MELEE)
            }
            val attacks = maxOf(60, opponent.hitpoints)
            for (purpose in purposes) {
                val level = when (purpose) {
                    BotGearPurpose.MAGIC -> bot.magic.level
                    BotGearPurpose.RANGED -> bot.ranged.level
                    else -> minOf(bot.attack.level, bot.strength.level)
                }
                if (level < minimumLevel) continue
                val outfit = CombatLoadout.find(bot, purpose, minimumCasts = attacks) ?: continue
                val gear = outfit.gear.copyOf()
                val weapon = gear[Equipment.WEAPON]
                when (champion.rule) {
                    ChampionRule.UNARMED -> gear.fill(null)
                    ChampionRule.WEAPON_ONLY -> gear.indices.filter { it != Equipment.WEAPON }.forEach { gear[it] = null }
                    else -> Unit
                }
                if (weapon == null && champion.rule != ChampionRule.UNARMED) continue
                val ammo = if (purpose == BotGearPurpose.RANGED) gear[Equipment.AMMUNITION] ?: weapon else null
                if (ammo != null && bot.itemTracker.count(ammo) < attacks) continue
                return Trip(champion, outfit.copy(gear = gear), ammo?.let { Item(it, attacks) },
                    outfit.runes.map { (id, amount) -> Item(id, amount * attacks) }, food, opponent.maximumHit)
            }
            return null
        }
    }

    /**
     * Owned equipment and consumables for a single native encounter.
     *
     * @author lare96
     */
    private data class Trip(val champion: Champion, val outfit: CombatLoadout, val ammo: Item?,
                            val runes: List<Item>, val food: BossFood.Policy, val maxHit: Int) {
        val scroll = champion.scroll.takeIf { it >= 0 }
        val stripsGear = champion.rule == ChampionRule.WEAPON_ONLY || champion.rule == ChampionRule.UNARMED
        val foodAllowed = food.requiredHealing > 0
    }

    private var enabledProtection = false
    override val actionSafety = ActionSafety.COMBAT
    override val allowsCombatResponse = false
    override fun allowsLoot(item: GroundItem): Boolean = false
    override fun snapshot() = null

    override suspend fun finish() {
        if (enabledProtection) bot.combat.prayers.deactivate(protection, false)
        enabledProtection = false
    }

    override suspend fun run(): Boolean {
        if (!leaveArena()) {
            defer()
            return true
        }
        val plan = trip ?: return true
        if (Challenge.defeated(bot, plan.champion)) return true
        var step = "prepare the outfit"
        val won = withTimeoutOrNull(8.minutes) {
            if (!prepare(plan) || !configure(plan)) return@withTimeoutOrNull false
            step = "reach the guild gallery"
            if (!WebWalker.webWalk(bot, gallery, attempts = 2, bank = BankAccess.NONE, fallback = false).success)
                return@withTimeoutOrNull false
            step = "accept Larxus's challenge"
            if (!accept(plan)) return@withTimeoutOrNull false
            step = "descend through the statue"
            if (!descend()) return@withTimeoutOrNull false
            step = "enter the arena"
            if (!Challenge.allowed(bot, plan.champion) || !crossPortcullis(entering = true))
                return@withTimeoutOrNull false
            step = "win the fight"
            fight(plan)
        } == true
        if (won) bot.overlays.closeWindows() else bot.log("Champions' Challenge: could not $step at ${bot.position}.")
        val left = leaveArena()
        if (!won || !left) defer()
        return true
    }

    private fun defer() {
        bot.championsRetryAt = System.currentTimeMillis() + 30.minutes.inWholeMilliseconds
    }

    private fun live() = bot.state == EntityState.ACTIVE && bot.isAlive

    private fun ready(plan: Trip): Boolean =
        (plan.scroll == null || bot.inventory.contains(plan.scroll)) && Challenge.allowed(bot, plan.champion) &&
            plan.outfit.gear.withIndex().all { (slot, id) -> id == null || bot.equipment[slot]?.id == id } &&
            (plan.ammo?.let { bot.equipment.computeAmountForId(it.id) >= it.amount } != false) &&
            bot.inventory.containsAll(plan.runes) &&
            (!plan.foodAllowed || BossFood.carriedHealing(bot, plan.food) >= plan.food.requiredHealing)

    private suspend fun prepare(plan: Trip): Boolean {
        if (ready(plan)) return true
        if (!handler.banking.travelToBankOpen()) return false
        val keep = plan.scroll?.takeIf { plan.foodAllowed || bot.inventory.computeAmountForId(it) <= 1 }
        val retained = setOfNotNull(keep)
        try {
            if (!handler.banking.depositInventory(retained)) return false
            if (plan.stripsGear) {
                for (slot in plan.outfit.gear.indices) {
                    if (bot.equipment[slot]?.id != plan.outfit.gear[slot] && !handler.equipment.unequip(slot)) return false
                }
                if (!handler.banking.depositInventory(retained)) return false
            }
            if (plan.scroll != null && !bot.inventory.contains(plan.scroll) &&
                !handler.banking.withdrawUnnoted(Item(plan.scroll))) return false
            for (item in plan.runes) {
                val missing = item.amount - bot.inventory.computeAmountForId(item.id)
                if (missing > 0 && !handler.banking.withdrawUnnoted(Item(item.id, missing))) return false
            }
            if (plan.foodAllowed && !BossFood.withdraw(bot, plan.food)) return false
            val wanted = plan.outfit.gear.withIndex().mapNotNull { (slot, id) ->
                id?.let { plan.ammo?.takeIf { supply -> supply.id == id } ?: Item(it) }
                    ?.takeUnless { bot.equipment[slot]?.id == id &&
                        (plan.ammo?.id != id || bot.equipment.computeAmountForId(id) >= plan.ammo.amount) }
            }
            for (item in wanted) {
                val owned = if (item.id == plan.ammo?.id) bot.equipment.computeAmountForId(item.id) else 0
                if (!handler.banking.withdrawUnnoted(Item(item.id, item.amount - owned))) return false
            }
        } finally {
            if (bot.bank.isOpen) bot.overlays.closeWindows()
        }
        for (id in plan.outfit.gear.filterNotNull()) {
            if (bot.inventory.contains(id) && !handler.equipment.equip(id)) return false
        }
        return ready(plan)
    }

    private fun configure(plan: Trip): Boolean {
        val magic = bot.combat.magic
        magic.selectedSpell = CombatSpellDefinition.NONE
        magic.autocastSpell = CombatSpellDefinition.NONE
        magic.isAutocasting = false
        if (plan.champion.rule == ChampionRule.NO_PRAYER) bot.combat.prayers.deactivateAll()
        if (plan.champion.rule == ChampionRule.NO_SPECIAL) bot.combat.specialBar.forceOff()
        val spell = plan.outfit.spell
        if (spell != null) return handler.combat.setAutocastSpell(spell) ||
            handler.combat.selectAndUseSpell(spell.spell)
        val style = BotCombatActionHandler.allowedStyles(bot, bot.combat.weapon.typeDef.styles).firstOrNull() ?: return false
        if (bot.combat.weapon.styleDef != style) bot.combat.weapon.changeStyle(style.button)
        return true
    }

    private suspend fun accept(plan: Trip): Boolean {
        repeat(3) {
            if (!live()) return false
            if (Challenge.challenge(bot) == plan.champion) return waitFor(15.seconds) { !bot.overlays.hasWindow() }
            val larxus = bot.world.locator.findNpcs(gallery, 10) { it.id == Challenge.LARXUS && it.isAlive }
                .firstOrNull() ?: return false
            val accepted = BotNpcDialogues.respond(bot, larxus, listOf(Challenge.ACCEPT_OPTION),
                completed = { Challenge.challenge(bot) == plan.champion }) {
                val policy = InteractionPolicy.STANDARD_SIZE
                if (!handler.interactions.approachLocally(larxus, policy) &&
                    !handler.interactions.approach(larxus, policy, timeout = 15.seconds)) return@respond false
                if (plan.scroll == null) bot.output.sendNpcInteraction(1, larxus)
                else {
                    val slot = bot.inventory.computeIndexForId(plan.scroll)
                    slot >= 0 && bot.output.useItemOnNpc(slot, plan.scroll, larxus)
                }
            }
            if (accepted) return waitFor(15.seconds) { !bot.overlays.hasWindow() }
            delay(600)
        }
        return false
    }

    private suspend fun descend(): Boolean {
        repeat(3) {
            if (!live()) return false
            if (statue(Challenge.STATUE_OPEN) == null) {
                click(statue(Challenge.STATUE)) { statue(Challenge.STATUE_OPEN) != null }
            }
            if (click(statue(Challenge.STATUE_OPEN)) { Challenge.lowerRooms(bot.position) && !bot.isLocked }) return true
            delay(600)
        }
        return false
    }

    private suspend fun fight(plan: Trip): Boolean = withTimeoutOrNull(
        if (plan.champion.rule == ChampionRule.UNARMED) 4.minutes else 3.minutes
    ) {
        val spell = plan.outfit.spell
        if (plan.champion.rule == ChampionRule.UNARMED && bot.skill(Skill.PRAYER).staticLevel >= protection.level &&
            bot.skill(Skill.PRAYER).level > 0 && !bot.combat.prayers.isActive(protection)) {
            enabledProtection = bot.output.clickButton(protection.button)
        }
        while (live() && Challenge.arena(bot.position)) {
            if (Challenge.defeated(bot, plan.champion)) return@withTimeoutOrNull true
            val opponent = Challenge.opponent(bot)
            if (opponent == null) {
                delay(600)
                continue
            }
            if (plan.foodAllowed && bot.health <= bot.totalHealth - 16) BossFood.eat(bot, plan.food)
            if (bot.health <= maxOf(plan.maxHit * 2, bot.totalHealth / 3)) return@withTimeoutOrNull false
            if (plan.ammo?.let { !bot.equipment.contains(it.id) } == true) return@withTimeoutOrNull false
            val manualSpell = spell?.takeUnless { bot.combat.magic.isAutocasting }
            if (manualSpell != null && !handler.combat.selectAndUseSpell(manualSpell.spell)) return@withTimeoutOrNull false
            if (manualSpell != null || bot.combat.target !== opponent) attack(opponent)
            delay(600)
        }
        false
    } == true

    private suspend fun attack(opponent: Npc): Boolean =
        handler.interactions.interactLocally(3, opponent, timeout = 8.seconds) { live() && opponent.isAlive }

    private suspend fun leaveArena(): Boolean {
        if (!live() || !Challenge.lowerRooms(bot.position)) return true
        bot.combat.target = null
        bot.combat.autoRetaliateTarget = null
        if (Challenge.arena(bot.position) && !crossPortcullis(entering = false)) return false
        val ladder = bot.world.locator.findObjectsOnTile(SpecialLadder.CHAMPIONS_ARENA_UP.ladder) {
            it.def().actions.any { option -> option.equals("Climb-up", ignoreCase = true) }
        }.firstOrNull()
        return click(ladder) { !Challenge.lowerRooms(bot.position) && !bot.isLocked }
    }

    private suspend fun crossPortcullis(entering: Boolean): Boolean {
        val gate = bot.world.locator.findObjectsOnTile(Challenge.ANTECHAMBER.translate(-1, 0)) {
            it.id == Challenge.PORTCULLIS
        }.firstOrNull()
        repeat(3) {
            if (!live()) return false
            if (click(gate) { Challenge.arena(bot.position) == entering }) return true
            delay(600)
        }
        return false
    }

    private fun statue(id: Int) = bot.world.locator.findObjectsOnTile(statue) {
        it.id == id && it.state == EntityState.ACTIVE
    }.firstOrNull()

    private suspend fun click(obj: GameObject?, arrived: () -> Boolean): Boolean {
        if (arrived()) return true
        if (obj == null || !live() || !approach(obj) ||
            !live() || obj.state != EntityState.ACTIVE || !bot.output.sendObjectInteraction(1, obj)) return false
        return waitFor(10.seconds, arrived)
    }

    private suspend fun approach(obj: GameObject): Boolean {
        val policy = InteractionPolicy.STANDARD_SIZE
        if (obj.position.z == bot.z) return handler.interactions.approachLocally(obj, policy)
        val collision = bot.world.collisionManager
        if (collision.reached(bot, obj, policy)) return true
        return EntranceStands.of(obj, bot.world).filter { it.z == bot.z }
            .sortedBy { it.computeLongestDistance(bot.position) }.any { stand ->
                bot.navigator.navigate(stand, false).await() == NavigationResult.REACHED &&
                    collision.reached(bot, obj, policy)
            }
    }
}
