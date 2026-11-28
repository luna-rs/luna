package game.bot.scripts.quests

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotTaverleyKeys
import api.bot.action.trading.ExactBotTrade
import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.combat.death.DeathHookHandler.deathItems
import game.content.heroesQuest.HeroesGang as Gang
import game.content.heroesQuest.HeroesProofs
import game.content.heroesQuest.HeroesQuest as Quest
import game.content.questRewards.QuestRewards.owns
import game.content.sailing.Entrana
import game.content.shieldOfArrav.ShieldOfArrav
import game.obj.doors.TaverleyJailDoor
import game.player.item.consume.food.Food
import game.player.item.consume.potion.Potion
import game.skill.cooking.cookFood.Cooking
import game.skill.cooking.cookFood.CookingInterface
import game.skill.fishing.catchFish.Tool
import game.skill.herblore.makePotion.FinishedPotion
import game.skill.magic.CombinationRune
import game.skill.magic.Rune
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Collects all three proofs through the same two-gang and skilling interactions available to players.
 *
 * @author lare96
 */
class HeroesQuestBotScript(bot: Bot,
                           private val data: QuestAttemptData = QuestAttemptData(60 * 60_000L)) : BotScript(bot) {
    companion object {
        private const val GERRANT = 558
        private const val LAVA_SPOT = 800
        private const val FISHING_BAIT = 313
        private const val HARRALANDER = 255
        private const val WATER = 227
        private const val JAILER = 201
        private const val QUEST_COOLDOWN_MS = 30 * 60_000L
        private val achietties = Position(2903, 3510)
        private val firebird = Position(2847, 3386)
        private val iceQueen = Position(2866, 9956)
        private val lavaShore = SubZone.TAVERLY_LAVA_EEL_FISHING.inside
        private val lavaReach = InteractionPolicy(InteractionType.LINE_OF_SIGHT, 1)
        private val jailer = Position(2933, 9693)
        private val gerrant = Position(3012, 3223)
        private val kitchen = Position(2790, 3191)
        private val alfonse = Position(2793, 3188)
        private val grubor = Position(2812, 3171)
        private val trobert = Position(2806, 3174)
        private val garv = Position(2776, 3186)
        private val grip = Position(2774, 3192)
        private val meeting = Position(2776, 3186)
        private val katrine = Position(3186, 3385)
        private val straven = Position(3246, 9780)
        private val cookTown = Position(2811, 3444)
        private val cookApproach = Position(2816, 3438)
        private val armour = listOf(1165, 1125, 1077)
        private val oil = FinishedPotion.BLAMISH_OIL
        private val rod = Tool.FISHING_ROD.id
        private val oilyRod = Tool.LAVA_EEL_FISHING_ROD.id
        private val questDrops = setOf(Gang.TREASURE_KEY, TaverleyJailDoor.JAIL_CELL.key,
            Quest.ICE_GLOVES, Quest.FIRE_FEATHER)
        private val questSupplies = setOf(Currency.COINS.id, FISHING_BAIT, HARRALANDER, WATER, rod, oilyRod, oil.id,
            oil.secondary, oil.unf, Quest.ARMBAND, Quest.LAVA_EEL, Quest.RAW_LAVA_EEL, Quest.ICE_GLOVES,
            Quest.FIRE_FEATHER, Gang.PAPERS, Gang.SIDE_KEY, Gang.TREASURE_KEY, Gang.CANDLESTICK,
            BotTaverleyKeys.DUSTY_KEY, TaverleyJailDoor.JAIL_CELL.key)
        private var Bot.heroesRetryAt by Attr.long().persist("heroes_quest_bot_retry_at")

        fun eligible(bot: Bot): Boolean = Quest.stage(bot) < Quest.COMPLETE && ActionSafety.NON_COMBAT.allows(bot) &&
            !bot.overlays.hasWindow() && System.currentTimeMillis() >= bot.heroesRetryAt &&
            (Quest.isInProgress(bot) || Quest.canStart(bot)) &&
            (owns(bot, Quest.FIRE_FEATHER) || bot.skill(Skill.MINING).staticLevel >= 50 ||
                owns(bot, Quest.ICE_GLOVES)) &&
            (owns(bot, Quest.LAVA_EEL) || bot.skill(Skill.FISHING).staticLevel >= 53 &&
                bot.skill(Skill.COOKING).staticLevel >= 53 && owns(bot, FISHING_BAIT) &&
                (owns(bot, oilyRod) || owns(bot, rod) &&
                    (owns(bot, oil.id) || bot.skill(Skill.HERBLORE).staticLevel >= 25 &&
                        (owns(bot, oil.unf) || owns(bot, HARRALANDER) && owns(bot, WATER))))) &&
            (Gang.phoenix(bot) || armour.all { owns(bot, it) })
    }

    private val actions = QuestBotActions(bot)
    private var objective = "speak to Achietties"
    override val actionSafety = ActionSafety.COMBAT
    override val allowsCombatResponse = false
    var failure: String? = null
        private set

    override fun snapshot() = data

    override fun allowsLoot(item: GroundItem): Boolean = item.id in questDrops || item in bot.deathItems

    override suspend fun run(): Boolean {
        if (stage() == Quest.COMPLETE) return true
        suspend fun perform(label: String, action: suspend () -> Boolean): Boolean {
            objective = label
            bot.log("Heroes' Quest: $label.")
            repeat(3) {
                if (!actions.live()) return false
                if (action()) return true
                delay(600)
            }
            return false
        }
        val finished = withTimeoutOrNull(data.deadline - System.currentTimeMillis()) {
            if (stage() == Quest.NOT_STARTED && !perform("start the quest") {
                    actions.talk(Quest.ACHIETTIES, achietties, Quest.APPLY) { stage() > Quest.NOT_STARTED }
                }) return@withTimeoutOrNull false
            while (actions.live() && !actions.has(Quest.ARMBAND)) {
                if (!perform("complete the gang proof at stage ${stage()}", ::gangStep)) return@withTimeoutOrNull false
            }
            if (!perform("catch and cook a lava eel", ::collectEel)) return@withTimeoutOrNull false
            if (!perform("obtain the fire feather", ::collectFeather)) return@withTimeoutOrNull false
            perform("give Achietties the proofs") {
                actions.talk(Quest.ACHIETTIES, achietties, Quest.OFFER_PROOFS) { stage() == Quest.COMPLETE }
            }
        } == true
        if (!finished && stage() != Quest.COMPLETE) {
            failure = "Could not $objective at ${bot.position}, stage ${stage()}."
            bot.log("Heroes' Quest: $failure")
            bot.heroesRetryAt = System.currentTimeMillis() + QUEST_COOLDOWN_MS
        }
        return true
    }

    private fun stage() = Quest.stage(bot)

    private suspend fun gangStep(): Boolean {
        if (bot.bank.contains(Quest.ARMBAND)) return retrieveBanked(listOf(Quest.ARMBAND))
        val stage = stage()
        val phoenix = Gang.phoenix(bot)
        val leader = if (phoenix) ShieldOfArrav.STRAVEN else ShieldOfArrav.KATRINE
        val leaderTile = if (phoenix) straven else katrine
        val progressed = { stage() > stage }
        return when (stage) {
            Quest.STARTED -> actions.talk(leader, leaderTile, Gang.ASK_ARMBAND, done = progressed)
            Quest.PHOENIX_BRIEFED -> actions.talk(Gang.ALFONSE, alfonse, Gang.PHOENIX_PASSWORD, done = progressed)
            Quest.PHOENIX_SPOKE_TO_ALFONSE ->
                actions.talk(Gang.CHARLIE, kitchen, Gang.ASK_CHARLIE, done = progressed)
            Quest.PHOENIX_SPOKE_TO_CHARLIE -> shootGrip()
            Quest.PHOENIX_KILLED_GRIP -> {
                (actions.has(Gang.CANDLESTICK) || trade(emptyList(), listOf(Item(Gang.CANDLESTICK)))) &&
                    actions.talk(leader, leaderTile, done = progressed)
            }
            Quest.BLACK_ARM_BRIEFED -> actions.talk(Gang.GRUBOR, grubor, Gang.BLACK_PASSWORD, done = progressed)
            Quest.BLACK_ARM_GAVE_PASSWORD ->
                actions.talk(Gang.TROBERT, trobert, Gang.ASK_TROBERT, done = progressed)
            Quest.BLACK_ARM_OBTAINED_PAPERS -> {
                recoverPapers() && retrieveBanked(armour) && equipDisguise() &&
                    actions.talk(Gang.GARV, garv, Gang.SHOW_DISGUISE, done = progressed)
            }
            Quest.BLACK_ARM_PASSED_DISGUISE -> recoverPapers() && actions.talk(Gang.GRIP, grip, done = progressed)
            Quest.BLACK_ARM_GAVE_PAPERS -> lureGripAndSearch()
            Quest.BLACK_ARM_LOOTED_CHEST -> {
                if (!actions.has(Gang.CANDLESTICK) && !lureGripAndSearch()) return false
                if (actions.has(Gang.CANDLESTICK, 2) && !trade(listOf(Item(Gang.CANDLESTICK)), emptyList())) return false
                actions.talk(leader, leaderTile, done = progressed)
            }
            Quest.PHOENIX_OBTAINED_ARMBAND, Quest.BLACK_ARM_OBTAINED_ARMBAND ->
                actions.talk(leader, leaderTile) { actions.has(Quest.ARMBAND) }
            else -> false
        }
    }

    private suspend fun recoverPapers(): Boolean = actions.has(Gang.PAPERS) ||
        if (bot.bank.contains(Gang.PAPERS)) retrieveBanked(listOf(Gang.PAPERS))
        else actions.talk(Gang.TROBERT, trobert, Gang.ASK_TROBERT) { actions.has(Gang.PAPERS) }

    private suspend fun equipDisguise(): Boolean {
        for (id in armour) {
            if (!bot.equipment.contains(id) && !handler.equipment.equip(id)) return false
        }
        return true
    }

    private suspend fun shootGrip(): Boolean {
        if (!Gang.sideKeyUsed(bot)) {
            if (!actions.has(Gang.SIDE_KEY) && !trade(emptyList(), listOf(Item(Gang.SIDE_KEY)))) return false
            if (!actions.walk(Gang.kitchenWall.translate(1, 0)) ||
                !actions.click(Gang.KITCHEN_WALL, Gang.kitchenWall) ||
                !actions.await { bot.x < Gang.kitchenWall.x }) return false
            val door = actions.objectAt(Gang.SIDE_DOOR, Gang.sideDoor) ?: return false
            if (!handler.inventory.useItem(Gang.SIDE_KEY).onObject(door) ||
                !actions.await { Gang.sideKeyUsed(bot) }) return false
        }
        if (!actions.walk(Gang.firingTile)) return false
        if (!actions.await(180_000L) { actions.npc(Gang.GRIP, Gang.lure)?.position == Gang.lure }) return false
        val target = actions.npc(Gang.GRIP, Gang.lure) ?: return false
        return bot.output.sendNpcInteraction(3, target) && actions.await(50_000L) { stage() >= Quest.PHOENIX_KILLED_GRIP }
    }

    private suspend fun lureGripAndSearch(): Boolean {
        if (!actions.has(Gang.TREASURE_KEY) && !actions.loot(Gang.TREASURE_KEY, Gang.lure)) {
            if (!partnerAtFiringTile() && !partnerHasSideKey() && !giveSideKey()) return false
            if (!actions.await(120_000L) { partnerAtFiringTile() }) return false
            val box = if (actions.objectAt(Gang.CABINET_OPEN, Gang.cabinet) != null) Gang.CABINET_OPEN else Gang.CABINET
            if (!actions.click(box, Gang.cabinet) || !actions.await { optionAvailable(Gang.LURE) } ||
                !handler.widgets.clickDialogueOption(Gang.LURE) ||
                !actions.loot(Gang.TREASURE_KEY, Gang.lure, wait = 50_000L)) return false
        }
        if (!Gang.treasureKeyUsed(bot)) {
            val door = actions.objectAt(Gang.TREASURE_DOOR, Gang.treasureDoor) ?: return false
            if (!handler.inventory.useItem(Gang.TREASURE_KEY).onObject(door) ||
                !actions.await { Gang.treasureKeyUsed(bot) }) return false
        }
        if (actions.objectAt(Gang.CHEST_OPEN, Gang.chest) == null &&
            (!actions.click(Gang.CHEST, Gang.chest) || !actions.await { actions.objectAt(Gang.CHEST_OPEN, Gang.chest) != null })) {
            return false
        }
        return actions.click(Gang.CHEST_OPEN, Gang.chest) && actions.await { actions.has(Gang.CANDLESTICK) }
    }

    private suspend fun giveSideKey(): Boolean {
        if (!actions.has(Gang.SIDE_KEY) &&
            !actions.talk(Gang.GRIP, grip, Gang.ASK_DUTIES, Gang.ASK_KEY) { actions.has(Gang.SIDE_KEY) }) return false
        return trade(listOf(Item(Gang.SIDE_KEY)), emptyList())
    }

    private fun partnerAtFiringTile(): Boolean = bot.world.locator.findPlayers(Gang.firingTile, 2) {
        it !== bot && it.isAlive && Gang.isInFiringRoom(it) && Gang.phoenix(it) && Gang.sideKeyUsed(it) &&
            Quest.stage(it) in Quest.PHOENIX_SPOKE_TO_CHARLIE..Quest.PHOENIX_KILLED_GRIP
    }.isNotEmpty()

    private fun partnerHasSideKey(): Boolean = bot.world.locator.findPlayers(meeting, 24) {
        it !== bot && it.isAlive && Gang.phoenix(it) &&
            Quest.stage(it) in Quest.PHOENIX_SPOKE_TO_CHARLIE..Quest.PHOENIX_KILLED_GRIP &&
            (Gang.sideKeyUsed(it) || it.inventory.contains(Gang.SIDE_KEY))
    }.isNotEmpty()

    private suspend fun trade(give: List<Item>, receive: List<Item>): Boolean {
        val phoenix = Gang.phoenix(bot)
        val blackArmTile = meeting.translate(-1, 0)
        if (!actions.walk(if (phoenix) meeting else blackArmTile)) return false
        val partnerTile = if (phoenix) blackArmTile else meeting
        var partner: Bot? = null
        if (!actions.await(150_000L) {
                partner = bot.world.locator.findPlayers(partnerTile, 1) {
                    it is Bot && it !== bot && it.isAlive && it.position == partnerTile && it.walking.isEmpty &&
                        Gang.phoenix(it) != phoenix &&
                        it.scriptStack.findActive(HeroesQuestBotScript::class.java).isPresent &&
                        (if (phoenix) Quest.stage(it) in Quest.BLACK_ARM_GAVE_PAPERS..Quest.BLACK_ARM_LOOTED_CHEST
                         else Quest.stage(it) in Quest.PHOENIX_SPOKE_TO_CHARLIE..Quest.PHOENIX_KILLED_GRIP) &&
                        receive.all(it.inventory::contains)
                }.filterIsInstance<Bot>().minByOrNull { it.username }
                partner != null
            }) return false
        return ExactBotTrade.exchange(bot, partner ?: return false, give, receive) { actions.live() }
    }

    private suspend fun collectFeather(): Boolean {
        if (actions.has(Quest.FIRE_FEATHER) || bot.bank.contains(Quest.FIRE_FEATHER) &&
            retrieveBanked(listOf(Quest.FIRE_FEATHER))) return true
        if (!reserveInventory(2)) return false
        if (!bot.equipment.contains(Quest.ICE_GLOVES)) {
            if (!actions.has(Quest.ICE_GLOVES) && !retrieveBanked(listOf(Quest.ICE_GLOVES))) return false
            if (!actions.has(Quest.ICE_GLOVES) &&
                (!actions.walk(SubZone.ICE_QUEEN_LAIR.inside, 2) ||
                    !collectDrop(Quest.ICE_GLOVES, Quest.ICE_QUEEN, iceQueen))) return false
            if (!handler.equipment.equip(Quest.ICE_GLOVES)) return false
        }
        return bankForbiddenGear() && actions.walk(firebird, 5) &&
            collectDrop(Quest.FIRE_FEATHER, Quest.FIRE_BIRD, firebird)
    }

    private suspend fun collectDrop(item: Int, npc: Int, area: Position): Boolean {
        if (actions.has(item) || actions.loot(item, area)) return true
        if (!actions.await(90_000L) {
                actions.has(item) || actions.npc(npc, area)?.let {
                    !it.combat.inCombat() || bot.combat.target === it
                } == true
            }) return false
        return actions.fight(npc, area) { actions.has(item) } &&
            (actions.has(item) || actions.loot(item, area, wait = 12_000L))
    }

    private suspend fun collectEel(): Boolean {
        if (actions.has(Quest.LAVA_EEL) || bot.bank.contains(Quest.LAVA_EEL) && retrieveBanked(listOf(Quest.LAVA_EEL))) return true
        if (!actions.has(Quest.RAW_LAVA_EEL)) {
            val keySlots = when {
                actions.has(BotTaverleyKeys.DUSTY_KEY) -> 0
                bot.bank.contains(BotTaverleyKeys.DUSTY_KEY) || actions.has(TaverleyJailDoor.JAIL_CELL.key) -> 1
                else -> 2
            }
            if (!reserveInventory(1 + keySlots) || !prepareRod() || !retrieveBanked(listOf(FISHING_BAIT))) return false
            if (!actions.walk(lavaShore, 2) && (!prepareDustyKey() || !actions.walk(lavaShore, 2))) return false
            val spot = bot.world.locator.findNpcs(bot, 8) {
                it.id == LAVA_SPOT && it.z == bot.z && it.state == EntityState.ACTIVE
            }.minByOrNull { it.position.computeLongestDistance(bot.position) } ?: return false
            val batch = minOf(3, bot.inventory.computeRemainingSize(), bot.inventory.computeAmountForId(FISHING_BAIT))
            if (batch == 0 || !actions.clickNpc(spot, 1, lavaReach)) return false
            actions.await(90_000L) { actions.has(Quest.RAW_LAVA_EEL, batch) }
            if (!actions.has(Quest.RAW_LAVA_EEL)) return false
        }
        val range = bot.world.locator.findObjects(cookTown, 24) {
            it.id in Cooking.COOKING_OBJECTS && it.position.z == cookTown.z && it.state == EntityState.ACTIVE
        }.minByOrNull { it.position.computeLongestDistance(cookTown) } ?: return false
        if (!bot.position.isWithinDistance(range.position, 6) && !actions.walk(cookApproach)) return false
        while (actions.has(Quest.RAW_LAVA_EEL) && !actions.has(Quest.LAVA_EEL)) {
            val before = bot.inventory.computeAmountForId(Quest.RAW_LAVA_EEL)
            if (!handler.inventory.useItem(Quest.RAW_LAVA_EEL).onObject(range) ||
                !actions.await { bot.overlays.has(CookingInterface::class.java) } ||
                !bot.output.clickButton(13720) ||
                !actions.await { bot.inventory.computeAmountForId(Quest.RAW_LAVA_EEL) < before }) return false
        }
        return actions.has(Quest.LAVA_EEL)
    }

    private suspend fun prepareDustyKey(): Boolean {
        val dustyKey = BotTaverleyKeys.DUSTY_KEY
        if (actions.has(dustyKey)) return false
        if (bot.bank.contains(dustyKey)) return retrieveBanked(listOf(dustyKey))
        val jailKey = TaverleyJailDoor.JAIL_CELL.key
        if (!retrieveBanked(listOf(jailKey)) ||
            !bot.position.isWithinDistance(jailer, 12) && !actions.walk(jailer, 4)) return false
        if (!collectDrop(jailKey, JAILER, jailer)) return false
        if (!actions.await(15_000L) { ActionSafety.NON_COMBAT.allows(bot) }) return false
        val carried = bot.inventory.filterNotNull().mapTo(mutableSetOf()) { it.id }
        return BotTaverleyKeys.visit(bot, carried, actions::live, onAttack = null) ==
            BotTaverleyKeys.VisitResult.OBTAINED
    }

    private suspend fun prepareRod(): Boolean {
        if (!retrieveBanked(listOf(oilyRod, rod, oil.id, oil.secondary, oil.unf, HARRALANDER, WATER))) return false
        if (actions.has(oilyRod)) return true
        if (!actions.has(rod)) return false
        if (!actions.has(oil.id)) {
            if (!actions.has(oil.secondary) &&
                !actions.talk(GERRANT, gerrant, HeroesProofs.ASK_ABOUT_EELS) { actions.has(oil.secondary) }) return false
            if (!actions.has(oil.unf) && !makeOne(HARRALANDER, WATER, oil.unf)) return false
            if (!makeOne(oil.unf, oil.secondary, oil.id)) return false
        }
        return handler.inventory.useItem(oil.id).onItem(rod) && actions.await { actions.has(oilyRod) }
    }

    private suspend fun makeOne(first: Int, second: Int, result: Int): Boolean {
        if (!handler.inventory.useItem(first).onItem(second) ||
            !actions.await { bot.overlays.has(MakeItemDialogue::class.java) }) return false
        handler.widgets.clickMakeItem(0, 1)
        return actions.await { actions.has(result) }
    }

    private suspend fun openBank(): Boolean {
        if (bot.bank.isOpen) return true
        val bank = handler.webWalkToNearestBank(attempts = 2, fallback = false) ?: return false
        return handler.banking.open(bank)
    }

    private suspend fun reserveInventory(slots: Int): Boolean {
        if (bot.inventory.computeRemainingSize() >= slots) return true
        if (!openBank()) return false
        try {
            val candidates = bot.inventory.filterNotNull().filter { item ->
                val id = item.id
                id !in questSupplies && item.equipDef == null && id !in Food.ID_TO_FOOD &&
                    id !in Potion.DOSE_TO_POTION && id !in Rune.ID_TO_RUNE && id !in CombinationRune.ID_TO_RUNE
            }.map { it.id }.distinct()
            for (id in candidates) {
                if (bot.inventory.computeRemainingSize() >= slots) break
                if (!handler.banking.depositAll(id)) return false
            }
            return bot.inventory.computeRemainingSize() >= slots
        } finally {
            handler.widgets.clickCloseInterface()
        }
    }

    private suspend fun retrieveBanked(ids: List<Int>): Boolean {
        val needed = ids.filter { bot.bank.contains(it) && !actions.has(it) && !bot.equipment.contains(it) }
        if (needed.isEmpty()) return true
        if (!openBank()) return false
        return try {
            handler.banking.withdrawAll(needed.map {
                Item(it, if (it == FISHING_BAIT) minOf(10, bot.bank.computeAmountForId(it)) else 1)
            })
        } finally {
            handler.widgets.clickCloseInterface()
        }
    }

    private suspend fun bankForbiddenGear(): Boolean {
        if (!Entrana.isCarryingForbidden(bot)) return true
        if (!openBank()) return false
        try {
            for (id in bot.inventory.filterNotNull().filter(Entrana::isForbidden).map { it.id }.distinct()) {
                if (!handler.banking.depositAll(id)) return false
            }
            val equipped = bot.equipment.filterNotNull().filter(Entrana::isForbidden)
            for (item in equipped) {
                val slot = item.equipDef?.index ?: return false
                if (!handler.equipment.unequip(slot) || !handler.banking.depositAll(item.id)) return false
            }
            return !Entrana.isCarryingForbidden(bot)
        } finally {
            handler.widgets.clickCloseInterface()
        }
    }

    private fun optionAvailable(option: String) =
        bot.overlays.getOverlay(OptionDialogue::class.java)?.options?.contains(option) == true
}
