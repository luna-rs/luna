package game.bot.scripts.quests

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.trading.ExactBotTrade
import api.bot.script.BotScript
import com.google.gson.JsonObject
import game.content.shieldOfArrav.ShieldOfArrav as Quest
import game.player.QuestJournalInterface
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Acquires one shield half, exchanges it with a real partner, and collects the royal reward.
 *
 * @author lare96
 */
class ShieldOfArravBotScript(bot: Bot, private val data: ArravData = ArravData()) : BotScript(bot) {
    companion object {
        private val reldo = Position(3209, 3495)
        private val bookcase = Position(3212, 3493)
        private val baraek = Position(3218, 3435)
        private val straven = Position(3246, 9780)
        private val jonny = Position(3223, 3395)
        private val tramp = Position(3208, 3391)
        private val katrine = Position(3186, 3385)
        private val store = Position(3245, 3385, 1)
        private val storeLadder = Position(3252, 3384)
        private val storeDoor = Position(3251, 3385)
        private val chest = Position(3235, 9761)
        private val phoenixDoor = Position(3247, 9779)
        private const val PHOENIX_HIDEOUT_DOOR = 2397
        private const val BLACK_ARM_STAIR_DOOR = 2399
        private val cupboard = Position(3189, 3385, 1)
        private val cupboardUp = Position(3188, 3389)
        private val cupboardStairFoot = Position(3188, 3388)
        private val cupboardDown = Position(3188, 3390, 1)
        private val cupboardStairHead = Position(3188, 3392, 1)
        private val cupboardDoor = Position(3185, 3388)
        private val square = Position(3207, 3428)
        private val curator = Position(3255, 3445)
        private val king = Position(3222, 3476)
        private var Bot.arravRetryAt by Attr.long().persist("shield_of_arrav_bot_retry_at")

        fun eligible(bot: Bot): Boolean = !Quest.completed(bot) && ActionSafety.NON_COMBAT.allows(bot) &&
            !bot.overlays.hasWindow() && bot.skill(Skill.HITPOINTS).staticLevel >= 20 &&
            System.currentTimeMillis() >= bot.arravRetryAt &&
            (Quest.phoenixStage(bot) > 0 || Quest.blackArmStage(bot) > 0 ||
                (bot.position.isWithinDistance(square, 30) && bot.inventory.contains(Item(Quest.COINS, Quest.BRIBE))))
    }

    /**
     * Carries the selected side across a pause before the first native interaction.
     *
     * @author lare96
     */
    class ArravData : QuestAttemptData() {
        var phoenix: Boolean? = null
        var sharedHalf = false

        override fun load(data: JsonObject) {
            super.load(data)
            phoenix = data.get("phoenix")?.asBoolean
            sharedHalf = data.get("sharedHalf")?.asBoolean ?: false
        }

        override fun save(data: JsonObject) {
            super.save(data)
            phoenix?.let { data.addProperty("phoenix", it) }
            data.addProperty("sharedHalf", sharedHalf)
        }
    }

    private val actions = QuestBotActions(bot)
    private var objective = "choose a gang"
    override val actionSafety = ActionSafety.COMBAT
    override val allowsCombatResponse = false
    var failure: String? = null
        private set

    override fun snapshot() = data

    private val phoenixSide: Boolean
        get() = when {
            Quest.phoenixStage(bot) >= Quest.PHOENIX_JOINED -> true
            Quest.blackArmStage(bot) > 0 -> false
            Quest.phoenixStage(bot) > 0 -> true
            else -> data.phoenix ?: (bot.username.hashCode() and 1 == 0)
        }

    override suspend fun run(): Boolean {
        if (Quest.completed(bot)) return true
        data.phoenix = phoenixSide
        val finished = withTimeoutOrNull(data.deadline - System.currentTimeMillis()) {
            while (actions.live() && !Quest.completed(bot)) {
                if (!step()) {
                    delay(600)
                    if (!step()) return@withTimeoutOrNull false
                }
            }
            Quest.completed(bot)
        } == true
        if (!finished && !Quest.completed(bot)) {
            failure = "Could not $objective at ${bot.position}."
            bot.log("Shield of Arrav: $failure")
            bot.arravRetryAt = System.currentTimeMillis() + 30 * 60_000L
        }
        return true
    }

    private suspend fun step(): Boolean {
        if (Quest.completed(bot)) return true
        return if (phoenixSide && Quest.phoenixStage(bot) < Quest.PHOENIX_JOINED) joinPhoenix()
        else if (!phoenixSide && Quest.blackArmStage(bot) < Quest.BLACK_ARM_JOINED) joinBlackArm()
        else completeWithPartner()
    }

