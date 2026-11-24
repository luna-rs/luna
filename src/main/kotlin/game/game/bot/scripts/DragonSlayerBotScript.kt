package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotCombatActionHandler
import api.bot.action.BotNpcDialogues
import api.bot.boss.BossFood
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import api.bot.webwalk.walk.WebWalker
import com.google.gson.JsonObject
import game.content.dragonSlayer.DragonSlayer as Quest
import game.content.dragonSlayer.DragonSlayerDialogue as Dialogue
import game.content.dragonSlayer.DragonSlayerMap as MapPieces
import game.content.dragonSlayer.DragonSlayerMap.mazeDoorsUsed
import game.content.dragonSlayer.DragonSlayerShip as Ship
import io.luna.game.model.Entity
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.mob.varp.PersistentVarp.DRAGON_SLAYER_ORACLE
import io.luna.game.model.mob.varp.PersistentVarp.DRAGON_SLAYER_SHIELD
import io.luna.game.model.mob.varp.PersistentVarp.DRAGON_SLAYER_SHIP_REPAIRS
import io.luna.game.model.mob.varp.PersistentVarp.DRAGON_SLAYER_WORMBRAIN
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Follows the Dragon Slayer journal through live interactions. Quest varps, inventory and position determine
 * where to resume after interruption. Each attempt has a deadline and releases the bot on a failed step.
 *
 * @author lare96
 */
class DragonSlayerBotScript(bot: Bot, private val data: DragonSlayerData = DragonSlayerData()) : BotScript(bot) {
    companion object {
        private const val QUEST_REWARD_XP = 18_650.0
        private const val ATTEMPT_MS = 18 * 60_000L
        private const val COOLDOWN_MS = 60 * 60_000L
        private val food = BossFood.Policy(minimumBiteHealing = 10, requiredHealing = 120)
        private var Bot.dragonSlayerRetryAt by Attr.long().persist("dragon_slayer_bot_retry_at")

        private val guild = Position(3191, 3362)
        private val oziach = Position(3069, 3517)
        private val duke = Position(3212, 3220, 1)
        private val oracle = Position(3015, 3502)
        private val klarense = Position(3047, 3204)
        private val wormbrain = Position(3014, 3189)
        private val draynor = Position(3100, 3258)
        private val rock = Position(2833, 3255)
        private val lairGate = Position(2847, 9636)
        private val elvarg = Position(2852, 9637)
        private val mazeDoors = listOf(Position(2926, 3253), Position(2931, 3253, 1), Position(2924, 3249, 2),
            Position(2931, 9643), Position(2929, 9652), Position(2936, 9655))
        private val mazeDoorStands = listOf(Position(2926, 3253), Position(2930, 3253, 1), Position(2924, 3250, 2),
            Position(2931, 9643), Position(2929, 9651), Position(2936, 9655))
        private val mazeNpcs = listOf(Position(2930, 3252), Position(2927, 3251, 1), Position(2923, 3251, 2),
            Position(2932, 9642), Position(2929, 9649), Position(2936, 9650))

        fun eligible(bot: Bot): Boolean {
            if (Quest.stage(bot) == Quest.COMPLETE || !bot.isAlive || bot.combat.inCombat() || bot.overlays.hasWindow() ||
                bot.controllers.primary.javaClass != PlayerController::class.java ||
                System.currentTimeMillis() < bot.dragonSlayerRetryAt || bot.equipment[Equipment.WEAPON] == null ||
                bot.skill(Skill.ATTACK).staticLevel < 45 || bot.skill(Skill.HITPOINTS).staticLevel < 45 ||
                BossFood.carriedHealing(bot, food) < food.requiredHealing) return false
            if (listOf(Skill.STRENGTH, Skill.DEFENCE).any {
                    BotCombatActionHandler.limitExperience(bot, bot.skill(it), QUEST_REWARD_XP) < QUEST_REWARD_XP
                }) return false
            return requiredSupplies(bot).all { bot.actionHandler.has(it) }
        }

        private fun requiredSupplies(bot: Bot): List<Item> = buildList {
            val stage = Quest.stage(bot)
            val repairs = bot.varpManager.getValue(DRAGON_SLAYER_SHIP_REPAIRS).coerceIn(0, 3)
            var coins = 0
            if (stage < Quest.SHIP_BOUGHT) coins += 2_000
            if (stage < Quest.MAP_GIVEN_TO_NED && !owns(bot, Quest.WORMBRAIN_MAP_PART) && !owns(bot, Quest.CRANDOR_MAP)) {
                coins += 10_000
            }
            if (coins > 0) add(Item(995, coins))
            if (stage < Quest.SHIP_REPAIRED && repairs < 3) {
                add(Item(Ship.PLANK, 3 - repairs))
                add(Item(Ship.STEEL_NAILS, 30 * (3 - repairs)))
                add(Item(Ship.HAMMER))
            }
            if (stage < Quest.MAP_GIVEN_TO_NED && !MapPieces.oracleOfferingMade(bot) &&
                !owns(bot, Quest.ORACLE_MAP_PART) && !owns(bot, Quest.CRANDOR_MAP)) addAll(MapPieces.offerings)
        }

        private fun owns(bot: Bot, id: Int): Boolean = bot.actionHandler.has(Item(id))
    }

