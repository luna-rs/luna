package game.bot.scripts

import api.bot.Suspendable.naturalMicroDelay
import api.bot.Suspendable.waitFor
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

    override fun shouldReact(): Boolean {
        valuableItems.clear()

        if (bot.health < 1) {
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

                val amountBefore = bot.inventory.computeAmountForId(item.id)
                if (handler.interactions.interact(1, groundItem)) {
                    // Interaction success only confirms reaching the item and sending the click.
                    waitFor {
                        groundItem.state != EntityState.ACTIVE ||
                                bot.inventory.computeAmountForId(item.id) > amountBefore
                    }
                }

                val collected = bot.inventory.computeAmountForId(item.id) > amountBefore
                if (collected) {
                    bot.preferences.rebalanceWantedItems()
                }
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

        valuableItems.clear()
        return true
    }

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

    /**
     * Selects active nearby loot and orders it by recovery priority.
     *
     * Death items remain tracked until they leave the world. Selecting an item does not mark it as recovered.
     * Junk that cannot fit is skipped, while existing inventory stacks can still accept loot when all slots are used.
     */
    private fun findValuableItems() {
        for (groundItem in bot.viewableItems) {
            if (groundItem.state != EntityState.ACTIVE ||
                !groundItem.isWithinDistance(bot, Position.VIEWING_DISTANCE * 2) ||
                groundItem.id in ignored) {
                continue
            }

            val item = groundItem.toItem()
            if (!bot.inventory.hasSpaceFor(item) &&
                (item.id in ALL_JUNK || droppedJunk)) {
                continue
            }

            val valuable = when {
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

        valuableItems.sortBy {
            when {
                it in bot.deathItems -> 0
                bot.preferences.needsWantedItem(it.id) -> 1
                else -> 2
            }
        }
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