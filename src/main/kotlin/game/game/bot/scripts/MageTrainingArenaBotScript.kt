package game.bot.scripts

import api.bot.Suspendable.waitFor
import api.bot.action.BotNpcDialogues
import api.bot.script.DynamicBotScript
import api.bot.script.productionRuneCosts
import api.predef.*
import engine.obj.StairDestination
import game.content.mageTrainingArena.AlchemistsPlayground
import game.content.mageTrainingArena.ArenaRoom
import game.content.mageTrainingArena.CreatureGraveyard
import game.content.mageTrainingArena.EnchantingChamber
import game.content.mageTrainingArena.MageTrainingArena
import game.content.mageTrainingArena.MageTrainingArena.Points
import game.content.mageTrainingArena.MageTrainingArena.arenaRoom
import game.content.mageTrainingArena.MageTrainingArena.bonesToPeachesUnlocked
import game.content.mageTrainingArena.MageTrainingArena.mtaStarted
import game.content.mageTrainingArena.RewardsShop
import game.content.mageTrainingArena.TelekineticTheatre
import game.skill.magic.Magic
import game.skill.magic.SpellCost
import game.skill.magic.SpellRequirement
import game.skill.magic.bonesToItems.BonesToItemsType
import game.skill.magic.enchantJewellery.EnchantJewelleryType
import game.skill.magic.lowHighAlch.AlchemyType
import game.skill.magic.telekineticGrab.TelekineticGrabAction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Makes bounded visits to the Mage Training Arena, using owned supplies to work toward Bones to Peaches.
 * The arena persists the hat, points and spell unlock between visits.
 *
 * @author lare96
 */
internal class MageTrainingArenaBotScript(bot: Bot) : DynamicBotScript(bot) {

    companion object {
        private const val COINS = 995
        private const val GRAVEYARD_ENTRY_HP = 24
        private const val GRAVEYARD_EXIT_HP = 12
        private const val WORK_SLOTS = 22
        private val GUARDIAN = Position(3363, 3304)
        private val REWARDS = Position(3362, 3318, 1)
        private val REWARD_SLOT = RewardsShop.STOCK.indexOfFirst { it.id == RewardsShop.BONES_TO_PEACHES }
        private val TARGETS = Points.entries.zip(RewardsShop.STOCK[REWARD_SLOT].prices).toMap()

        private fun points(bot: Bot, kind: Points) = MageTrainingArena.points(bot, kind)

        fun eligible(bot: Bot): Boolean = !bot.bonesToPeachesUnlocked && bot.spellbook == Spellbook.REGULAR &&
            (TARGETS.all { (kind, target) -> points(bot, kind) >= target } || roomFor(bot) != null)

        private fun roomFor(bot: Bot): Visit? =
            TARGETS.entries.sortedBy { (kind, target) -> points(bot, kind).toDouble() / target }
                .firstNotNullOfOrNull { (kind, target) ->
                    if (points(bot, kind) >= target) null else visits(bot, kind).firstOrNull { visit ->
                        bot.magic.level >= visit.level && visit.runes(bot, visit.minimumCasts) != null
                    }
                }

        private fun visits(bot: Bot, kind: Points): List<Visit> = when (kind) {
            Points.TELEKINETIC -> listOf(Visit(TelekineticTheatre, TelekineticGrabAction.LEVEL,
                TelekineticGrabAction.SPELL_ID, TelekineticGrabAction.REQUIREMENTS, 12, 48))
            Points.ALCHEMIST -> AlchemyType.entries.reversed().map { type ->
                Visit(AlchemistsPlayground, type.level, type.spellId, type.requirements, 5, 36)
            }
            Points.ENCHANTMENT -> EnchantJewelleryType.ALL.reversed().map { type ->
                Visit(EnchantingChamber, type.level, type.spellId, type.requirements, 1, 36)
            }
            Points.GRAVEYARD -> listOf(BonesToItemsType.BANANAS).filter { bot.hitpoints.level >= GRAVEYARD_ENTRY_HP }
                .map { Visit(CreatureGraveyard, it.level, it.spellId, it.requirements.drop(1), 1, 8) }
        }
    }

