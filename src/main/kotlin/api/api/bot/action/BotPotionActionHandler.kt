package api.bot.action

import api.predef.*
import game.player.item.consume.potion.Potion
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionType
import java.util.EnumMap

/**
 * Decides when a bot drinks the potions it carries, one dose at a time.
 *
 * Needs are checked in order of danger: antipoison while poisoned, antifire near dragons once the last dose is gone, a
 * prayer potion once a whole dose fits, then combat boosts that have worn down. Personality and emotions shape the
 * habits:
 * - Dumb bots sometimes forget a potion they carry and only think of it again a while later.
 * - Fear keeps antipoison, antifire, and prayer potions in mind.
 * - Intelligent bots top up boosts once about half has worn off; dumb bots wait until a boost is nearly gone.
 * - Greedy bots stretch their boosts further to save doses.
 *
 * @author lare96
 */
class BotPotionActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    /**
     * Something a bot can drink a potion for.
     */
    enum class Need(val defensive: Boolean) {
        ANTIPOISON(true),
        ANTIFIRE(true),
        PRAYER(true),
        BOOST(false)
    }

    companion object {

        /**
         * Fire-breathing NPCs, from the dragon combat hooks: King Black Dragon and the green, blue, red and black
         * dragons.
         */
        val FIRE_BREATHERS = setOf(50, 53, 54, 55, 941)

        /**
         * Every antipoison dose, strongest potion first.
         */
        private val ANTIPOISONS = listOf(Potion.ANTIDOTE_PLUS_PLUS, Potion.ANTIDOTE_PLUS, Potion.SUPER_ANTIPOISON,
                                         Potion.ANTIPOISON_POTION)

        /**
         * Combat potions by the skill they boost, super potions first.
         */
        private val BOOSTS = mapOf(
            SKILL_ATTACK to listOf(Potion.SUPER_ATTACK to true, Potion.ATTACK_POTION to false),
            SKILL_STRENGTH to listOf(Potion.SUPER_STRENGTH to true, Potion.STRENGTH_POTION to false),
            SKILL_DEFENCE to listOf(Potion.SUPER_DEFENCE to true, Potion.DEFENCE_POTION to false),
            SKILL_RANGED to listOf(Potion.RANGING_POTION to false),
            SKILL_MAGIC to listOf(Potion.MAGIC_POTION to false)
        )

        /**
         * The three-tick potion delay, during which another drink would be ignored.
         */
        private const val DRINK_DELAY_MS = 1800L

        /**
         * Whether a whole prayer potion dose fits under [staticLevel] from [level].
         */
        fun prayerDoseFits(level: Int, staticLevel: Int) = staticLevel - level >= Potion.prayerRestore(staticLevel)

        /**
         * The share of a full boost left when a bot of [intelligence] drinks again; greedy bots wait longer.
         */
        fun boostRefreshShare(intelligence: Double, greedy: Boolean) =
            (intelligence * 0.5 - if (greedy) 0.25 else 0.0).coerceAtLeast(0.0)

        /**
         * Whether a boost of [fullBoost] above [staticLevel] has worn down to [refreshShare] of itself at [level].
         * At a share of zero the bot only drinks once the boost is gone.
         */
        fun boostWornDown(level: Int, staticLevel: Int, fullBoost: Int, refreshShare: Double) =
            level - staticLevel <= fullBoost * refreshShare && level < staticLevel + fullBoost

        /**
         * The chance a bot of [intelligence] forgets a potion it needs.
         */
        fun forgetChance(intelligence: Double) = (1.0 - intelligence) * (1.0 - intelligence) * 0.6

        /**
         * Whether [mob] breathes dragonfire.
         */
        fun breathesFire(mob: Mob?) = mob is Npc && mob.id in FIRE_BREATHERS
    }

    /**
     * When a forgotten need comes back to mind, in milliseconds.
     */
    private val remindAt = EnumMap<Need, Long>(Need::class.java)

    /**
     * When the last dose was drunk, in milliseconds.
     */
    private var lastDrinkAt = 0L

    /**
     * The share of a full boost left when the bot drinks again, decided anew after each boost dose.
     */
    private var refreshShare: Double? = null

    /**
     * Drinks at most one dose for the most pressing need.
     *
     * @param fighting Whether the bot is fighting, so combat boosts are worth a dose.
     * @param dragons Whether the bot faces dragonfire.
     * @param prayerBelow Prayer points under which the bot wants a prayer potion, or `null` if it isn't praying.
     * @return `true` if a dose was drunk.
     */
    fun drink(fighting: Boolean, dragons: Boolean, prayerBelow: Int? = null): Boolean {
        if (System.currentTimeMillis() - lastDrinkAt < DRINK_DELAY_MS) return false
        if (bot.status.isPoisoned() && !bot.status.isAntiPoison() && drinkFor(Need.ANTIPOISON, ANTIPOISONS)) {
            return true
        }
        if (dragons && !bot.status.isAntiFire() && drinkFor(Need.ANTIFIRE, listOf(Potion.ANTI_FIRE_POTION))) {
            return true
        }
        val prayer = bot.prayer
        if (prayerBelow != null && prayer.level < prayerBelow && prayerDoseFits(prayer.level, prayer.staticLevel) &&
            drinkFor(Need.PRAYER, listOf(Potion.PRAYER_POTION))) {
            return true
        }
        if (fighting) {
            val share = refreshShare ?: boostRefreshShare(bot.personality.intelligence,
                                                         bot.emotions.isFeeling(EmotionType.GREEDY))
            refreshShare = share
            for ((skillId, potions) in BOOSTS) {
                val skill = bot.skill(skillId)
                val worn = potions.filter { (_, superPotion) ->
                    boostWornDown(skill.level, skill.staticLevel, Potion.combatBoost(skill.staticLevel, superPotion),
                                  share)
                }
                if (worn.isNotEmpty() && drinkFor(Need.BOOST, worn.map { it.first })) {
                    refreshShare = null
                    return true
                }
            }
        }
        return false
    }

    /**
     * Withdraws one antifire dose for a dragon trip, if the bot has none on it and one is banked.
     *
     * @return `true` if the bot carries antifire afterward.
     */
    suspend fun withdrawAntifire(): Boolean {
        val potion = Potion.ANTI_FIRE_POTION
        if (carries(potion)) return true
        val id = potion.doses.reversed().firstOrNull { bot.bank.contains(it) } ?: return false
        return handler.banking.withdraw(Item(id)) && carries(potion)
    }

    /**
     * Whether the bot has a dose of [potion] in its inventory.
     */
    private fun carries(potion: Potion) = potion.doses.any { bot.inventory.contains(it) }

    /**
     * Drinks a carried dose of [potions] for [need], unless the bot forgets about it.
     */
    private fun drinkFor(need: Need, potions: List<Potion>): Boolean {
        val carried = potions.filter(::carries)
        return carried.isNotEmpty() && remembers(need) && drinkAny(carried)
    }

    /**
     * Rolls whether the bot thinks of [need] now. A bot that forgets doesn't think of it again for a while, but fear
     * keeps antipoison, antifire, and prayer potions in mind.
     */
    private fun remembers(need: Need): Boolean {
        val now = System.currentTimeMillis()
        if (need.defensive && bot.emotions.isFeeling(EmotionType.SCARED)) return true
        if (now < (remindAt[need] ?: 0L)) return false
        if (rand(forgetChance(bot.personality.intelligence))) {
            remindAt[need] = now + rand(20, 60) * 1000L
            bot.log("Forgot about my ${need.name.lowercase()} potion.")
            return false
        }
        return true
    }

    /**
     * Drinks the first carried dose of [potions], preferring earlier potions and the fewest doses left.
     */
    private fun drinkAny(potions: List<Potion>): Boolean {
        for (potion in potions) {
            for (id in potion.doses) {
                val index = bot.inventory.computeIndexForId(id)
                if (index != -1 && handler.inventory.clickItem(1, id, index)) {
                    lastDrinkAt = System.currentTimeMillis()
                    return true
                }
            }
        }
        return false
    }
}