    /**
     * Persists the deadline across script interruption and login. Progress remains on the character.
     *
     * @author lare96
     */
    class DragonSlayerData : BotScriptData() {
        var deadline = System.currentTimeMillis() + ATTEMPT_MS

        override fun load(data: JsonObject) {
            deadline = data.get("deadline").asLong
        }

        override fun save(data: JsonObject) {
            data.addProperty("deadline", deadline)
        }
    }

    override val actionSafety = ActionSafety.COMBAT
    override val allowsCombatResponse = false
    var failure: String? = null
        private set

    override fun snapshot(): DragonSlayerData = data

    override fun allowsLoot(item: GroundItem): Boolean = item.id in MapPieces.colouredKeys || item.id in Quest.mapParts

    override suspend fun run(): Boolean {
        if (stage() == Quest.COMPLETE) return true
        val remaining = data.deadline - System.currentTimeMillis()
        var step = "prepare supplies"
        val finished = if (remaining > 0) withTimeoutOrNull(remaining) {
            if (!prepare()) return@withTimeoutOrNull false
            while (live() && stage() != Quest.COMPLETE) {
                step = when (stage()) {
                    0 -> "speak to the Guildmaster"
                    Quest.GUILD_MASTER_SPOKEN_TO -> "accept Oziach's challenge"
                    Quest.OZIACH_SPOKEN_TO -> "prepare for Crandor and buy the ship"
                    in Quest.SHIP_BOUGHT until Quest.SHIP_REPAIRED -> "repair the hull"
                    Quest.SHIP_REPAIRED -> "complete the map and hire Ned"
                    Quest.MAP_GIVEN_TO_NED -> "sail to Crandor"
                    Quest.SAILED_TO_CRANDOR -> "reach and defeat Elvarg"
                    else -> return@withTimeoutOrNull false
                }
                bot.log("Dragon Slayer: $step.")
                val priorStage = stage()
                if (!advance() && stage() == priorStage) {
                    if (!live()) return@withTimeoutOrNull false
                    delay(600)
                    if (!advance() && stage() == priorStage) return@withTimeoutOrNull false
                }
            }
            stage() == Quest.COMPLETE
        } == true else false
        if (!finished) {
            failure = "Could not $step at ${bot.position}, quest stage ${stage()}."
            bot.log("Dragon Slayer: $failure")
            bot.dragonSlayerRetryAt = System.currentTimeMillis() + COOLDOWN_MS
        }
        return true
    }

    private fun stage(): Int = Quest.stage(bot)

    private fun live(): Boolean = bot.state == EntityState.ACTIVE && bot.isAlive

