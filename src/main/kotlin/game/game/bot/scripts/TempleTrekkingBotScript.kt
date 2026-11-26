package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.Suspendable.waitFor
import api.bot.action.BotCombatActionHandler
import api.bot.action.BotNpcDialogues
import api.bot.boss.BossFood
import api.bot.script.BotScript
import api.bot.webwalk.plan.BankAccess
import api.bot.webwalk.walk.WebWalker
import engine.bank.Banking
import engine.bank.QuestBank
import game.content.petCats.PetCats
import game.content.quests.QuestGates
import game.content.templeTrekking.TempleTrekking as Temple
import game.content.templeTrekking.TempleTrekking.trek
import game.content.templeTrekking.Trek
import game.content.templeTrekking.TrekController
import game.content.templeTrekking.TrekEncounter
import game.content.templeTrekking.TrekEventType
import game.content.templeTrekking.TrekEvents
import game.content.templeTrekking.TrekFollower
import game.content.templeTrekking.TrekMonster
import game.content.templeTrekking.TrekPuzzles as Puzzles
import game.content.templeTrekking.TrekRewards
import game.content.templeTrekking.TrekRoute
import game.content.templeTrekking.TrekTier
import game.content.templeTrekking.TrekVillager
import game.skill.woodcutting.cutTree.Axe
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject
import io.luna.net.msg.out.GameChatboxMessageWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Escorts an easy villager on route one using owned supplies, then exchanges the earned token in Burgh de Rott.
 * Each event is handled through ordinary player actions in the bot's own room.
 *
 * @author lare96
 */
class TempleTrekkingBotScript private constructor(bot: Bot, private val pack: Pack?) : BotScript(bot) {
    companion object {
        private val villagers = Position(3481, 3241)
        private val banker = QuestBank.BURGH_DE_ROTT.anchor
        private val foodPolicy = BossFood.Policy(minimumBiteHealing = 8, requiredHealing = 90)
        private val chat = GameChatboxMessageWriter::class.java
        private const val SPARE_SLOTS = 12
        private const val CONTINUE = 13832
        private const val EVADE = 13831
        private const val ESCAPE = 13833
        private var Bot.trekRetryAt by Attr.long()

        /**
         * Recovers an existing token or plans a supplied trip for combat-capable bots in Morytania.
         */
        fun select(bot: Bot): TempleTrekkingBotScript? {
            if (!bot.isAlive || bot.isLocked || bot.combat.inCombat() || bot.overlays.hasWindow() ||
                bot.controllers.primary.javaClass != PlayerController::class.java ||
                !QuestGates.passes(bot, Temple.QUEST) || System.currentTimeMillis() < bot.trekRetryAt) return null
            if (Temple.hasToken(bot)) return TempleTrekkingBotScript(bot, null)
            if (bot.preferences.isSkiller || PetCats.follower(bot) != null ||
                !bot.position.isWithinDistance(villagers, 320) || bot.health < bot.totalHealth * 3 / 4 ||
                listOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS).any {
                    bot.skill(it).staticLevel < 45
                }) return null
            return plan(bot)?.let { TempleTrekkingBotScript(bot, it) }
        }

        private fun owned(bot: Bot, id: Int) = bot.inventory.computeAmountForId(id).toLong() +
            bot.bank.computeAmountForId(id)

