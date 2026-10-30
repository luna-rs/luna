package game.bot.scripts

import api.bot.Suspendable.naturalMicroDelay
import api.bot.script.BotScript.ActionSafety
import api.bot.script.ReflexBotScript
import api.combat.death.DeathHookHandler.deathItems
import api.predef.*
import api.predef.ext.*
import game.player.item.consume.food.Food
import game.player.item.consume.potion.Potion
import game.skill.fletching.attachArrow.Arrow
import game.skill.magic.Rune
import game.skill.prayer.Bone
import io.luna.Luna
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionType
import kotlin.math.roundToInt

/**
 * A [ReflexBotScript] that collects wanted, valuable, and death-related ground items.
 *
 * Death items are attempted first, followed by wanted items and general loot. The minimum loot value depends on
 * combat level, skill progress, time played, personality, and the bot's current emotions.
 *
 * Low-value junk may be dropped to make room for non-junk loot. Wanted items and items selected for pickup are
 * protected from dropping. Junk pickups must fit without dropping other items to prevent repeated exchanges.
 *
 * @param bot The bot running this script.
 * @author lare96
 */
class LootItemReflexScript(bot: Bot) : ReflexBotScript(bot) {

    companion object {

        /**
         * The minimum coin pile considered worthwhile unless it is wanted or belongs to the bot's death pile.
         */
        const val MINIMUM_LOOT_GP_VALUE = 1000.0

        /**
         * How long, in ticks, the bot leaves an item alone after failing to pick it up.
         */
        const val FAILED_PICKUP_TICKS = 100

        /**
         * Item types eligible for dropping when their value is below the bot's loot threshold.
         *
         * Membership alone does not make an item disposable. Wanted items and selected loot are protected.
         */
        val ALL_JUNK: Set<Int> = (
                Bone.ID_TO_BONE.keys +
                        Food.ID_TO_FOOD.keys +
                        Potion.DOSE_TO_POTION.keys +
                        Rune.ID_TO_RUNE.keys +
                        Arrow.IDS
                ).toSet()

        /**
         * Calculates the minimum item value this bot considers worth collecting.
         *
         * Skill progress and time played determine maturity within a combat-level price range. Intelligence and
         * confidence adjust that value, while greed lowers it for the current scan.
         *
         * @param bot The bot whose loot threshold is being calculated.
         * @return The minimum loot value in coins, never less than one.
         */
        fun calculateMinLootValue(bot: Bot): Int {
            val hoursPlayed = bot.timePlayed.toMinutes().coerceAtLeast(0) / 60.0
            val experience = hoursPlayed / (hoursPlayed + 100.0)

            val skills = bot.skills.toList()
            val totalLevel = skills.sumOf { it.staticLevel }
            val startingTotal = skills.size + 9
            val maximumTotal = skills.size * 99
            val progress = ((totalLevel - startingTotal).toDouble() /
                    (maximumTotal - startingTotal)).coerceIn(0.0, 1.0)

            val maturity = 0.75 * progress + 0.25 * experience
            val (minimumMultiplier, maximumMultiplier) = when {
                bot.combatLevel < 20 -> 0.10 to 0.25
                bot.combatLevel < 40 -> 0.25 to 0.50
                bot.combatLevel < 60 -> 0.50 to 0.75
                bot.combatLevel < 80 -> 0.75 to 1.00
                else -> 1.00 to 1.25
            }
            val combatMultiplier = minimumMultiplier +
                    (maximumMultiplier - minimumMultiplier) * maturity

            val intelligence = bot.personality.intelligence.coerceIn(0.0, 1.0)
            val confidence = bot.personality.confidence.coerceIn(0.0, 1.0)
            val personalityMultiplier = (0.75 + 0.50 * intelligence) *
                    (0.90 + 0.20 * confidence)

            val greedMultiplier = if (bot.emotions.isFeeling(EmotionType.GREEDY)) {
                rand().nextDouble(0.50, 0.75)
            } else {
                1.0
            }

            val value = Luna.settings().bots().baseLootValue().toDouble() *
                    combatMultiplier *
                    personalityMultiplier *
                    greedMultiplier

            return value.roundToInt().coerceAtLeast(1)
        }
    }

    /**
     * The minimum loot value for the current scan and its subsequent pickup attempts.
     */
    private var minimumLootValue = 1

    /**
     * Ground items selected for pickup, ordered by death recovery, wanted-item status, and general value.
     */
    private val valuableItems = ArrayList<GroundItem>()
    
    
    /**
     * Script relative ignored item IDs.
     */
    private var ignored: Set<Int> = emptySet()

    /**
     * Whether a previous attempt failed to create enough inventory space.
     *
     * Items that already fit remain eligible. The restriction resets once the inventory has a free slot.
     */
    private var droppedJunk = false

    /**
     * Items the bot failed to pick up, and the tick until which it leaves each alone. Otherwise the reflex would pause
     * the bot's script for the same item again as soon as it resumed.
     */
    private val failedPickups = HashMap<GroundItem, Long>()

