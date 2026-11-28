package game.content.heroesQuest

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.ext.addItem
import api.predef.ext.scheduleOnce
import game.content.heroesQuest.HeroesQuest as Quest
import game.content.questRewards.QuestRewards.owns
import game.content.shieldOfArrav.ShieldOfArrav
import game.obj.doors.Doors
import game.obj.doors.QuestDoors
import game.obj.entrances.Entrances
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject

/**
 * The Brimhaven caper. The disguise, keys and cooperation govern the candlestick proof.
 *
 * @author lare96
 */
object HeroesGang {
    const val GARV = 788
    const val GRUBOR = 789
    const val TROBERT = 790
    const val GRIP = 792
    const val ALFONSE = 793
    const val CHARLIE = 794
    const val CANDLESTICK = 1577
    const val PAPERS = 1584
    const val SIDE_KEY = 1586
    const val TREASURE_KEY = 1588
    const val SIDE_DOOR = 2622
    const val TREASURE_DOOR = 2621
    const val KITCHEN_WALL = 2629
    const val CHEST = 2632
    const val CHEST_OPEN = 2633
    const val CABINET = 2635
    const val CABINET_OPEN = 2636
    const val ASK_ARMBAND = "Can you help me find a master thief's armband?"
    const val BLACK_PASSWORD = "Four-leaf clover."
    const val PHOENIX_PASSWORD = "Gherkin."
    const val ASK_CHARLIE = "Can you help me get into Pete's mansion?"
    const val ASK_TROBERT = "How can I get into Pete's mansion?"
    const val SHOW_DISGUISE = "I'm Hartigen, the new deputy."
    const val ASK_DUTIES = "What are my duties?"
    const val ASK_KEY = "Is there anything I can do now?"
    const val LURE = "Take a look in the cabinet."

    val sideDoor = Position(2781, 3197)
    val treasureDoor = Position(2764, 3197)
    val kitchenWall = Position(2787, 3190)
    val chest = Position(2766, 3199)
    val cabinet = Position(2775, 3196)
    val lure = Position(2777, 3198)
    val firingTile = Position(2780, 3198)
    private val gripHome = Position(2774, 3192)
    private val cabinetRoomDoor = Position(2777, 3196)
    private const val AMBUSH_TICKS = 75
    private var Player.heroSideKeyUsed by Attr.boolean().persist("heroes_quest_side_key_used")
    private var Player.heroTreasureKeyUsed by Attr.boolean().persist("heroes_quest_treasure_key_used")
    private val ambushes = HashMap<Npc, Ambush>()

    /**
     * The eligible accomplices at the time Grip investigates an open cabinet.
     *
     * @author lare96
     */
    private class Ambush(val inside: Player, val shooter: Player) {
        var holdingGrip = false
    }

    fun phoenix(player: Player): Boolean = ShieldOfArrav.phoenixStage(player) == ShieldOfArrav.PHOENIX_COMPLETE

    fun blackArm(player: Player): Boolean = ShieldOfArrav.blackArmStage(player) == ShieldOfArrav.BLACK_ARM_COMPLETE

    fun sideKeyUsed(player: Player): Boolean = player.heroSideKeyUsed

    fun treasureKeyUsed(player: Player): Boolean = player.heroTreasureKeyUsed

    fun isInFiringRoom(player: Player): Boolean =
        player.z == firingTile.z && player.x in 2780..2781 && player.y in 3198..3199

    fun speakToGangLeader(player: Player, npc: Npc, forPhoenix: Boolean): Boolean {
        if (!Quest.isInProgress(player) || if (forPhoenix) !phoenix(player) else !blackArm(player)) return false
        val stage = Quest.stage(player)
        val first = if (forPhoenix) Quest.PHOENIX_BRIEFED else Quest.BLACK_ARM_BRIEFED
        val bringCandlestick = if (forPhoenix) Quest.PHOENIX_KILLED_GRIP else Quest.BLACK_ARM_LOOTED_CHEST
        val armband = if (forPhoenix) Quest.PHOENIX_OBTAINED_ARMBAND else Quest.BLACK_ARM_OBTAINED_ARMBAND
        val dialogue = player.newDialogue().npc(npc.id, "Back again?")
        when (stage) {
            Quest.STARTED -> dialogue.options(ASK_ARMBAND, {
                val instructions = if (forPhoenix) "Tell Alfonse in Brimhaven the password: $PHOENIX_PASSWORD"
                                   else "Tell Grubor at our Brimhaven hideout: $BLACK_PASSWORD"
                it.newDialogue().npc(npc.id, "Bring me one of Scarface Pete's candlesticks and I'll give you an armband.",
                    instructions).then { member ->
                        if (Quest.nearby(member, npc)) Quest.advance(member, Quest.STARTED, first)
                    }.open()
            }, "Just passing by.", {})
            bringCandlestick -> {
                if (player.inventory.contains(CANDLESTICK)) {
                    dialogue.npc(npc.id, "That's one of Pete's candlesticks. Here's the armband you earned.")
                        .then {
                            if (Quest.stage(it) == bringCandlestick && Quest.nearby(it, npc) &&
                                it.inventory.remove(CANDLESTICK)) {
                                if (!owns(it, Quest.ARMBAND)) it.inventory.add(Quest.ARMBAND)
                                Quest.advance(it, bringCandlestick, armband)
                            }
                        }
                } else dialogue.npc(npc.id, "I'll need one of Pete's candlesticks first.")
            }
            armband -> {
                dialogue.npc(npc.id, "Take good care of that armband.")
                if (!owns(player, Quest.ARMBAND)) dialogue.then {
                    if (Quest.stage(it) == armband && Quest.nearby(it, npc) && !owns(it, Quest.ARMBAND)) {
                        it.inventory.add(Quest.ARMBAND)
                    }
                }
            }
            else -> dialogue.npc(npc.id, "How are things in Brimhaven?")
        }
        dialogue.open()
        return true
    }