    private suspend fun joinPhoenix(): Boolean {
        val stage = Quest.phoenixStage(bot)
        objective = "finish Phoenix stage $stage"
        return when (stage) {
            0 -> actions.talk(Quest.RELDO, reldo, Quest.ASK_QUEST, portrait = Quest.RELDO_PORTRAIT) {
                Quest.phoenixStage(bot) > stage
            }
            1 -> {
                if (!actions.has(Quest.BOOK) && (!actions.click(Quest.BOOKCASE, bookcase, 2) ||
                        !actions.await { actions.has(Quest.BOOK) })) return false
                val read = handler.inventory.clickItem(1, Quest.BOOK) && actions.await { Quest.phoenixStage(bot) > stage }
                if (bot.overlays.has(QuestJournalInterface::class.java)) handler.widgets.clickCloseInterface()
                read
            }
            Quest.READ_BOOK -> actions.talk(Quest.RELDO, reldo, portrait = Quest.RELDO_PORTRAIT) {
                Quest.phoenixStage(bot) > stage
            }
            Quest.ASKED_RELDO -> actions.talk(Quest.BARAEK, baraek, Quest.ASK_GANG, Quest.PAY_BARAEK) {
                Quest.phoenixStage(bot) > stage
            }
            Quest.PAID_BARAEK -> actions.talk(Quest.STRAVEN, straven, Quest.JOIN_PHOENIX) { Quest.phoenixStage(bot) > stage }
            Quest.ASSIGNED_REPORT -> {
                if (!actions.has(Quest.REPORT) && !actions.loot(Quest.REPORT, jonny)) {
                    if (!actions.fight(Quest.JONNY, jonny) { actions.has(Quest.REPORT) }) return false
                    if (!actions.has(Quest.REPORT) && !actions.loot(Quest.REPORT, jonny, wait = 10_000)) return false
                }
                actions.talk(Quest.STRAVEN, straven) { Quest.phoenixStage(bot) > stage }
            }
            else -> false
        }
    }

    private suspend fun joinBlackArm(): Boolean {
        val stage = Quest.blackArmStage(bot)
        objective = "finish Black Arm stage $stage"
        return when (stage) {
            0 -> actions.talk(Quest.TRAMP, tramp, Quest.FIND_ALLEY) { Quest.blackArmStage(bot) > stage }
            1 -> actions.talk(Quest.KATRINE, katrine, Quest.JOIN_BLACK_ARM) { Quest.blackArmStage(bot) > stage }
            Quest.ASSIGNED_CROSSBOWS -> {
                if (!actions.has(Quest.CROSSBOW, 2) && !inStoreUpperFloor() && !enterWeaponsStore()) return false
                repeat(2 - bot.inventory.computeAmountForId(Quest.CROSSBOW).coerceAtMost(2)) {
                    if (!actions.loot(Quest.CROSSBOW, store)) return false
                }
                if (inStoreUpperFloor() && (!actions.click(1746, storeLadder.translate(0, 0, 1)) ||
                        !actions.await { bot.z == 0 } || !leaveWeaponsStore())) return false
                actions.talk(Quest.KATRINE, katrine) { Quest.blackArmStage(bot) > stage }
            }
            else -> false
        }
    }

    private fun inStoreUpperFloor(): Boolean = bot.z == store.z && bot.position.isWithinDistance(store, 10)

    private suspend fun enterWeaponsStore(): Boolean {
        if (actions.click(1747, storeLadder) && actions.await { inStoreUpperFloor() }) return true
        val outside = storeDoor.translate(0, 1)
        if (!actions.walk(outside) || !actions.click(Quest.WEAPONS_STORE_DOOR, storeDoor) ||
            !actions.await { bot.position == storeDoor && !bot.isLocked }) return false
        return actions.click(1747, storeLadder) && actions.await { inStoreUpperFloor() }
    }

    private suspend fun leaveWeaponsStore(): Boolean {
        val outside = storeDoor.translate(0, 1)
        return actions.walk(storeDoor) && actions.click(Quest.WEAPONS_STORE_DOOR, storeDoor) &&
            actions.await { bot.position == outside && !bot.isLocked }
    }