    /**
     * A room's native spell and the number of casts to pack for a visit.
     *
     * @author lare96
     */
    private class Visit(val room: ArenaRoom, val level: Int, val spell: Int,
                        val requirements: List<SpellRequirement>, val minimumCasts: Int, val maxCasts: Int) {
        fun runes(bot: Bot, minimum: Int = minimumCasts): List<Item>? {
            val costs = bot.productionRuneCosts(requirements)
            val casts = costs.minOfOrNull {
                (bot.inventory.computeAmountForId(it.id).toLong() + bot.bank.computeAmountForId(it.id)) / it.amount
            } ?: maxCasts.toLong()
            if (casts < minimum) return null
            return costs.map { it.withAmount(it.amount * minOf(casts, maxCasts.toLong()).toInt()) }
        }

        fun canCast(bot: Bot) = Magic.spellCost(bot, level, requirements) is SpellCost.Castable
    }

    private var enteredRoom = false
    private var openedRewards = false

    override fun allowsLoot(item: GroundItem): Boolean = false

    override suspend fun run(): Boolean {
        withTimeoutOrNull(8.minutes) {
            if (!eligible(bot)) return@withTimeoutOrNull
            if (readyToBuy()) {
                if (prepare(null) && enterHall() && getHat()) buySpell()
                return@withTimeoutOrNull
            }
            val visit = roomFor(bot) ?: return@withTimeoutOrNull
            if (!prepare(visit) || !enterHall() || !getHat()) return@withTimeoutOrNull
            enteredRoom = true
            if (!clickObject(visit.room.portal) || !waitFor(4.seconds) { bot.arenaRoom === visit.room }) {
                return@withTimeoutOrNull
            }
            withTimeoutOrNull(4.minutes) {
                when (visit.room) {
                    TelekineticTheatre -> MageTrainingMazeBot.play(bot) {
                        needs(Points.TELEKINETIC) && visit.canCast(bot)
                    }
                    AlchemistsPlayground -> alchemy(visit)
                    EnchantingChamber -> enchant(visit)
                    CreatureGraveyard -> graveyard(visit)
                    else -> error("Unknown Mage Training Arena room: ${visit.room.title}")
                }
            }
            if (bot.arenaRoom != null &&
                (!clickObject(MageTrainingArena.EXIT_TELEPORT) || !waitFor(4.seconds) { bot.arenaRoom == null })) {
                return@withTimeoutOrNull
            }
            if (readyToBuy()) buySpell()
        }
        return true
    }

    override suspend fun finish() {
        if (openedRewards && RewardsShop.isOpen(bot)) bot.overlays.closeWindows()
        if (enteredRoom && bot.arenaRoom != null && bot.state == EntityState.ACTIVE) {
            MageTrainingArena.leave(bot, false)
        }
        enteredRoom = false
        openedRewards = false
    }

    private fun needs(kind: Points) = points(bot, kind) < TARGETS.getValue(kind)

    private fun readyToBuy() = TARGETS.keys.none(::needs)

    private suspend fun prepare(visit: Visit?): Boolean {
        val supplies = visit?.runes(bot).orEmpty()
        val bankedHat = MageTrainingArena.HATS.firstOrNull { bot.bank.contains(it) && !MageTrainingArena.hasHat(bot) }
        val supplied = supplies.all { amount(it.id) >= it.amount }
        val slots = if (visit == null) 1 else WORK_SLOTS
        val blocked = MageTrainingArena.carriesClue(bot) ||
            visit?.room === AlchemistsPlayground && bot.inventory.contains(COINS) ||
            visit?.room === CreatureGraveyard && bot.inventory.containsAny(CreatureGraveyard.BANANA, CreatureGraveyard.PEACH)
        if (bankedHat == null && supplied && !blocked && bot.inventory.computeRemainingSize() >= slots) return true
        if (onRewardsFloor() && !stairs(false)) return false
        if (inHall() && !doorway(false)) return false
        val bank = handler.webWalkToNearestBank(attempts = 2, fallback = false) ?: return false
        if (!handler.banking.open(bank)) return false
        try {
            val keep = supplies.mapTo(mutableSetOf()) { it.id }
            keep.addAll(MageTrainingArena.HATS)
            if (!handler.banking.depositInventory(keep)) return false
            if (bankedHat != null && !handler.banking.withdrawUnnoted(Item(bankedHat))) return false
            for (item in supplies) {
                val needed = item.amount - bot.inventory.computeAmountForId(item.id)
                if (needed > 0 && !handler.banking.withdrawUnnoted(item.withAmount(needed))) return false
            }
            return bot.inventory.computeRemainingSize() >= slots
        } finally {
            if (bot.bank.isOpen) bot.overlays.closeWindows()
        }
    }