    private suspend fun advance(): Boolean = when (stage()) {
        0 -> talk(Quest.GUILD_MASTER, guild, Dialogue.START) { stage() > 0 }
        Quest.GUILD_MASTER_SPOKEN_TO -> talk(Quest.OZIACH, oziach, Dialogue.CHALLENGE) { stage() >= Quest.OZIACH_SPOKEN_TO }
        Quest.OZIACH_SPOKEN_TO -> prepareVoyage()
        in Quest.SHIP_BOUGHT until Quest.SHIP_REPAIRED -> repair()
        Quest.SHIP_REPAIRED -> leaveShip() && collectMap() &&
            (Ship.hired(bot) || talk(Ship.NED_IN_DRAYNOR, draynor, Ship.HIRE_OPTION) { Ship.hired(bot) }) &&
            talk(Ship.NED_IN_DRAYNOR, draynor, Ship.MAP_OPTION) { stage() >= Quest.MAP_GIVEN_TO_NED }
        Quest.MAP_GIVEN_TO_NED -> sail()
        Quest.SAILED_TO_CRANDOR -> fightElvarg()
        else -> false
    }

    private suspend fun prepare(): Boolean {
        val missing = requiredSupplies(bot).mapNotNull {
            val amount = it.amount - bot.inventory.computeAmountForId(it.id)
            if (amount > 0) Item(it.id, amount) else null
        }
        val questItems = (Quest.mapParts + Quest.CRANDOR_MAP + MapPieces.colouredKeys + Dialogue.MAZE_KEY)
            .filter { !bot.inventory.contains(it) && bot.bank.contains(it) }.map { Item(it) }
        val toWithdraw = missing + questItems
        if (toWithdraw.isEmpty()) return true
        if (!bot.actionHandler.hasAll(toWithdraw) || !bot.inventory.hasSpaceForAll(toWithdraw)) return false
        return withdraw(toWithdraw) && bot.inventory.containsAll(requiredSupplies(bot))
    }

    private suspend fun prepareVoyage(): Boolean {
        if (!bot.inventory.contains(Quest.CRANDOR_MAP)) {
            if (bot.mazeDoorsUsed == 0 && !bot.inventory.contains(Quest.MAZE_MAP_PART) &&
                !talk(Quest.OZIACH, oziach, Dialogue.MAZE) { bot.inventory.contains(Dialogue.MAZE_KEY) }) return false
            if (!bot.inventory.contains(Quest.WORMBRAIN_MAP_PART) &&
                !hint(DRAGON_SLAYER_WORMBRAIN, Dialogue.WORMBRAIN_HINT)) return false
            if (!bot.inventory.contains(Quest.ORACLE_MAP_PART) &&
                !hint(DRAGON_SLAYER_ORACLE, Dialogue.ORACLE_HINT)) return false
        }
        if (!bot.equipment.contains(Quest.ANTI_DRAGON_SHIELD) && !owns(bot, Quest.ANTI_DRAGON_SHIELD) &&
            !hint(DRAGON_SLAYER_SHIELD, Dialogue.SHIELD_HINT)) return false
        if (!bot.inventory.contains(Quest.CRANDOR_MAP) && !bot.inventory.contains(Quest.ORACLE_MAP_PART) && !oraclePiece()) return false
        return leaveOracleRoom() && shield() && talk(Ship.KLARENSE, klarense, Ship.BUY_OPTION) { stage() >= Quest.SHIP_BOUGHT }
    }

    private suspend fun shield(): Boolean {
        val shield = Quest.ANTI_DRAGON_SHIELD
        if (bot.equipment.contains(shield)) return true
        if (!bot.inventory.contains(shield)) {
            if (bot.bank.contains(shield)) {
                if (!withdraw(listOf(Item(shield)))) return false
            } else if (!hint(DRAGON_SLAYER_SHIELD, Dialogue.SHIELD_HINT) ||
                !talk(Dialogue.DUKE, duke, Dialogue.SHIELD) { bot.inventory.contains(shield) }) return false
        }
        return handler.equipment.equip(shield)
    }

