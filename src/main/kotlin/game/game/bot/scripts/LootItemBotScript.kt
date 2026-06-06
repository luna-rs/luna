package game.bot.scripts

import api.bot.script.ReflexBotScript
import api.bot.Suspendable.naturalMicroDelay
import api.combat.death.DeathHookHandler.deathItems
import api.predef.*
import game.player.item.consume.food.Food
import game.player.item.consume.potion.Potion
import game.skill.prayer.Bone
import io.luna.Luna
import io.luna.game.model.EntityState
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionType

/**
 * A [ReflexBotScript] that allows a bot to interrupt its current activity and loot valuable nearby ground items.
 *
 * This script is intentionally conservative. Bots only react to items that are personally wanted, valuable enough
 * compared to their current loot threshold, or part of their own death pile. If the bot has no free inventory space,
 * it may drop low-priority junk items to make room before attempting to loot.
 *
 * The loot threshold is affected by combat level, personality, and emotions. Lower-level bots are more willing to
 * loot cheap items, intelligent bots are slightly more selective, dumb bots are slightly less selective, and greedy
 * bots are more likely to loot items below their normal value threshold.
 *
 * @author lare96
 */
class LootItemBotScript(bot: Bot) : ReflexBotScript(bot) {

    companion object {

        /**
         * Item ids that can be dropped when the bot needs inventory space for valuable loot.
         */
        val JUNK_ITEMS: Set<Int> = run {
            val junk = HashSet<Int>()
            junk += Bone.ID_TO_BONE.keys
            junk += Food.ID_TO_FOOD.keys
            junk += Potion.DOSE_TO_POTION.keys
            junk
        }
    }

    /**
     * The currently visible ground items that this bot wants to loot.
     *
     * This list is rebuilt during [shouldReact] and consumed during [run]. Items are sorted by priority before looting
     * so death items and explicitly wanted items are attempted before generic high-value loot.
     */
    private val valuableItems = ArrayList<GroundItem>()

    /**
     * Whether this script already tried to free inventory space by dropping junk.
     *
     * This prevents the bot from repeatedly reacting to the same nearby loot when its inventory is still full after
     * a failed junk-dropping pass.
     */
    private var droppedJunk = false

    override fun shouldReact(): Boolean {
        valuableItems.clear()

        if (findValuableItems()) {
            if (bot.inventory.isFull && droppedJunk) {
                // Our inventory was full last time and it is still full. Do not react again.
                return false
            }

            // Our inventory is not full, or we have not tried to drop junk for this loot attempt yet.
            droppedJunk = false
            return true
        }
        return false
    }

    override suspend fun run(): Boolean {
        val spaceNeeded = valuableItems.size - bot.inventory.computeRemainingSize()
        if (spaceNeeded > 0) {
            dropJunk(spaceNeeded)
            droppedJunk = true
        }

        if (bot.inventory.isFull) {
            // Inventory is still full after trying to drop junk.
            return true
        }

        valuableItems.sortWith(
            compareByDescending<GroundItem> { it in bot.deathItems }
                .thenByDescending { it.id in bot.preferences.wantedItems }
                .thenByDescending { world.economy.getTotalPrice(it.toItem()) }
        )

        repeat(2) {
            val iterator = valuableItems.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()

                if (item.state != EntityState.ACTIVE || handler.interactions.interact(1, item)) {
                    iterator.remove()
                }

                if (!bot.personality.isDextrous) {
                    bot.naturalMicroDelay()
                }
            }
        }

        // Clean up script state and return to prior activities.
        droppedJunk = false
        return true
    }

    private fun findValuableItems(): Boolean {
        var minimumLootValue = Luna.settings().bots().baseLootValue().toDouble()
        minimumLootValue *= when {
            bot.combatLevel < 20 -> rand().nextDouble(0.10, 0.25)
            bot.combatLevel < 40 -> rand().nextDouble(0.25, 0.50)
            bot.combatLevel < 60 -> rand().nextDouble(0.50, 0.75)
            bot.combatLevel < 80 -> rand().nextDouble(0.75, 1.0)
            else -> rand(1.0, 1.25)
        }

        when {
            bot.personality.isIntelligent -> minimumLootValue *= rand(1.0, 1.25)
            bot.personality.isDumb -> minimumLootValue *= rand(0.75, 1.0)
        }

        if (bot.emotions.isFeeling(EmotionType.GREEDY)) {
            minimumLootValue *= rand().nextDouble(0.50, 0.75)
        }

        var found = false
        for (groundItem in bot.viewableItems) {
            val item = groundItem.toItem()

            if (item.id == 995) {
                if (item.amount > minimumLootValue * 0.50) {
                    valuableItems += groundItem
                    found = true
                }
            } else if (groundItem.id in bot.preferences.wantedItems) {
                valuableItems += groundItem
                found = true
            } else if (world.economy.getTotalPrice(item) > minimumLootValue) {
                valuableItems += groundItem
                found = true
            } else if (groundItem in bot.deathItems) {
                bot.deathItems.clear()
                valuableItems += groundItem
                found = true
            }
        }

        if (!found) {
            valuableItems.clear()
            return false
        }
        return true
    }

    /**
     * Drops enough junk items to create the requested number of free inventory spaces.
     *
     * This only drops items listed in [JUNK_ITEMS]. If the bot does not have enough junk items, fewer spaces may be
     * freed than requested, and the caller is responsible for checking whether the inventory is still full afterward.
     *
     * @param spaceNeeded The number of inventory spaces this script wants to create.
     */
    private suspend fun dropJunk(spaceNeeded: Int) {
        var amount = spaceNeeded

        for (item in bot.inventory) {
            if (amount < 1) {
                break
            }

            if (item != null && item.id in JUNK_ITEMS) {
                handler.inventory.dropItem(item.id)
                amount--
            }
        }
    }
}