    private fun inHall(): Boolean = bot.z == 0 && bot.position.x in 3353..3373 && bot.position.y in 3300..3323

    private fun onRewardsFloor() = bot.position.isWithinDistance(REWARDS, 12) && bot.z == REWARDS.z

    private suspend fun enterHall(): Boolean {
        if (inHall()) return true
        if (onRewardsFloor()) return stairs(false)
        return handler.webWalkTo(MageTrainingArena.OUTSIDE_DOORWAY, attempts = 2, fallback = false) && doorway(true)
    }

    private suspend fun doorway(entering: Boolean): Boolean =
        clickObject(MageTrainingArena.DOORWAY) && waitFor(4.seconds) { inHall() == entering }

    private suspend fun getHat(): Boolean {
        if (MageTrainingArena.hasHat(bot) && bot.mtaStarted) return true
        val guardian = bot.world.locator.findNearestNpc(GUARDIAN, MageTrainingArena.ENTRANCE_GUARDIAN) ?: return false
        val option = if (bot.mtaStarted) "About the progress hat..." else "I'm new to this place. Where am I?"
        val speech = setOf("Hi.", "Who created it?", "Interesting. So what can I do here?",
            "How do you record the points I have earned?", "Yes please!", "Sorry, can I?")
        return BotNpcDialogues.respond(bot, guardian, listOf(option), timeout = 60.seconds,
            completed = { bot.mtaStarted && MageTrainingArena.hasHat(bot) }, playerSpeech = speech) {
            handler.interactions.interactLocally(1, guardian)
        }
    }

    private suspend fun stairs(up: Boolean): Boolean {
        val origin = if (up) REWARDS.translate(0, 0, -1) else REWARDS
        val stairs = bot.world.locator.findNearestObject(origin, 18) {
            it.def().name in StairDestination.NAMES && up in StairDestination.directions(it)
        } ?: return false
        val action = if (up) "Climb-up" else "Climb-down"
        val option = stairs.def().actions.indexOfFirst { action.equals(it, ignoreCase = true) } + 1
        if (option == 0 || !click(stairs, option)) return false
        return waitFor(6.seconds) { bot.z == if (up) 1 else 0 }
    }

    private suspend fun buySpell() {
        if (!onRewardsFloor() && !stairs(true)) return
        val guardian = bot.world.locator.findNearestNpc(REWARDS, MageTrainingArena.REWARDS_GUARDIAN) ?: return
        openedRewards = true
        if (!handler.interactions.interactLocally(2, guardian) ||
            !waitFor(4.seconds) { RewardsShop.isOpen(bot) }) return
        if (output.sendItemWidgetClick(2, REWARD_SLOT, RewardsShop.ITEMS, RewardsShop.BONES_TO_PEACHES)) {
            waitFor(4.seconds) { bot.bonesToPeachesUnlocked }
        }
        if (RewardsShop.isOpen(bot)) output.sendCloseInterface()
        if (waitFor(4.seconds) { !RewardsShop.isOpen(bot) }) stairs(false)
    }

    private suspend fun alchemy(visit: Visit) {
        val arena = AlchemistsPlayground
        for (cast in 0 until visit.maxCasts) {
            if (!needs(Points.ALCHEMIST) || !visit.canCast(bot)) break
            val cupboard = objects(48) { normalizedCupboard(it.id) in arena.CUPBOARDS }
                .mapNotNull { obj -> arena.itemIn(normalizedCupboard(obj.id))?.let { obj to arena.coinValue(it) } }
                .maxWithOrNull(compareBy({ it.second }, { -it.first.position.computeLongestDistance(bot.position) }))
                ?.first ?: return
            if (!handler.interactions.interactLocally(1, cupboard) ||
                !waitFor(3.seconds) { arena.ITEMS.any(bot.inventory::contains) }) return
            val id = arena.ITEMS.first(bot.inventory::contains)
            if (!castOn(visit, id)) return
            if (amount(COINS) >= 100 && !depositAlchemy()) return
        }
        if (amount(COINS) > 0) depositAlchemy()
    }