    fun alfonse(player: Player, npc: Npc, openMenu: (Player) -> Unit): Boolean {
        if (Quest.stage(player) != Quest.PHOENIX_BRIEFED || !phoenix(player)) return false
        player.newDialogue().npc(ALFONSE, "Can I get you a table?")
            .options(PHOENIX_PASSWORD, {
                it.newDialogue().npc(ALFONSE, "Go through to the kitchen and speak to Charlie.")
                    .then { guest ->
                        if (Quest.nearby(guest, npc)) {
                            Quest.advance(guest, Quest.PHOENIX_BRIEFED, Quest.PHOENIX_SPOKE_TO_ALFONSE)
                        }
                    }.open()
            }, "I'd like to see your menu.", { openMenu(it) })
            .open()
        return true
    }

    fun charlie(player: Player, npc: Npc) {
        val dialogue = player.newDialogue().npc(CHARLIE, "Mind the kitchen.")
        if (Quest.stage(player) == Quest.PHOENIX_SPOKE_TO_ALFONSE && phoenix(player)) {
            dialogue.options(ASK_CHARLIE, {
                it.newDialogue().npc(CHARLIE, "Push the wall beside the kitchen door to reach the passage.",
                    "A partner inside the mansion will need to get you the key to the side room.")
                    .then { guest ->
                        if (Quest.nearby(guest, npc)) {
                            Quest.advance(guest, Quest.PHOENIX_SPOKE_TO_ALFONSE, Quest.PHOENIX_SPOKE_TO_CHARLIE)
                        }
                    }.open()
            }, "I'll leave you to it.", {})
        }
        dialogue.open()
    }

    fun grubor(player: Player, npc: Npc) {
        val dialogue = player.newDialogue().npc(GRUBOR, "What's the password?")
        if (Quest.stage(player) == Quest.BLACK_ARM_BRIEFED && blackArm(player)) {
            dialogue.options(BLACK_PASSWORD, {
                it.newDialogue().npc(GRUBOR, "You're expected. Find Trobert inside.")
                    .then { guest ->
                        if (Quest.nearby(guest, npc)) Quest.advance(guest, Quest.BLACK_ARM_BRIEFED, Quest.BLACK_ARM_GAVE_PASSWORD)
                    }.open()
            }, "Lucky horseshoe.", { it.newDialogue().npc(GRUBOR, "Try again.").open() })
        }
        dialogue.open()
    }

    fun trobert(player: Player, npc: Npc) {
        val dialogue = player.newDialogue().npc(TROBERT, "What can I do for you?")
        if (Quest.stage(player) in Quest.BLACK_ARM_GAVE_PASSWORD..Quest.BLACK_ARM_PASSED_DISGUISE && blackArm(player)) {
            dialogue.options(ASK_TROBERT, {
                it.newDialogue().npc(TROBERT, "Grip expects a Black Knight called Hartigen. These are his papers.",
                    "Wear a black full helm, platebody and platelegs. Show the papers to Garv.")
                    .then { guest ->
                        if (Quest.nearby(guest, npc) && Quest.stage(guest) in
                            Quest.BLACK_ARM_GAVE_PASSWORD..Quest.BLACK_ARM_PASSED_DISGUISE &&
                            !owns(guest, PAPERS) && guest.inventory.add(PAPERS)) {
                            Quest.advance(guest, Quest.BLACK_ARM_GAVE_PASSWORD, Quest.BLACK_ARM_OBTAINED_PAPERS)
                        }
                    }.open()
            }, "Nothing today.", {})
        }
        dialogue.open()
    }