    private suspend fun withdraw(items: List<Item>): Boolean {
        if (!bot.inventory.hasSpaceForAll(items)) return false
        if (!bot.bank.isOpen) {
            val bank = handler.webWalkToNearestBank(attempts = 2, fallback = false) ?: return false
            val option = if (bank is Npc) 2 else 1
            if (!handler.interactions.interact(option, bank) || !await { bot.bank.isOpen }) return false
        }
        val withdrawn = handler.banking.withdrawAll(items)
        bot.overlays.closeWindows()
        return withdrawn
    }

    private suspend fun hint(flag: PersistentVarp, option: String): Boolean =
        flag(flag) > 0 || talk(Quest.OZIACH, oziach, option) { flag(flag) > 0 }

    private fun flag(flag: PersistentVarp): Int = bot.varpManager.getValue(flag)

    private suspend fun board(): Boolean {
        if (bot.y > 9_600 && bot.position.isWithinDistance(Position(3049, 9640, bot.z), 8)) return true
        if (bot.z == 1 && bot.position.isWithinDistance(Ship.deckLadder, 6)) return true
        val plank = objectAt(Ship.BOARD_PLANK, Ship.boardingPlank) ?: return false
        // The bridge is stored one plane above the dock. The native object packet resolves it from below.
        return walk(Ship.dock) && bot.output.sendObjectInteraction(1, plank) &&
            await { bot.z == 1 && bot.position.isWithinDistance(Ship.deckLadder, 6) && !bot.isLocked }
    }

    private suspend fun ladderToHold(): Boolean {
        if (bot.z == Ship.nedHold.z && bot.position.isWithinDistance(Ship.nedHold, 6)) return true
        if (!leaveHold()) return false
        return click(Ship.DECK_LADDER, Ship.deckLadder) { bot.z == Ship.nedHold.z }
    }

    private suspend fun leaveHold(): Boolean {
        val ladder = Position(3049, 9640, bot.z)
        if (bot.y <= 9_600 || !bot.position.isWithinDistance(ladder, 7)) return true
        return click(Ship.HOLD_LADDER, ladder) { bot.y < 3_300 }
    }

    private suspend fun leaveShip(): Boolean {
        if (!leaveHold()) return false
        if (bot.z != 1 || !bot.position.isWithinDistance(Ship.deckLadder, 7)) return true
        return click(Ship.LEAVE_PLANK, Ship.leavingPlank) { bot.z == 0 }
    }

    private suspend fun sail(): Boolean {
        if (!Ship.underway(bot)) {
            if (!board() || !ladderToHold()) return false
            val accepted = talk(Ship.NED_ON_SHIP, Ship.nedHold, Ship.SAIL_OPTION) {
                stage() >= Quest.SAILED_TO_CRANDOR || Ship.underway(bot)
            }
            if (!accepted && !Ship.underway(bot) && stage() < Quest.SAILED_TO_CRANDOR) return false
        }
        return await(20_000) { stage() >= Quest.SAILED_TO_CRANDOR && !bot.isLocked }
    }

    private suspend fun repair(): Boolean {
        if (bot.z != Ship.repairHold.z || !bot.position.isWithinDistance(Ship.repairHold, 8)) {
            if (!board()) return false
            val ladder = objectAt(Ship.DECK_LADDER, Ship.deckLadder) ?: return false
            if (!objectClick(ladder) || !await { bot.overlays.getOverlay(OptionDialogue::class.java) != null } ||
                !handler.widgets.clickDialogueOption(Ship.REPAIR_HOLD_OPTION) ||
                !await { bot.position.isWithinDistance(Ship.repairHold, 5) && !bot.isLocked }) return false
        }
        val hole = objectAt(Ship.HOLE, Position(3047, 9639, 1)) ?: return false
        repeat(3) {
            if (stage() >= Quest.SHIP_REPAIRED) return true
            val previous = flag(DRAGON_SLAYER_SHIP_REPAIRS)
            if (!handler.inventory.useItem(Ship.PLANK).onObject(hole) ||
                !await { stage() >= Quest.SHIP_REPAIRED || flag(DRAGON_SLAYER_SHIP_REPAIRS) > previous }) return false
        }
        return stage() >= Quest.SHIP_REPAIRED
    }

