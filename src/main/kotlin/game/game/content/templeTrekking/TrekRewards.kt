package game.content.templeTrekking

import api.predef.*
import api.predef.ext.*
import game.content.templeTrekking.TempleTrekking.trekTokenEvents
import game.content.templeTrekking.TempleTrekking.trekTokenPoints
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import kotlin.random.Random

/**
 * What Burgh de Rott's banker gives for a reward token, and the tomes of learning.
 *
 * @author TheLining
 */
object TrekRewards {

    /**
     * One kind of reward and the range of amounts a trek pays out in it.
     *
     * @property id The item.
     * @property low The amount for a trek with the fewest points.
     * @property high The amount for a trek with the most.
     * @property noted Whether it's handed over as notes.
     */
    enum class Reward(val id: Int, val low: Int, val high: Int, val noted: Boolean = true) {
        COAL(453, 17, 102),
        BOWSTRINGS(1777, 29, 169),
        SILVER_BARS(2355, 18, 107),
        RUNE_ESSENCE(1436, 81, 468),
        WILDBLOOD_SEEDS(5311, 9, 55, noted = false),
        HERBS(205, 12, 70),
        RAW_LOBSTERS(377, 9, 56)
    }

    /**
     * A skill's tomes, from level 1 to level 3.
     */
    enum class Tome(val skill: Int, val ids: List<Int>) {
        FISHING(Skill.FISHING, listOf(7779, 7780, 7781)),
        AGILITY(Skill.AGILITY, listOf(7782, 7783, 7784)),
        THIEVING(Skill.THIEVING, listOf(7785, 7786, 7787)),
        SLAYER(Skill.SLAYER, listOf(7788, 7789, 7790)),
        MINING(Skill.MINING, listOf(7791, 7792, 7793)),
        FIREMAKING(Skill.FIREMAKING, listOf(7794, 7795, 7796)),
        WOODCUTTING(Skill.WOODCUTTING, listOf(7797, 7798, 7799));

        companion object {
            val BY_ID = entries.flatMap { tome -> tome.ids.mapIndexed { level, id -> id to (tome to level + 1) } }
                .toMap()
        }
    }

    /**
     * The XP a tome of each level gives.
     */
    val TOME_XP = listOf(550..1100, 990..2035, 990..4015)

    /**
     * The chance a token pays out a tome instead of supplies: one in this many.
     */
    const val TOME_CHANCE = 10

    const val IRON_ORE = 440
    const val GRIMY_TARROMIN = 203
    const val GRIMY_HARRALANDER = 205
    const val GRIMY_TOADFLAX = 3049

    /**
     * How much of [reward] a trek worth [points] pays. A trek that met no events gets only a token amount.
     */
    fun amount(reward: Reward, points: Int, events: Boolean): Int {
        if (!events) return maxOf(1, reward.high * Trek.MIN_POINTS / Trek.MAX_POINTS)
        val clamped = points.coerceIn(Trek.MIN_POINTS, Trek.MAX_POINTS)
        return reward.low + (reward.high - reward.low) * (clamped - Trek.MIN_POINTS) /
            (Trek.MAX_POINTS - Trek.MIN_POINTS)
    }

    /**
     * Cashes in the token at [index] of [plr]'s inventory.
     */
    fun exchange(plr: Player, index: Int) {
        val token = plr.inventory[index] ?: return
        val route = TrekRoute.forToken(token.id) ?: return
        val points = plr.trekTokenPoints
        val events = plr.trekTokenEvents
        plr.inventory.remove(index, token)
        plr.trekTokenPoints = 0
        plr.trekTokenEvents = false
        if (Random.nextInt(TOME_CHANCE) == 0) {
            val tome = Tome.entries.random()
            plr.giveItem(Item(tome.ids[route.level - 1]))
            return
        }
        val reward = Reward.entries.random()
        val count = amount(reward, points, events)
        when (reward) {
            Reward.COAL -> {
                give(plr, reward.id, count, true)
                give(plr, IRON_ORE, maxOf(1, count / 2), true)
            }

            Reward.HERBS -> {
                val toadflax = count / 3
                give(plr, if (Random.nextBoolean()) GRIMY_TARROMIN else GRIMY_HARRALANDER, count - toadflax, true)
                if (toadflax > 0) give(plr, GRIMY_TOADFLAX, toadflax, true)
            }

            else -> give(plr, reward.id, count, reward.noted)
        }
    }

    private fun give(plr: Player, id: Int, amount: Int, noted: Boolean) {
        val notedId = itemDef(id).notedId
        val item = if (noted && notedId.isPresent) Item(notedId.asInt, amount) else Item(id, amount)
        plr.giveItem(item)
    }

    /**
     * Reads a tome of learning, which gives its XP and is used up.
     */
    fun read(plr: Player, id: Int, index: Int) {
        val (tome, level) = Tome.BY_ID[id] ?: return
        val xp = TOME_XP[level - 1].random()
        plr.inventory.remove(index, Item(id))
        plr.skill(tome.skill).addExperience(xp.toDouble())
        plr.sendMessage("You read the tome and gain $xp ${Skill.getName(tome.skill)} experience.")
    }

    /**
     * Talks to Cornelius, the banker, who exchanges a reward token for its reward.
     */
    fun talkToBanker(plr: Player) {
        val id = TempleTrekking.CORNELIUS
        val token = plr.inventory.withIndex().firstOrNull { (_, item) -> item != null && item.id in TempleTrekking.TOKENS }
        val dialogue = plr.newDialogue().npc(id, "Good day, how may I help you?")
        if (token != null) {
            dialogue.options("I'd like to access my bank account, please.", { it.bank.open() },
                             "I'd like to trade in my reward token.", { offerExchange(it) },
                             "Never mind.", { it.newDialogue().player("Never mind.").open() })
        } else {
            dialogue.options("I'd like to access my bank account, please.", { it.bank.open() },
                             "Never mind.", { it.newDialogue().player("Never mind.").open() })
        }
        dialogue.open()
    }

    /**
     * Cornelius offers to trade the player's reward token for its reward.
     */
    fun offerExchange(plr: Player) {
        val id = TempleTrekking.CORNELIUS
        plr.newDialogue()
            .npc(id, "Ah, a reward token from Paterdomus. Would you like to trade it in for your reward?")
            .options("Yes please.", {
                val index = it.inventory.indexOfFirst { item -> item != null && item.id in TempleTrekking.TOKENS }
                if (index != -1) {
                    it.newDialogue().player("Yes please.").npc(id, "There you go. Thanks for helping our people.")
                        .then { p -> exchange(p, index) }.open()
                }
            }, "Not right now.", { it.newDialogue().player("Not right now.").open() })
            .open()
    }
}