    fun garv(player: Player, npc: Npc) {
        val dialogue = player.newDialogue().npc(GARV, "State your business.")
        if (Quest.stage(player) == Quest.BLACK_ARM_OBTAINED_PAPERS && blackArm(player)) {
            dialogue.options(SHOW_DISGUISE, {
                val disguised = it.equipment[Equipment.HEAD]?.id == 1165 &&
                    it.equipment[Equipment.CHEST]?.id == 1125 && it.equipment[Equipment.LEGS]?.id == 1077
                if (disguised && it.inventory.contains(PAPERS) && Quest.nearby(it, npc)) {
                    it.newDialogue().npc(GARV, "Your papers are in order. Report to Grip.")
                        .then { deputy ->
                            if (Quest.nearby(deputy, npc) && deputy.inventory.contains(PAPERS)) {
                                Quest.advance(deputy, Quest.BLACK_ARM_OBTAINED_PAPERS, Quest.BLACK_ARM_PASSED_DISGUISE)
                            }
                        }.open()
                } else it.newDialogue().npc(GARV, "I expect Hartigen in full black armour with his papers.").open()
            }, "No business here.", {})
        }
        dialogue.open()
    }

    fun grip(player: Player, npc: Npc) {
        val dialogue = player.newDialogue()
        when (Quest.stage(player)) {
            Quest.BLACK_ARM_PASSED_DISGUISE -> {
                dialogue.npc(GRIP, "You're late, deputy. Have you got your papers?")
                if (player.inventory.contains(PAPERS)) dialogue.then {
                    if (Quest.nearby(it, npc) && Quest.stage(it) == Quest.BLACK_ARM_PASSED_DISGUISE &&
                        it.inventory.remove(PAPERS)) Quest.advance(it, Quest.BLACK_ARM_PASSED_DISGUISE, Quest.BLACK_ARM_GAVE_PAPERS)
                }
            }
            Quest.BLACK_ARM_GAVE_PAPERS, Quest.BLACK_ARM_LOOTED_CHEST ->
                dialogue.npc(GRIP, "What is it, deputy?").options(ASK_DUTIES, {
                    it.newDialogue().npc(GRIP, "We guard Pete's treasure. This cabinet is my business.")
                        .options(ASK_KEY, { deputy ->
                            if (Quest.nearby(deputy, npc) && Quest.stage(deputy) in
                                Quest.BLACK_ARM_GAVE_PAPERS..Quest.BLACK_ARM_LOOTED_CHEST &&
                                !owns(deputy, SIDE_KEY) && deputy.inventory.add(SIDE_KEY)) {
                                deputy.newDialogue().npc(GRIP, "See if you can find what this key opens.").open()
                            }
                        }, "I'll take my post.", {}).open()
                }, "Nothing, sir.", {})
            else -> dialogue.npc(GRIP, "I'm on duty.")
        }
        dialogue.open()
    }

    fun useSideKey(player: Player, door: GameObject) {
        if (phoenix(player) && Quest.stage(player) in Quest.PHOENIX_SPOKE_TO_CHARLIE..Quest.PHOENIX_KILLED_GRIP) {
            player.heroSideKeyUsed = true
            player.sendMessage("The key fits the side door.")
        }
        QuestDoors.cross(player, door)
    }

    fun useTreasureKey(player: Player, door: GameObject) {
        if (blackArm(player) && Quest.stage(player) in Quest.BLACK_ARM_GAVE_PAPERS..Quest.BLACK_ARM_LOOTED_CHEST) {
            player.heroTreasureKeyUsed = true
            player.sendMessage("Grip's key opens the treasure room.")
        }
        QuestDoors.cross(player, door)
    }

    fun inspectCabinet(player: Player, cabinet: GameObject) {
        if (cabinet.id == CABINET) Entrances.replace(cabinet, CABINET_OPEN, 30)
        if (Quest.stage(player) !in Quest.BLACK_ARM_GAVE_PAPERS..Quest.BLACK_ARM_LOOTED_CHEST || !blackArm(player)) return
        player.newDialogue().text("The cabinet belongs to Grip. Opening it could get his attention.")
            .options(LURE, { lureGrip(it) }, "Leave it alone.", {}).open()
    }