        private fun plan(bot: Bot): Pack? {
            if (owned(bot, Puzzles.KNIFE) == 0L) return null
            val axe = Axe.entries.asReversed().firstOrNull {
                (bot.equipment[Equipment.WEAPON]?.id == it.id || owned(bot, it.id) > 0) &&
                    EquipmentDefinition.ALL.get(it.id).orElse(null)?.meetsAllRequirements(bot) == true
            } ?: return null
            val logs = Puzzles.LOGS.flatMap { id -> List(minOf(6L, owned(bot, id)).toInt()) { id } }.take(6)
            if (logs.size < 3) return null
            val stock = BossFood.stock(bot.inventory, bot.bank)
            val food = BossFood.plan(stock, foodPolicy, slots = SPARE_SLOTS - logs.size + 3) ?: return null
            val toPack = (6 - TrekEvents.food(bot).size).coerceAtLeast(0)
            val villagerFood = TrekEvents.FOODS.firstNotNullOfOrNull { id ->
                val surplus = owned(bot, id) - food.filter { it.id == id }.sumOf { it.amount }
                minOf(toPack.toLong(), surplus).toInt().takeIf { it > 0 }?.let { Item(id, it) }
            }
            return Pack(axe.id, logs.groupingBy { it }.eachCount().map { Item(it.key, it.value) }, food, villagerFood)
        }
    }

    /**
     * The owned tools and consumables set aside for a single outing.
     *
     * @author lare96
     */
    private data class Pack(val axe: Int, val logs: List<Item>, val food: List<Item>, val villagerFood: Item?)

    private var outing: Trek? = null
    override val actionSafety = ActionSafety.COMBAT
    override val allowsCombatResponse = false
    override fun allowsLoot(item: GroundItem) = false
    override fun snapshot() = null

    override suspend fun run(): Boolean {
        bot.trekRetryAt = System.currentTimeMillis() + 30.minutes.inWholeMilliseconds
        val completed = if (pack == null) {
            withTimeoutOrNull(4.minutes) { redeem() } == true
        } else {
            val arrived = withTimeoutOrNull(7.minutes) {
                prepare(pack) && startTrek() && escort()
            } == true
            if (!arrived) {
                val room = TrekEvents.controller(bot)
                if (room != null && room.trek === outing && !room.travelling && isActionSafe()) {
                    withTimeoutOrNull(15.seconds) { takePath(room, ESCAPE) }
                }
                bot.log("Temple Trekking: outing ended at ${bot.position}.")
            }
            arrived && withTimeoutOrNull(4.minutes) { redeem() } == true
        }
        if (completed) bot.trekRetryAt = System.currentTimeMillis() + 90.minutes.inWholeMilliseconds
        return true
    }

    override suspend fun finish() {
        val trek = outing ?: return
        if (bot.trek !== trek || bot.state != EntityState.ACTIVE || !bot.isAlive) return
        bot.navigator.cancel()
        bot.walking.clear()
        val room = TrekEvents.controller(bot)
        if (room?.trek === trek) room.escape()
        else bot.trek = null // The route-map task will close its window and release its lock.
    }

    private suspend fun prepare(plan: Pack): Boolean {
        val stored = plan.villagerFood
        if (stored != null && !packVillagerFood(stored)) return false
        val supplies = listOf(Item(Puzzles.KNIFE)) + plan.logs + plan.food
        val axeReady = bot.equipment[Equipment.WEAPON]?.id == plan.axe
        val required = if (axeReady) supplies else supplies + Item(plan.axe)
        if (!bot.inventory.containsAll(required) || bot.inventory.computeRemainingSize() < SPARE_SLOTS) {
            if (!handler.banking.travelToBankOpen()) return false
            try {
                if (!handler.banking.depositInventory()) return false
                for (item in required) {
                    if (!handler.banking.withdrawUnnoted(item)) return false
                }
            } finally {
                if (bot.bank.isOpen) bot.overlays.closeWindows()
            }
        }
        if (!axeReady && !handler.equipment.equip(plan.axe)) return false
        val weapon = bot.combat.weapon
        val styles = BotCombatActionHandler.allowedStyles(bot, weapon.typeDef.styles)
        val style = styles.firstOrNull() ?: return false
        if (weapon.styleDef !in styles) weapon.changeStyle(style.button)
        return bot.inventory.computeRemainingSize() >= SPARE_SLOTS
    }

    private suspend fun packVillagerFood(food: Item): Boolean {
        if (bot.inventory.computeAmountForId(food.id) != food.amount) {
            if (!handler.banking.travelToBankOpen()) return false
            try {
                if (!handler.banking.depositInventory() || !handler.banking.withdrawUnnoted(food)) return false
            } finally {
                if (bot.bank.isOpen) bot.overlays.closeWindows()
            }
        }
        val npc = reachVillager() ?: return false
        val villager = TrekVillager.of(npc.id) ?: return false
        val before = TrekEvents.food(bot).size
        return BotNpcDialogues.respond(bot, npc, listOf("Give all of this food."),
            completed = { TrekEvents.food(bot).size > before }, advanceSpeech = true, speakerId = villager.npc) {
            useOnNpc(food.id, npc, InteractionPolicy.STANDARD_SIZE)
        }
    }

    private suspend fun reachVillager(): Npc? {
        if (!walkTo(villagers)) return null
        return bot.world.locator.findNpcs(villagers, 9, true) {
            it !is TrekFollower && it.state == EntityState.ACTIVE && TrekVillager.of(it.id)?.tier == TrekTier.EASY
        }.firstOrNull()
    }

    private suspend fun startTrek(): Boolean {
        val npc = reachVillager() ?: return false
        val villager = TrekVillager.of(npc.id) ?: return false
        if (!BotNpcDialogues.respond(bot, npc, listOf("I can take you to Paterdomus."), completed = {
                    bot.overlays.getOverlay(Temple.RouteMap::class.java)?.villager == villager
                }, playerSpeech = setOf("Hello there. What are you doing here?"),
                advanceSpeech = true, speakerId = villager.npc) {
                handler.interactions.approachLocally(npc, InteractionPolicy.STANDARD_SIZE) &&
                    isActionSafe(ActionSafety.NON_COMBAT) && bot.output.sendNpcInteraction(1, npc)
            }) return false
        if (!bot.output.clickButton(TrekRoute.ROUTE_ONE.button)) return false
        if (!waitFor(4.seconds) { bot.trek != null }) return false
        outing = bot.trek
        return true
    }

    private suspend fun escort(): Boolean {
        while (bot.trek === outing && bot.isAlive) {
            val room = TrekEvents.controller(bot)
            if (room == null || room.travelling || !isActionSafe()) {
                delay(600)
                continue
            }
            if (!clearSpeech()) return false
            val encounter = room.encounter
            val solved = when {
                encounter.complete -> true
                encounter is Puzzles.Bog -> bog(room, encounter)
                encounter is Puzzles.River -> river(room, encounter)
                encounter is Puzzles.Bridge -> bridge(room, encounter)
                encounter is TrekEncounter.Fight -> fight(room)
                else -> false
            }
            if (bot.trek !== outing) break
            if (solved && clearSpeech()) {
                if (!takePath(room, CONTINUE)) return false
            } else if (room.type.fight && room.room.evadable) {
                if (!takePath(room, EVADE)) return false
            } else {
                return false
            }
        }
        return bot.isAlive && bot.position == Temple.ARRIVAL && bot.inventory.contains(TrekRoute.ROUTE_ONE.token)
    }

    private suspend fun fight(room: TrekController): Boolean {
        // The easy route can bypass encounters that require dedicated pouch charges or a silver weapon.
        if (room.type == TrekEventType.GHASTS || room.type == TrekEventType.JUVINATES) return false
        return defeatMonsters(room) { room.encounter.complete }
    }

    private suspend fun defeatMonsters(room: TrekController, finished: () -> Boolean): Boolean =
        withTimeoutOrNull(75.seconds) {
            while (inRoom(room)) {
                if (finished()) return@withTimeoutOrNull true
                if (bot.health <= bot.totalHealth - 14) BossFood.eat(bot, foodPolicy)
                val follower = room.follower
                if (bot.health <= bot.totalHealth / 3 ||
                    (room.type.fight && follower != null && follower.health <= follower.totalHealth / 3)) {
                    return@withTimeoutOrNull false
                }
                val target = room.npcs().filterIsInstance<TrekMonster>()
                    .filter { it.owner === bot && it.state == EntityState.ACTIVE && it.isAlive && !it.beaten }
                    .minWithOrNull(compareBy<TrekMonster> { it !== bot.combat.target }
                        .thenBy { it.combat.target !== follower }
                        .thenBy { bot.position.computeLongestDistance(it.position) })
                if (target != null && bot.combat.target !== target) {
                    handler.interactions.interactLocally(3, target, timeout = 5.seconds) { inRoom(room) }
                }
                delay(600)
            }
            false
        } == true

    private suspend fun bog(room: TrekController, puzzle: Puzzles.Bog): Boolean {
        val bush = objects(room, 14) { it.id == Puzzles.SPINY_BUSH }.firstOrNull() ?: return false
        repeat(4) {
            val count = bot.inventory.computeAmountForId(Puzzles.BRANCH)
            if (!useOnObject(Puzzles.KNIFE, bush, InteractionPolicy.STANDARD_SIZE, room) ||
                !waitFor(4.seconds) { bot.inventory.computeAmountForId(Puzzles.BRANCH) > count }) return false
        }
        for (y in Puzzles.Bog.SOUTH..Puzzles.Bog.NORTH) {
            val x = puzzle.real(bot.position).x
            val candidates = if (y == Puzzles.Bog.SOUTH) (Puzzles.Bog.WEST..Puzzles.Bog.EAST).toList()
                else (x - 1..x + 1).filter { it in Puzzles.Bog.WEST..Puzzles.Bog.EAST }
            var stepped = false
            for (candidate in candidates.sortedBy { kotlin.math.abs(it - x) }) {
                val tile = room.tile(Position(candidate, y))
                if (y == Puzzles.Bog.SOUTH && !walkLocal(room, tile.translate(0, -1))) continue
                val ground = bot.world.locator.findObjectsOnTile(tile) { it.id == Puzzles.BOG }.firstOrNull() ?: continue
                if (!probe(room, ground)) continue
                if (!bot.output.sendObjectInteraction(1, ground) ||
                    !waitFor(5.seconds) { bot.position == tile && !bot.isLocked }) return false
                stepped = true
                break
            }
            if (!stepped) return false
        }
        return walkLocal(room, bot.position.translate(0, 1)) && waitFor(4.seconds) { puzzle.crossed }
    }

    private suspend fun probe(room: TrekController, ground: GameObject): Boolean {
        while (inRoom(room) && bot.inventory.contains(Puzzles.BRANCH)) {
            val messages = bot.input.received[chat]
            val previous = messages.lastOrNull()
            if (!useOnObject(Puzzles.BRANCH, ground, Puzzles.NEXT_TO, room)) return false
            var result: String? = null
            if (!waitFor(4.seconds) {
                    result = messages.toList().asReversed().takeWhile { it !== previous }.firstNotNullOfOrNull {
                        ((it.message as? GameChatboxMessageWriter)?.message as? String)?.takeIf { line ->
                            line == "The ground seems firm." || line == "Erk, this ground feels quite soft." ||
                                line == "Your branch gets stuck in the bog and you lose it."
                        }
                    }
                    result != null
                }) return false
            if (result == "The ground seems firm.") return true
            if (result == "Erk, this ground feels quite soft.") return false
        }
        return false
    }

    private suspend fun river(room: TrekController, puzzle: Puzzles.River): Boolean {
        repeat(3) {
            val tree = objects(room, 17) { it.id in Puzzles.VINE_TREES.take(3) }.firstOrNull() ?: return false
            val count = bot.inventory.computeAmountForId(Puzzles.SHORT_VINE)
            if (!useOnObject(Puzzles.KNIFE, tree, InteractionPolicy.STANDARD_SIZE, room) ||
                !waitFor(4.seconds) { bot.inventory.computeAmountForId(Puzzles.SHORT_VINE) > count }) return false
        }
        if (!handler.inventory.useItem(Puzzles.SHORT_VINE).onItem(Puzzles.SHORT_VINE) ||
            !waitFor(4.seconds) { bot.inventory.contains(Puzzles.LONG_VINE) }) return false
        val branchTile = room.tile(Puzzles.River.SWING_FROM.translate(0, 1))
        val branch = bot.world.locator.findObjectsOnTile(branchTile) { it.id == Puzzles.BIG_BRANCH }.firstOrNull()
            ?: return false
        if (!useOnObject(Puzzles.LONG_VINE, branch, Puzzles.NEXT_TO, room) ||
            !waitFor(4.seconds) { !bot.inventory.contains(Puzzles.LONG_VINE) }) return false
        val vine = bot.world.locator.findObjectsOnTile(branchTile) { it.id == Puzzles.VINE_ON_BRANCH }.firstOrNull()
        return clickObject(room, vine, 2) && waitFor(8.seconds) { puzzle.crossed }
    }

    private suspend fun bridge(room: TrekController, puzzle: Puzzles.Bridge): Boolean {
        if (!defeatMonsters(room) { room.npcs().none { it is TrekMonster && it.id == Puzzles.ZOMBIE && it.isAlive } }) {
            return false
        }
        for (stage in Puzzles.BROKEN_BRIDGE until Puzzles.FIXED_BRIDGE) {
            val bridge = objects(room, 14) { it.id == stage }.firstOrNull() ?: return false
            val log = Puzzles.LOGS.firstOrNull(bot.inventory::contains) ?: return false
            if (!useOnObject(log, bridge, InteractionPolicy.STANDARD_SIZE, room) ||
                !waitFor(4.seconds) { bridge.state != EntityState.ACTIVE }) return false
        }
        val fixed = objects(room, 14) { it.id == Puzzles.FIXED_BRIDGE }.firstOrNull()
        return clickObject(room, fixed, 2) && waitFor(8.seconds) { puzzle.crossed }
    }

    private suspend fun takePath(room: TrekController, id: Int): Boolean {
        for (path in objects(room, 36) { it.id == id }.take(4)) {
            if (clickObject(room, path, 2) &&
                waitFor(5.seconds) { room.travelling || bot.trek !== outing }) return true
        }
        return false
    }

    private fun objects(room: TrekController, radius: Int, match: (GameObject) -> Boolean): List<GameObject> =
        bot.world.locator.findObjects(room.tile(room.room.start), radius) {
            it.state == EntityState.ACTIVE && bot.dynamicMap?.contains(it.position) == true && match(it)
        }.sortedBy { it.position.computeLongestDistance(bot.position) }

    private fun inRoom(room: TrekController) =
        bot.trek === outing && TrekEvents.controller(bot) === room && !room.travelling && isActionSafe()

    private suspend fun walkLocal(room: TrekController, tile: Position): Boolean =
        inRoom(room) && (bot.position == tile || bot.navigator.navigate(tile, false).await() == NavigationResult.REACHED)

    private suspend fun clickObject(room: TrekController, obj: GameObject?, option: Int): Boolean =
        obj != null && withTimeoutOrNull(9.seconds) {
            inRoom(room) && handler.interactions.approachLocally(obj, Puzzles.NEXT_TO) && inRoom(room) &&
                obj.state == EntityState.ACTIVE && bot.output.sendObjectInteraction(option, obj)
        } == true

    private suspend fun useOnObject(id: Int, obj: GameObject, policy: InteractionPolicy, room: TrekController): Boolean =
        withTimeoutOrNull(9.seconds) {
            if (!inRoom(room) || !handler.interactions.approachLocally(obj, policy) || !inRoom(room) ||
                obj.state != EntityState.ACTIVE) return@withTimeoutOrNull false
            val slot = bot.inventory.computeIndexForId(id)
            slot >= 0 && bot.output.useItemOnObject(slot, id, obj)
        } == true

    private suspend fun useOnNpc(id: Int, npc: Npc, policy: InteractionPolicy): Boolean {
        if (!handler.interactions.approachLocally(npc, policy) || !isActionSafe(ActionSafety.NON_COMBAT) ||
            npc.state != EntityState.ACTIVE) return false
        val slot = bot.inventory.computeIndexForId(id)
        return slot >= 0 && bot.output.useItemOnNpc(slot, id, npc)
    }

    private suspend fun walkTo(tile: Position): Boolean =
        bot.position.isWithinDistance(tile, 3) ||
            WebWalker.webWalk(bot, tile, attempts = 2, bank = BankAccess.NONE, fallback = false).success

    private suspend fun clearSpeech(): Boolean {
        val queue = bot.dialogues ?: return !bot.overlays.hasWindow()
        repeat(5) {
            val page = bot.overlays.getOverlay(DialogueInterface::class.java) ?: return !bot.overlays.hasWindow()
            if (bot.dialogues !== queue || !queue.owns(page) || !bot.output.sendContinueDialogue()) return false
            if (!waitFor(3.seconds) { bot.overlays.getOverlay(DialogueInterface::class.java) !== page }) return false
        }
        return !bot.overlays.hasWindow()
    }

    private suspend fun redeem(): Boolean {
        val token = Temple.TOKENS.firstOrNull { owned(bot, it) > 0 } ?: return false
        if (!clearSpeech()) return false
        if (!bot.inventory.contains(token)) {
            if (!handler.banking.travelToBankOpen()) return false
            try {
                if (!handler.banking.withdrawUnnoted(Item(token))) return false
            } finally {
                if (bot.bank.isOpen) bot.overlays.closeWindows()
            }
        }
        if (!walkTo(banker)) return false
        val npc = bot.world.locator.findNpcs(banker, 5) {
            it.id == Temple.CORNELIUS && it.state == EntityState.ACTIVE
        }.firstOrNull() ?: return false
        if (!BotNpcDialogues.respond(bot, npc, listOf("Yes please."), completed = { !bot.inventory.contains(token) },
                advanceSpeech = true) {
                useOnNpc(token, npc, Banking.BANKER_POLICY)
            }) return false
        for (id in TrekRewards.Tome.BY_ID.keys) {
            if (bot.inventory.contains(id)) handler.inventory.clickItem(1, id)
        }
        return true
    }
}