    override fun shouldReact(): Boolean {
        valuableItems.clear()

        // A locked or busy bot can't pick anything up yet.
        if (bot.health < 1 || !ActionSafety.COMBAT.allows(bot)) {
            return false
        }
        if (!bot.inventory.isFull) {
            droppedJunk = false
        }

        ignored = bot.scriptStack.current()?.takeIf { it.isRunning() }?.ignoredLoot ?: emptySet()
        minimumLootValue = calculateMinLootValue(bot)
        bot.deathItems.removeAll { it.state != EntityState.ACTIVE }
        findValuableItems()
        return valuableItems.isNotEmpty()
    }

    override suspend fun run(): Boolean {
        repeat(2) {
            val iterator = valuableItems.iterator()
            while (iterator.hasNext()) {
                if (bot.health < 1) {
                    return true
                }

                val groundItem = iterator.next()
                if (bot.scriptStack.current()?.allowsLoot(groundItem) == false) {
                    iterator.remove()
                    continue
                }
                if (groundItem.state != EntityState.ACTIVE) {
                    bot.deathItems.remove(groundItem)
                    iterator.remove()
                    continue
                }

                val item = groundItem.toItem()
                if (!bot.inventory.hasSpaceFor(item)) {
                    if (item.id in ALL_JUNK || droppedJunk) {
                        continue
                    }

                    dropJunk(item)
                    if (!bot.inventory.hasSpaceFor(item)) {
                        droppedJunk = true
                        continue
                    }
                }

                // Dropping items can suspend, so check that the target still exists.
                if (groundItem.state != EntityState.ACTIVE) {
                    bot.deathItems.remove(groundItem)
                    iterator.remove()
                    continue
                }

                val collected = handler.loot.pickup(groundItem)
                if (groundItem.state != EntityState.ACTIVE) {
                    bot.deathItems.remove(groundItem)
                    iterator.remove()
                } else if (collected) {
                    iterator.remove()
                }

                if (!bot.personality.isDextrous) {
                    bot.naturalMicroDelay()
                }
            }
        }

        // What's left wasn't collected: a lost race, a refused click, or a walk that didn't get there.
        val until = bot.world.currentTick + FAILED_PICKUP_TICKS
        for (groundItem in valuableItems) {
            if (groundItem.state == EntityState.ACTIVE) {
                failedPickups[groundItem] = until
            }
        }
        valuableItems.clear()
        return true
    }

    /**
     * Selects active nearby loot and orders it by recovery priority.
     *
     * Death items remain tracked until they leave the world. Selecting an item does not mark it as recovered.
     * Junk that cannot fit is skipped, while existing inventory stacks can still accept loot when all slots are used.
     */
    private fun findValuableItems() {
        val now = bot.world.currentTick
        failedPickups.entries.removeIf { (groundItem, until) -> until <= now || groundItem.state != EntityState.ACTIVE }
        for (groundItem in bot.viewableItems) {
            // The pickup refuses an item the bot can't see, such as one beyond its viewing distance.
            if (groundItem.state != EntityState.ACTIVE ||
                !groundItem.isWithinDistance(bot, Position.VIEWING_DISTANCE * 2) ||
                groundItem.id in ignored || !groundItem.isVisibleTo(bot) || groundItem in failedPickups) {
                continue
            }
            if (bot.scriptStack.current()?.allowsLoot(groundItem) == false) continue

            val item = groundItem.toItem()
            if (!bot.inventory.hasSpaceFor(item) &&
                (item.id in ALL_JUNK || droppedJunk)) {
                continue
            }

            val valuable = when {
                TreasureTrailBotScript.wantsClue(bot, item.id) -> true
                groundItem in bot.deathItems -> true
                bot.preferences.needsWantedItem(item.id) -> true
                item.id == 995 -> item.amount >
                        (minimumLootValue * 0.25).coerceAtLeast(MINIMUM_LOOT_GP_VALUE)
                else -> world.economy.getTotalPrice(item) >= minimumLootValue
            }
            if (valuable) {
                valuableItems += groundItem
            }
        }

        val ordered = handler.loot.ordered(valuableItems)
        valuableItems.clear()
        valuableItems.addAll(ordered)
    }

    /**
     * Drops eligible junk until the requested item fits or no candidates remain.
     *
     * Inventory contents are copied before dropping because each action can suspend and modify the container.
     * Capacity is checked after each attempt instead of assuming that a successful interaction freed a slot.
     *
     * @param loot The item requiring inventory space.
     */
    private suspend fun dropJunk(loot: Item) {
        val protectedIds = valuableItems.mapTo(HashSet()) { it.id }
        val candidates = bot.inventory.filterNotNull()

        for (item in candidates) {
            if (bot.health < 1 || bot.inventory.hasSpaceFor(loot)) {
                return
            }
            if (item.id !in ALL_JUNK ||
                item.id in protectedIds ||
                bot.preferences.needsWantedItem(item.id) ||
                world.economy.getTotalPrice(item) >= minimumLootValue) {
                continue
            }

            handler.inventory.dropItem(item.id)
        }
    }
}