    private fun normalizedCupboard(id: Int) = if (id % 2 == 0) id - 1 else id

    private suspend fun depositAlchemy(): Boolean =
        clickObject(AlchemistsPlayground.COLLECTOR) && waitFor(4.seconds) { amount(COINS) == 0 }

    private suspend fun enchant(visit: Visit) {
        val arena = EnchantingChamber
        for (cast in 0 until visit.maxCasts) {
            if (!needs(Points.ENCHANTMENT) || !visit.canCast(bot)) break
            val stone = bot.world.locator.findNearestItem(bot, 16) {
                it.id == arena.DRAGONSTONE && it.isVisibleTo(bot)
            }
            val id = if (stone != null && handler.interactions.interactLocally(1, stone) &&
                waitFor(3.seconds) { amount(arena.DRAGONSTONE) > 0 }) arena.DRAGONSTONE else {
                val shape = arena.bonus
                if (!clickObject(shape.pile) || !waitFor(3.seconds) { amount(shape.item) > 0 }) return
                shape.item
            }
            if (!castOn(visit, id)) return
            if (amount(arena.ORB) >= 20 && !depositOrbs()) return
        }
        if (amount(arena.ORB) > 0) depositOrbs()
    }

    private suspend fun depositOrbs(): Boolean =
        clickObject(EnchantingChamber.ORB_HOLE) && waitFor(4.seconds) { amount(EnchantingChamber.ORB) == 0 }

    private suspend fun graveyard(visit: Visit) {
        val arena = CreatureGraveyard
        repeat(visit.maxCasts) {
            if (!needs(Points.GRAVEYARD) || !visit.canCast(bot) || bot.hitpoints.level <= GRAVEYARD_EXIT_HP) return
            val pile = objects(40) { it.id in arena.PILES }.firstOrNull() ?: return
            var fruit = 0
            val capacity = minOf(arena.FRUIT_PER_POINT + 3, bot.inventory.computeRemainingSize())
            while (bot.hitpoints.level > GRAVEYARD_EXIT_HP && fruit < capacity) {
                val bones = arena.BONES.sumOf(::amount)
                if (!handler.interactions.interactLocally(1, pile) ||
                    !waitFor(3.seconds) { arena.BONES.sumOf(::amount) > bones }) return
                fruit = arena.BONES.withIndex().sumOf { (index, id) -> (index + 1) * amount(id) }
            }
            if (fruit == 0 || !output.clickButton(visit.spell) || !waitFor(6.seconds) { amount(arena.BANANA) > 0 }) return
            while (amount(arena.BANANA) > 0 && bot.hitpoints.level <= GRAVEYARD_EXIT_HP + 6) {
                val index = bot.inventory.computeIndexForId(arena.BANANA)
                if (!handler.inventory.eat(index, arena.BANANA)) return
            }
            if (amount(arena.BANANA) > 0 &&
                (!clickObject(arena.CHUTE) || !waitFor(4.seconds) { amount(arena.BANANA) == 0 })) return
        }
    }

    private suspend fun castOn(visit: Visit, id: Int): Boolean {
        val slot = bot.inventory.computeIndexForId(id)
        return slot >= 0 && output.useSpellOnItem(visit.spell, slot, id) &&
            waitFor(8.seconds) { bot.inventory.computeIdForIndex(slot) != id }
    }

    private fun amount(id: Int): Int = bot.inventory.computeAmountForId(id)

    private fun objects(distance: Int, filter: (GameObject) -> Boolean): Set<GameObject> =
        bot.world.locator.findObjects(bot, distance, true) { it.state == EntityState.ACTIVE && filter(it) }

    private suspend fun clickObject(id: Int): Boolean =
        objects(48) { it.id == id && (bot.arenaRoom?.inside(it.position) ?: true) }.any { click(it) }

    private suspend fun click(target: GameObject, option: Int = 1): Boolean =
        withTimeoutOrNull(20.seconds) {
            handler.interactions.approachLocally(target, InteractionPolicy.STANDARD_SIZE) &&
                output.sendObjectInteraction(option, target)
        } == true
}