    private suspend fun collectMap(): Boolean {
        if (bot.inventory.contains(Quest.CRANDOR_MAP)) return true
        if (!bot.inventory.contains(Quest.MAZE_MAP_PART) && !maze()) return false
        if (!leaveMazeChestRoom()) return false
        if (!bot.inventory.contains(Quest.WORMBRAIN_MAP_PART) &&
            (!hint(DRAGON_SLAYER_WORMBRAIN, Dialogue.WORMBRAIN_HINT) ||
                !talk(MapPieces.WORMBRAIN, wormbrain, MapPieces.ASK_MAP, MapPieces.BUY_MAP,
                    reach = InteractionPolicy(InteractionType.LINE_OF_SIGHT, 3)) {
                    bot.inventory.contains(Quest.WORMBRAIN_MAP_PART)
                })) return false
        if (!bot.inventory.contains(Quest.ORACLE_MAP_PART) && !oraclePiece()) return false
        return handler.inventory.useItem(Quest.MAZE_MAP_PART).onItem(Quest.WORMBRAIN_MAP_PART) &&
            await { bot.inventory.contains(Quest.CRANDOR_MAP) }
    }

    private suspend fun leaveMazeChestRoom(): Boolean {
        if (bot.z != 0 || bot.x !in 2934..2941 || bot.y !in 9655..9660) return true
        return click(1755, Position(2939, 9657)) { bot.y < 9_600 }
    }

    private suspend fun maze(): Boolean {
        if (bot.mazeDoorsUsed == 0) {
            val key = Dialogue.MAZE_KEY
            if (!bot.inventory.contains(key) && !talk(Quest.OZIACH, oziach, Dialogue.MAZE) { bot.inventory.contains(key) }) return false
            if (!useDoor(MapPieces.MAZE_ENTRANCE, MapPieces.mazeEntranceTile, key) { bot.mazeDoorsUsed >= 1 }) return false
            if (!walk(Position(2939, 3248), 1)) return false
        }
        for (index in MapPieces.colouredKeys.indices) {
            if (bot.mazeDoorsUsed > index + 1) continue
            if (!mazeLevel(index)) return false
            val key = MapPieces.colouredKeys[index]
            if (!bot.inventory.contains(key) && !fightForKey(index, key)) return false
            if (!crossMazeDoor(index, key)) return false
        }
        return enterMazeChestRoom() &&
            search(MapPieces.MAZE_CHEST, MapPieces.MAZE_CHEST_OPEN, MapPieces.mazeChestTile, Quest.MAZE_MAP_PART)
    }

    private suspend fun enterMazeChestRoom(): Boolean {
        val door = mazeDoors.last()
        if (bot.z != door.z || !bot.position.isWithinDistance(door, 8) || bot.y > door.y) return true
        return crossMazeDoor(mazeDoors.lastIndex)
    }

    private suspend fun crossMazeDoor(index: Int, key: Int? = null): Boolean {
        val at = mazeDoors[index]
        val stand = mazeDoorStands[index]
        val id = MapPieces.mazeDoors[index]
        if (!await(6_000) { objectAt(id, at) != null }) return false
        val door = objectAt(id, at) ?: return false
        val opposite = at.translate(1, door.direction.toNormalDirection())
        val exit = if (stand == at) opposite else at
        if (key == null && bot.position == exit) return true
        if (!walkToMazeTile(stand)) return false
        val packetSent = if (key != null && bot.mazeDoorsUsed <= index + 1) {
            val slot = bot.inventory.computeIndexForId(key)
            slot >= 0 && bot.output.useItemOnObject(slot, key, door)
        } else bot.output.sendObjectInteraction(1, door)
        return packetSent && await(6_000) { bot.position == exit && bot.mazeDoorsUsed > index + 1 }
    }

    private suspend fun walkToMazeTile(at: Position): Boolean =
        (bot.position.isWithinDistance(at, 24) && bot.navigator.navigate(at, false).await() == NavigationResult.REACHED) || walk(at)