    private fun lureGrip(player: Player) {
        if (Quest.stage(player) !in Quest.BLACK_ARM_GAVE_PAPERS..Quest.BLACK_ARM_LOOTED_CHEST ||
            !player.isAlive || player.z != cabinet.z || !player.position.isWithinDistance(cabinet, 3)) return
        val world = player.world
        val grip = world.locator.findNpcs(gripHome, 12) {
            it.id == GRIP && it.isAlive && it.state == EntityState.ACTIVE
        }.firstOrNull() ?: return
        if (grip in ambushes || grip.combat.inCombat() || grip.combat.isDisabled || grip.walking.isLocked) return
        val partner = world.locator.findPlayers(firingTile, 2) {
            it !== player && it.isAlive && isInFiringRoom(it) && it.heroSideKeyUsed && phoenix(it) &&
                Quest.stage(it) in Quest.PHOENIX_SPOKE_TO_CHARLIE..Quest.PHOENIX_KILLED_GRIP
        }.minByOrNull { it.position.computeLongestDistance(firingTile) } ?: run {
            player.sendMessage("Your partner needs to be in the side room before you lure Grip.")
            return
        }
        val attempt = Ambush(player, partner)
        ambushes[grip] = attempt
        grip.combat.isDisabled = true
        leadGripToCabinet(grip, attempt)
        player.sendMessage("Grip comes to check the cabinet. The ambush is ready when he reaches it.")
        world.scheduleOnce(AMBUSH_TICKS) {
            if (ambushes.remove(grip, attempt)) {
                releaseGrip(grip, attempt)
                if (grip.isAlive && !grip.combat.inCombat()) grip.navigator.navigate(gripHome, false)
            }
        }
    }

    private fun leadGripToCabinet(grip: Npc, attempt: Ambush) {
        fun enterCabinetRoom(attemptsRemaining: Int = 6) {
            if (ambushes[grip] !== attempt || !grip.isAlive) return
            grip.navigator.navigate(lure, false).thenAccept { result ->
                if (ambushes[grip] !== attempt) return@thenAccept
                if (result == NavigationResult.REACHED) {
                    grip.walking.isLocked = true
                    attempt.holdingGrip = true
                } else if (attemptsRemaining > 1) {
                    grip.world.scheduleOnce(3) { enterCabinetRoom(attemptsRemaining - 1) }
                } else if (ambushes.remove(grip, attempt)) releaseGrip(grip, attempt)
            }
        }
        if (grip.y >= cabinetRoomDoor.y) {
            enterCabinetRoom()
            return
        }
        // Grip opens the existing door once he reaches it, so it stays open for his walk into the room.
        grip.navigator.navigate(cabinetRoomDoor.translate(0, -1), false).thenAccept { result ->
            if (ambushes[grip] !== attempt) return@thenAccept
            if (result != NavigationResult.REACHED) {
                ambushes.remove(grip, attempt)
                releaseGrip(grip, attempt)
                return@thenAccept
            }
            val door = grip.world.objects.findAll(cabinetRoomDoor)
                .filter { it.id == 1530 && it.state == EntityState.ACTIVE }.findFirst().orElse(null)
            door?.let { Doors.toggle(grip.world, attempt.inside, it) }
            enterCabinetRoom()
        }
    }

    private fun releaseGrip(grip: Npc, attempt: Ambush) {
        grip.combat.isDisabled = false
        if (attempt.holdingGrip) grip.walking.isLocked = false
    }

    fun gripDefeated(grip: Npc, attacker: Player?) {
        val ambush = ambushes.remove(grip) ?: return
        releaseGrip(grip, ambush)
        val inside = ambush.inside
        if (attacker == null || attacker !== ambush.shooter || grip.position != lure || attacker.isEventBot ||
            !isInFiringRoom(attacker) ||
            !inside.isAlive || inside.state != EntityState.ACTIVE ||
            !inside.position.isWithinDistance(cabinet, 6) || inside.z != cabinet.z ||
            Quest.stage(attacker) !in Quest.PHOENIX_SPOKE_TO_CHARLIE..Quest.PHOENIX_KILLED_GRIP ||
            Quest.stage(inside) !in Quest.BLACK_ARM_GAVE_PAPERS..Quest.BLACK_ARM_LOOTED_CHEST) return
        Quest.advance(attacker, Quest.PHOENIX_SPOKE_TO_CHARLIE, Quest.PHOENIX_KILLED_GRIP)
        if (!owns(inside, TREASURE_KEY)) {
            inside.world.addItem(GroundItem(inside.context, TREASURE_KEY, 1, grip.position, ChunkUpdatableView.localView(inside)))
        }
    }

    fun searchChest(player: Player, openChest: GameObject) {
        if (!blackArm(player) || !player.heroTreasureKeyUsed ||
            Quest.stage(player) !in Quest.BLACK_ARM_GAVE_PAPERS..Quest.BLACK_ARM_LOOTED_CHEST ||
            owns(player, CANDLESTICK)) {
            player.sendMessage("You find nothing else in the chest.")
            return
        }
        val candlesticks = Item(CANDLESTICK, 2)
        if (!player.inventory.hasSpaceFor(candlesticks) || !player.inventory.add(candlesticks)) {
            player.sendMessage("You need room for both candlesticks.")
            return
        }
        player.sendMessage("You find two candlesticks, one for you and one for your partner.")
        Quest.advance(player, Quest.BLACK_ARM_GAVE_PAPERS, Quest.BLACK_ARM_LOOTED_CHEST)
        Entrances.replace(openChest, CHEST)
    }
}