    private suspend fun completeWithPartner(): Boolean {
        val phoenix = phoenixSide
        if (!actions.has(Quest.CERTIFICATE) && (phoenix || !data.sharedHalf)) {
            objective = "retrieve the gang's shield half"
            val half = if (phoenix) Quest.PHOENIX_HALF else Quest.BLACK_ARM_HALF
            if (!actions.has(half) && !(if (phoenix) phoenixHalf() else blackArmHalf())) return false
            if (!actions.has(Quest.BLACK_ARM_HALF) || !phoenix) {
                objective = "exchange the Black Arm shield half with a partner"
                if (!trade(if (phoenix) emptyList() else listOf(Item(Quest.BLACK_ARM_HALF)),
                        if (phoenix) listOf(Item(Quest.BLACK_ARM_HALF)) else emptyList())) return false
                if (!phoenix) data.sharedHalf = true
            }
            if (phoenix) {
                objective = "certify both halves with the curator"
                if (!actions.talk(Quest.CURATOR, curator) { actions.has(Quest.CERTIFICATE, 2) }) return false
            }
        }
        if (!actions.has(Quest.CERTIFICATE) || phoenix && actions.has(Quest.CERTIFICATE, 2)) {
            objective = "exchange a certificate with a partner"
            if (!trade(if (phoenix) listOf(Item(Quest.CERTIFICATE)) else emptyList(),
                    if (phoenix) emptyList() else listOf(Item(Quest.CERTIFICATE)))) return false
        }
        objective = "claim the reward from King Roald"
        return actions.talk(Quest.ROALD, king) { Quest.completed(bot) }
    }

    private suspend fun takeHalf(at: Position, closed: Int, open: Int, searchOption: Int, item: Int): Boolean {
        if (actions.objectAt(open, at) == null &&
            (!actions.click(closed, at) || !actions.await { actions.objectAt(open, at) != null })) return false
        return actions.click(open, at, searchOption) && actions.await { actions.has(item) }
    }

    private suspend fun phoenixHalf(): Boolean {
        suspend fun search() = takeHalf(chest, Quest.PHOENIX_CHEST, Quest.PHOENIX_CHEST_OPEN, 1, Quest.PHOENIX_HALF)
        if (search()) return true
        if (!actions.walk(phoenixDoor.translate(0, 1)) || !actions.click(PHOENIX_HIDEOUT_DOOR, phoenixDoor) ||
            !actions.await { bot.position == phoenixDoor && !bot.isLocked }) return false
        return search()
    }

    private suspend fun blackArmHalf(): Boolean {
        if (bot.z != cupboard.z || !bot.position.isWithinDistance(cupboard, 10)) {
            if (!actions.walk(cupboardStairFoot) &&
                (!crossCupboardDoor(entering = true) || !actions.walk(cupboardStairFoot))) return false
            if (!actions.click(1722, cupboardUp) || !actions.await {
                    bot.z == cupboard.z && bot.position.isWithinDistance(cupboard, 10)
                }) return false
        }
        return takeHalf(cupboard, Quest.BLACK_ARM_CUPBOARD, Quest.BLACK_ARM_CUPBOARD_OPEN, 2, Quest.BLACK_ARM_HALF)
    }

    private suspend fun trade(give: List<Item>, receive: List<Item>): Boolean {
        if (bot.z == cupboard.z && bot.position.isWithinDistance(cupboard, 10)) {
            if (!actions.walk(cupboardStairHead) || !actions.click(1723, cupboardDown) ||
                !actions.await { bot.position == cupboardStairFoot && !bot.isLocked } ||
                !crossCupboardDoor(entering = false)) return false
        }
        if (bot.z == 0 && bot.x in 3229..3258 && bot.y in 9757..9779 &&
            (!actions.walk(phoenixDoor) || !actions.click(PHOENIX_HIDEOUT_DOOR, phoenixDoor) ||
                !actions.await { bot.position == phoenixDoor.translate(0, 1) && !bot.isLocked })) return false
        val phoenix = phoenixSide
        val blackArmTile = square.translate(-1, 0)
        if (!actions.walk(if (phoenix) square else blackArmTile)) return false
        val partnerTile = if (phoenix) blackArmTile else square
        var partner: Bot? = null
        val ready = actions.await(150_000) {
            partner = bot.world.locator.findPlayers(partnerTile, 1) {
                it is Bot && it !== bot && it.isAlive && it.position == partnerTile && it.walking.isEmpty &&
                    it.scriptStack.findActive(ShieldOfArravBotScript::class.java)
                    .filter { script -> script.phoenixSide != phoenix }.isPresent &&
                    receive.all(it.inventory::contains)
            }.filterIsInstance<Bot>().minByOrNull { it.username }
            partner != null
        }
        if (!ready) return false
        val other = partner ?: return false
        return ExactBotTrade.exchange(bot, other, give, receive) { actions.live() }
    }

    private suspend fun crossCupboardDoor(entering: Boolean): Boolean {
        val outside = cupboardDoor.translate(0, -1)
        return actions.walk(if (entering) outside else cupboardDoor) &&
            actions.click(BLACK_ARM_STAIR_DOOR, cupboardDoor) &&
            actions.await { bot.position == (if (entering) cupboardDoor else outside) && !bot.isLocked }
    }
}