    private suspend fun climbFromMazeRoom(index: Int, stand: Position, ladder: Position): Boolean {
        if (!walkToMazeTile(stand) && (!crossMazeDoor(index - 1) || !walkToMazeTile(stand))) return false
        return click(1747, ladder) { bot.z > ladder.z }
    }

    private suspend fun mazeLevel(index: Int): Boolean {
        return when {
            index == 1 && bot.z == 0 -> climbFromMazeRoom(index, Position(2927, 3256), Position(2928, 3256))
            index == 2 && bot.z == 1 -> climbFromMazeRoom(index, Position(2933, 3254, 1), Position(2934, 3254, 1))
            index == 3 && bot.y < 9_600 -> {
                if (bot.z == 2 && (!walk(Position(2939, 3240, 2)) ||
                        !click(1746, Position(2940, 3240, 2)) { bot.z == 1 })) return false
                if (bot.z == 1 && (!walk(Position(2937, 3239, 1)) ||
                        !click(1746, Position(2937, 3240, 1)) { bot.z == 0 })) return false
                click(2605, Position(2932, 3240)) { bot.y > 9_600 }
            }
            else -> true
        }
    }

    private suspend fun fightForKey(index: Int, key: Int): Boolean {
        val anchor = mazeNpcs[index]
        fun available() = bot.world.locator.findItems(anchor, 14) { it.id == key && it.isVisibleTo(bot) }.firstOrNull()
        if (available() == null) {
            val enemy = npcAt(MapPieces.mazeKeyNpcs[index], anchor) ?: return false
            if (!fight(enemy) { !enemy.isAlive }) return false
        }
        if (!await { available() != null }) return false
        return available()?.let { handler.loot.pickup(it) } == true && bot.inventory.contains(key)
    }

    private suspend fun oraclePiece(): Boolean {
        if (!MapPieces.oracleOfferingMade(bot)) {
            if (!hint(DRAGON_SLAYER_ORACLE, Dialogue.ORACLE_HINT) ||
                !talk(Dialogue.ORACLE, oracle, Dialogue.ORACLE_MAP) { flag(DRAGON_SLAYER_ORACLE) >= 2 } ||
                !useDoor(MapPieces.MAGIC_DOOR, MapPieces.magicDoorTile, MapPieces.offerings.first().id) {
                    MapPieces.oracleOfferingMade(bot)
                }) return false
        }
        return search(MapPieces.ORACLE_CHEST, MapPieces.ORACLE_CHEST_OPEN, MapPieces.oracleChestTile, Quest.ORACLE_MAP_PART) &&
            leaveOracleRoom()
    }

    private suspend fun leaveOracleRoom(): Boolean {
        val door = MapPieces.magicDoorTile
        if (bot.z != door.z || bot.x !in door.x..3059 || bot.y !in 9838..9844) return true
        if (bot.navigator.navigate(door, false).await() != NavigationResult.REACHED) return false
        return click(MapPieces.MAGIC_DOOR, door) { bot.x < door.x }
    }

    private suspend fun useDoor(id: Int, at: Position, item: Int, done: () -> Boolean): Boolean {
        val door = objectAt(id, at) ?: return false
        if (!handler.inventory.useItem(item).onObject(door)) return false
        val approach = bot.position
        if (!await { done() && !bot.isLocked }) return false
        await(3_000) { bot.position != approach && bot.walking.isEmpty }
        return true
    }

    private suspend fun search(closed: Int, open: Int, at: Position, piece: Int): Boolean {
        if (objectAt(open, at) == null && !click(closed, at) { objectAt(open, at) != null }) return false
        return click(open, at) { bot.inventory.contains(piece) }
    }

    private suspend fun fightElvarg(): Boolean {
        if (!shield()) return false
        if (bot.y < 9_600 && !click(2609, rock) { bot.y > 9_600 }) return false
        if (bot.z == lairGate.z && bot.x < lairGate.x && bot.position.isWithinDistance(lairGate, 35)) {
            val outside = lairGate.translate(-1, 0)
            if (bot.navigator.navigate(outside, false).await() != NavigationResult.REACHED && !walk(outside)) return false
            if (!click(2607, lairGate) { bot.x >= lairGate.x }) return false
        }
        val enemy = npcAt(Quest.ELVARG, elvarg) ?: return false
        return fight(enemy) { !enemy.isAlive } && await { stage() == Quest.COMPLETE }
    }

    private suspend fun fight(enemy: Npc, done: () -> Boolean): Boolean {
        val reach = bot.combat.getDefaultAttack(enemy)?.interactionPolicy ?: InteractionPolicy.STANDARD_SIZE
        if (!approachNpc(enemy, reach) { live() && bot.healthPercent >= 35 } ||
            !bot.output.sendNpcInteraction(3, enemy)) return done()
        return await(85_000) {
            if (!done() && bot.healthPercent < 65) BossFood.eat(bot, food)
            done()
        }
    }

    private suspend fun talk(id: Int, at: Position, vararg options: String,
                             reach: InteractionPolicy = InteractionPolicy.STANDARD_SIZE, done: () -> Boolean): Boolean {
        if (done()) return true
        val npc = npcAt(id, at) ?: return false
        if (!approachNpc(npc, reach, ::live)) return false
        repeat(2) {
            if (BotNpcDialogues.respond(bot, npc, options.toList(), completed = done) {
                    approachLocally(npc, reach) && handler.interactions.interact(1, npc, reach, ::live)
                } || done()) return true
            if (!live() || bot.isLocked) return false
        }
        return false
    }

    private suspend fun approachNpc(npc: Npc, reach: InteractionPolicy, allowed: () -> Boolean): Boolean {
        repeat(3) {
            if (!allowed() || !npc.isAlive) return false
            if (approachLocally(npc, reach) || handler.interactions.approach(npc, reach, timeout = 255.seconds,
                    allowOutOfView = true, canContinue = allowed)) return allowed()
            delay(600)
        }
        return false
    }

    private suspend fun click(id: Int, at: Position, done: () -> Boolean): Boolean {
        if (done()) return true
        val obj = objectAt(id, at) ?: return false
        return objectClick(obj) && await { done() && !bot.isLocked }
    }

    private suspend fun objectClick(obj: GameObject): Boolean {
        val policy = InteractionPolicy.STANDARD_SIZE
        return (approachLocally(obj, policy) ||
            handler.interactions.approach(obj, policy, timeout = 120.seconds, canContinue = ::live)) &&
            bot.output.sendObjectInteraction(1, obj)
    }

    private suspend fun approachLocally(target: Entity, policy: InteractionPolicy): Boolean {
        if (bot.isLocked || bot.z != target.position.z || !bot.position.isWithinDistance(target.position, 24)) return false
        val collision = bot.world.collisionManager
        if (collision.reached(bot, target, policy)) return true
        val stands = handler.interactions.interactionStands(target, policy)
            .sortedBy { it.computeLongestDistance(bot.position) }.take(4)
        for (stand in stands) {
            if (bot.navigator.navigate(stand, false).await() == NavigationResult.REACHED &&
                collision.reached(bot, target, policy)) return true
        }
        return false
    }

    private suspend fun walk(at: Position, radius: Int = 0): Boolean =
        WebWalker.webWalk(bot, at, radius = radius, attempts = 2, bank = BankAccess.NONE, fallback = false).success

    private fun npcAt(id: Int, at: Position): Npc? = bot.world.locator.findNpcs(at, 12) {
        it.id == id && it.isAlive && it.state == EntityState.ACTIVE
    }.minByOrNull { it.position.computeLongestDistance(at) }

    private fun objectAt(id: Int, at: Position): GameObject? = bot.world.locator.findObjectsOnTile(at) {
        it.id == id && it.state == EntityState.ACTIVE
    }.firstOrNull()

    private suspend fun await(timeout: Long = 15_000L, done: suspend () -> Boolean): Boolean = withTimeoutOrNull(timeout) {
        while (live()) {
            if (done()) return@withTimeoutOrNull true
            delay(150)
        }
        false
    } == true
}
