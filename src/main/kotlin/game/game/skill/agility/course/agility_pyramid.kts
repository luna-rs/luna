package game.skill.agility.course

import api.predef.*
import api.predef.ext.*
import game.player.Messages
import game.skill.agility.*
import game.skill.agility.Agility.course
import game.skill.agility.AgilityCourse.AGILITY_PYRAMID
import game.skill.agility.AgilityObstacle.*
import game.skill.agility.pyramid.AgilityPyramid
import game.skill.agility.pyramid.AgilityPyramid.BACKWARDS_DAMAGE
import game.skill.agility.pyramid.AgilityPyramid.BALANCE_WALK
import game.skill.agility.pyramid.AgilityPyramid.BLOCKING_TICKS
import game.skill.agility.pyramid.AgilityPyramid.BLOCK_PUSH_INNER
import game.skill.agility.pyramid.AgilityPyramid.BLOCK_PUSH_OUTER
import game.skill.agility.pyramid.AgilityPyramid.CLIMB_WALK
import game.skill.agility.pyramid.AgilityPyramid.COINS
import game.skill.agility.pyramid.AgilityPyramid.CROSS_GAPS
import game.skill.agility.pyramid.AgilityPyramid.CYCLE
import game.skill.agility.pyramid.AgilityPyramid.Crossing
import game.skill.agility.pyramid.AgilityPyramid.DOORWAYS
import game.skill.agility.pyramid.AgilityPyramid.EXIT
import game.skill.agility.pyramid.AgilityPyramid.GAP_HANG_FALL
import game.skill.agility.pyramid.AgilityPyramid.GAP_HANG_FALL_BACK
import game.skill.agility.pyramid.AgilityPyramid.GAP_HANG_WALK
import game.skill.agility.pyramid.AgilityPyramid.GAP_JUMP_ANIMATION
import game.skill.agility.pyramid.AgilityPyramid.GAP_JUMP_FALL
import game.skill.agility.pyramid.AgilityPyramid.Hazard
import game.skill.agility.pyramid.AgilityPyramid.JUMP_GAP
import game.skill.agility.pyramid.AgilityPyramid.LEDGES
import game.skill.agility.pyramid.AgilityPyramid.LEDGE_FALL
import game.skill.agility.pyramid.AgilityPyramid.LEDGE_FALL_BACK
import game.skill.agility.pyramid.AgilityPyramid.LEDGE_OFF
import game.skill.agility.pyramid.AgilityPyramid.LEDGE_ON
import game.skill.agility.pyramid.AgilityPyramid.LEDGE_WALK
import game.skill.agility.pyramid.AgilityPyramid.LOW_WALL
import game.skill.agility.pyramid.AgilityPyramid.LOW_WALL_CLIMB
import game.skill.agility.pyramid.AgilityPyramid.LOW_WALL_SLIP
import game.skill.agility.pyramid.AgilityPyramid.PLANKS
import game.skill.agility.pyramid.AgilityPyramid.PLANK_FALL
import game.skill.agility.pyramid.AgilityPyramid.PUSH_DAMAGE
import game.skill.agility.pyramid.AgilityPyramid.PYRAMID_TOP
import game.skill.agility.pyramid.AgilityPyramid.ROCK_SLIDE
import game.skill.agility.pyramid.AgilityPyramid.ROLLING_BLOCKS
import game.skill.agility.pyramid.AgilityPyramid.SIMON
import game.skill.agility.pyramid.AgilityPyramid.SLIDING_BLOCKS
import game.skill.agility.pyramid.AgilityPyramid.SlidingBlock
import game.skill.agility.pyramid.AgilityPyramid.TILT_FALL
import game.skill.agility.pyramid.AgilityPyramid.TOP_CLIMB
import game.skill.agility.pyramid.AgilityPyramid.TOP_PRICE
import game.skill.agility.pyramid.AgilityPyramid.TOP_ROCKS
import game.skill.agility.pyramid.AgilityPyramid.fall
import game.skill.agility.pyramid.AgilityPyramid.pyramidLastTile
import game.skill.agility.pyramid.AgilityPyramid.pyramidTopTaken
import game.skill.agility.pyramid.AgilityPyramid.simonStage
import game.skill.agility.pyramid.AgilityPyramid.slips
import game.skill.agility.pyramid.AgilityPyramid.tilt
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.PlayerTickEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueUtils

/**
 * The crossing of [obstacle] this player stands at the start of, either way.
 */
fun Player.crossing(obstacle: AgilityObstacle): Crossing = AgilityPyramid.crossingFrom(obstacle, position)!!

/**
 * Crosses [crossing] the right way.
 */
fun Player.across(crossing: Crossing): AgilityAction {
    val to = crossing.endFor(position)
    return when (crossing.obstacle) {
        // The player leans on the wall for a tick before climbing over it.
        PYRAMID_LOW_WALL -> AgilityAction(this, listOf(AgilityStep(to, 3, animation = LOW_WALL_CLIMB,
            animationTick = 1, moveStart = 57, moveEnd = 87)), "You climb the low wall...",
            endMessage = "... and make it over.")
        PYRAMID_LEDGE -> AgilityAction(this, listOf(
            AgilityStep(position, 1, animation = LEDGE_ON, jump = true),
            AgilityStep(to, 5, walkingAnimation = LEDGE_WALK),
            AgilityStep(to, 1, animation = LEDGE_OFF, jump = true, message = "You skillfully edge across the gap.")),
            "You put your foot on the ledge and try to edge across...")
        PYRAMID_PLANK -> AgilityAction(this, listOf(AgilityStep(to, 5, walkingAnimation = BALANCE_WALK)),
            "You walk carefully across the slippery plank...")
        PYRAMID_CROSS_GAP -> AgilityAction(this, listOf(AgilityStep(to, 5, walkingAnimation = GAP_HANG_WALK)), null)
        // The crouch starts a tick after the message, and the player leaps across during it.
        else -> AgilityAction(this, listOf(AgilityStep(to, 4, animation = GAP_JUMP_ANIMATION, animationTick = 1,
            moveStart = 55, moveEnd = 80)), "You jump the gap...")
    }
}

/**
 * Fails [crossing]: going backwards always does, and drops the player a level for a point of damage. Otherwise they
 * slip part way across and fall a level, or, on a low wall, just slip.
 */
fun Player.fail(crossing: Crossing): AgilityAction {
    val from = position
    if (crossing.behind(from)) {
        val pose = when (crossing.obstacle) {
            PYRAMID_LOW_WALL -> LOW_WALL_SLIP
            PYRAMID_LEDGE -> LEDGE_FALL_BACK
            PYRAMID_PLANK -> PLANK_FALL
            PYRAMID_CROSS_GAP -> GAP_HANG_FALL_BACK
            else -> GAP_JUMP_FALL
        }
        return fall(emptyList(), pose, 2, crossing.landing, BACKWARDS_DAMAGE)
    }
    val hit = crossing.hazard.damage
    return when (crossing.obstacle) {
        PYRAMID_LOW_WALL -> AgilityAction(this, listOf(AgilityStep(from, 3, animation = LOW_WALL_SLIP,
            animationTick = 1, jump = true)), "You climb the low wall...").also { it.onSuccess = { damage(hit) } }
        PYRAMID_LEDGE -> fall(listOf(AgilityStep(from, 1, animation = LEDGE_ON, jump = true),
            AgilityStep(crossing.along(from, 2), 2, walkingAnimation = LEDGE_WALK)), LEDGE_FALL, 2, crossing.landing,
            hit, "You put your foot on the ledge and try to edge across...")
        PYRAMID_PLANK -> fall(listOf(AgilityStep(crossing.along(from, 2), 2, walkingAnimation = BALANCE_WALK)),
            PLANK_FALL, 1, crossing.landing, hit, "You walk carefully across the slippery plank...")
        PYRAMID_CROSS_GAP -> fall(listOf(AgilityStep(crossing.along(from, 2), 2, walkingAnimation = GAP_HANG_WALK)),
            GAP_HANG_FALL, 2, crossing.landing, hit)
        // Both lines come together, then the player teeters a long while: the drop comes 8 ticks after them.
        else -> fall(listOf(AgilityStep(from, 1, jump = true, message = "... and miss your footing.")),
            GAP_JUMP_FALL, 7, crossing.landing, hit, "You jump the gap...")
    }
}

/**
 * Climbs the rocks on the top block and takes the gold top, if it's still there for this player.
 */
fun Player.climbToTop() = AgilityAction(this, listOf(
    AgilityStep(position, 3, animation = TOP_CLIMB, jump = true),
    AgilityStep(position, 3, jump = true, begin = {
        if (pyramidTopTaken) {
            sendMessage("You find nothing at the top of the pyramid.")
        } else if (inventory.add(Item(PYRAMID_TOP))) {
            pyramidTopTaken = true
            AgilityPyramid.sync(this)
            newDialogue().give(Item(PYRAMID_TOP), "You find a golden pyramid!", false).open()
        } else {
            sendMessage(Messages.inventoryFull())
        }
    })), null)

course(AGILITY_PYRAMID) {
    val crossings = listOf(LOW_WALL to PYRAMID_LOW_WALL, JUMP_GAP to PYRAMID_JUMP_GAP) +
        LEDGES.map { it to PYRAMID_LEDGE } + PLANKS.map { it to PYRAMID_PLANK } +
        CROSS_GAPS.map { it to PYRAMID_CROSS_GAP }
    for ((id, kind) in crossings) {
        obstacle(id, kind,
            checkpoint = { false },
            allow = { obj -> AgilityPyramid.crossingAt(obj.position)?.obstacle == kind },
            startTile = { obj -> AgilityPyramid.crossingAt(obj.position)!!.startFor(position) },
            failsIf = { crossing(kind).let { it.behind(position) || slips(it.hazard, it.level) } },
            fail = { fail(crossing(kind)) }) {
            across(crossing(kind))
        }
    }

    obstacle(TOP_ROCKS, PYRAMID_TOP_ROCKS,
        checkpoint = { false },
        allow = {
            if (pyramidTopTaken || inventory.hasSpaceFor(Item(PYRAMID_TOP))) true else {
                sendMessage(Messages.inventoryFull())
                false
            }
        },
        startTile = { Position(3043, y.coerceIn(4697, 4698), 3) }) {
        climbToTop()
    }
}

// The doorway in the top block leads down through the pyramid to its base. Every way to the top passes every
// obstacle, so leaving this way is the completed climb's 300, and the top is back for the next climb.
for (id in DOORWAYS) {
    object1(id) {
        if (plr.isLocked || AgilityPyramid.levelAt(plr.position) != 5) return@object1
        plr.move(EXIT)
        plr.pyramidTopTaken = false
        AgilityPyramid.sync(plr)
        plr.agility.addExperience(300.0)
        plr.newDialogue().text("You climb down the steep passage. It leads to the base of the", "pyramid.").open()
    }
}

/**
 * Climbs the rock slide between the desert and the pyramid's yard: down needs level 30, up nothing.
 */
fun slide(plr: Player, down: Boolean) {
    if (plr.isLocked || down && !Agility.checkLevel(plr, 30)) return
    val to = Position(if (down) 3349 else 3343, plr.y)
    plr.submitAction(AgilityAction(plr, listOf(AgilityStep(to, 6, walkingAnimation = CLIMB_WALK)), null).also {
        it.onSuccess = { plr.agility.addExperience(if (down) 5.0 else 1.0) }
    })
}

object1(ROCK_SLIDE) {
    val down = plr.x < 3346
    val start = Position(if (down) 3343 else 3349, plr.y.coerceIn(2843, 2846))
    if (plr.position == start) {
        slide(plr, down)
    } else if (!plr.isLocked) {
        plr.submitAction(AgilityApproach(plr, start) { slide(plr, down) })
    }
}

/**
 * Throws a player who steps onto a rolling block on to the tile past it, or knocks them a level down; going backwards
 * always does.
 */
fun rollingBlocks(plr: Player) {
    val here = plr.position
    val last = plr.pyramidLastTile
    plr.pyramidLastTile = here
    if (last == null || plr.isLocked || last.z != here.z || !last.isWithinDistance(here, 2)) return
    val block = ROLLING_BLOCKS.firstOrNull { here in it.tiles } ?: return
    if (last in block.tiles) return
    val forwards = block.forwards(last)
    plr.tilt(block)
    if (!forwards || plr.slips(Hazard.ROLLING_BLOCK, block.level)) {
        plr.submitAction(plr.fall(emptyList(), TILT_FALL, 2, block.landing,
            if (forwards) Hazard.ROLLING_BLOCK.damage else BACKWARDS_DAMAGE))
    } else {
        plr.submitAction(AgilityAction(plr, listOf(AgilityStep(block.past(here), 3, animation = TILT_FALL,
            moveStart = 20, moveEnd = 45)), null).also { it.onSuccess = { plr.agility.addExperience(12.0) } })
    }
}

/**
 * Moves a sliding block on through its cycle, and pushes off anyone in its lane while it fills it.
 */
fun slideBlock(block: SlidingBlock) {
    val npc = block.npc?.takeIf { it.state == EntityState.ACTIVE }
        ?: world.npcs.firstOrNull { it.id == block.npcId }?.also { block.npc = it } ?: return
    // The blocks only ever slide in and out; their spawns would otherwise wander.
    if (npc.isWandering) npc.isWandering = false
    when (Math.floorMod(world.currentTick + block.phase, CYCLE.toLong()).toInt()) {
        0 -> {
            if (npc.position != block.home) npc.move(block.home)
            npc.walking.addForcedStep(block.out)
        }
        1 -> npc.walking.addForcedStep(block.out)
        3, 4 -> npc.walking.addForcedStep(block.out.opposite())
    }
    if (Math.floorMod(world.currentTick + block.phase, CYCLE.toLong()) >= BLOCKING_TICKS) return
    world.locator.findPlayers(block.home, 4) { it.position in block.lane && !it.isLocked }.forEach {
        // The inner tile is further from the edge, so the block has further to shove the player.
        val inner = block.lane.minOf { tile -> tile.x * block.out.translateX + tile.y * block.out.translateY } ==
            it.x * block.out.translateX + it.y * block.out.translateY
        // World tasks run before this tick's actions, so the push's first tick counts at once: the drop comes 3
        // ticks after the knock-down starts.
        it.submitAction(it.fall(emptyList(), if (inner) BLOCK_PUSH_INNER else BLOCK_PUSH_OUTER, 4, block.landing,
            PUSH_DAMAGE))
    }
}

on(ServerLaunchEvent::class) {
    world.schedule(1) { SLIDING_BLOCKS.forEach { slideBlock(it) } }
}

on(PlayerTickEvent::class) {
    if (AgilityPyramid.watched(plr)) {
        AgilityPyramid.sync(plr)
        rollingBlocks(plr)
    }
}

on(LoginEvent::class) {
    AgilityPyramid.sync(plr)
}

/**
 * Sells Simon every top this player carries, and has them talk it over.
 */
fun sellTops(plr: Player) {
    val tops = plr.inventory.computeAmountForId(PYRAMID_TOP)
    if (tops == 0 || !plr.inventory.remove(Item(PYRAMID_TOP, tops))) return
    plr.inventory.add(Item(COINS, tops * TOP_PRICE))
    plr.simonStage = 2
    AgilityPyramid.sync(plr)
    val dialogue = plr.newDialogue()
    if (tops == 1) dialogue.player("I have one here I'll sell you.")
    dialogue.give(Item(PYRAMID_TOP), "You hand over the artefact(s) and Simon hands you ${tops * TOP_PRICE} coins.",
        false)
        .npc(SIMON, "Ripper! Thanks a bundle, mate! Thanks to you I can",
            "fulfill me contract. You're a true blue! The boss will be", "pleased.")
        .player("Glad I was able to help... but who is your boss? I", "thought you worked for the museum?")
        .npc(SIMON, "Mind your own bizzo, mate. But if you get any more,", "you know where I am!")
        .open()
}

/**
 * Offers Simon this player's tops, if they have any: [none] runs when they don't.
 */
fun offerTops(plr: Player, none: () -> Unit) {
    val tops = plr.inventory.computeAmountForId(PYRAMID_TOP)
    when {
        tops == 0 -> none()
        tops == 1 -> plr.newDialogue().options(
            "Sell it.", { sellTops(it) },
            "Keep it.", {
                it.newDialogue()
                    .player("I have one, but I want to keep it for now.")
                    .npc(SIMON, "Careful, mate, people might come looking for a thing like", "that!")
                    .player("Thanks for the advice, but I'll hang onto it for now.")
                    .npc(SIMON, "Bye, cobber.")
                    .open()
            }).open()
        else -> plr.newDialogue().options(
            "Sell them.", { sellTops(it) },
            "Keep them.", {
                it.newDialogue()
                    .player("I have some, but I want to keep them for now.")
                    .npc(SIMON, "Careful, mate, people might come looking for a thing like", "that!")
                    .player("Thanks for the advice, but I'll hang onto it for now.")
                    .npc(SIMON, "Bye, cobber.")
                    .open()
            }).open()
    }
}

npc1(SIMON) {
    when (plr.simonStage) {
        0 -> plr.newDialogue()
            .player("Hello.")
            .npc(SIMON, "G'day, mate. I'm Simon - Simon Templeton. Who are", "you?")
            .player(*DialogueUtils.splitChatLines("I'm ${plr.username}. What are you doing all the way out here?"))
            .npc(SIMON, "I've been contracted to retrieve artefacts from the top", "of this magnificent pyramid.")
            .player("So, why are you down here and not up there then?")
            .npc(SIMON, "Well, it's me back - an old injury I picked up in",
                "Sophanem has come back to haunt me. I was working", "for the Museum of Varrock, and the gold I had been",
                "given wasn't enough to pay for the carpet rides, let")
            .npc(SIMON, "alone any decent equipment. But that was a long time",
                "ago... I can't even lift a chisel right now. There's no", "way I can climb all the way up there.")
            .player("How on earth did you get down the cliff face, then?")
            .npc(SIMON, "That's what set off the injury. I was fine before I",
                "scrabbled down that pesky slope. I think it will be a while",
                "before I can climb back up again; I must be getting", "old.")
            .npc(SIMON, "Hang on a second... You look like you're pretty agile",
                "and up for a challenge. How about you retrieve it for", "me?")
            .npc(SIMON, "I'll tell you what, I'll give you 1000 coins for every", "artefact you get for me.")
            .player("I'll see what I can do.")
            .npc(SIMON, "Good on ya, mate!")
            .then {
                it.simonStage = 1
                AgilityPyramid.sync(it)
            }
            .open()
        1 -> plr.newDialogue()
            .player("Hello, Simon.")
            .npc(SIMON, *DialogueUtils.splitChatLines("G'day, ${plr.username}."))
            .player("How's it going?")
            .npc(SIMON, "Not bad, mate, not bad at all. Got any artefacts for", "me?")
            .then {
                offerTops(it) {
                    it.newDialogue()
                        .player("No, I haven't.")
                        .npc(SIMON, "Try harder, mate! It won't be long till some other",
                            "critter turns up and snatches them out from under", "your eyes.")
                        .player("I will, goodbye.")
                        .npc(SIMON, "Bye, cobber.")
                        .open()
                }
            }
            .open()
        else -> plr.newDialogue()
            .npc(SIMON, "G'day, mate. Got any new pyramid artefacts for me?")
            .then {
                offerTops(it) {
                    it.newDialogue()
                        .player("No, I haven't.")
                        .npc(SIMON, "Well, keep my offer in mind if you do find one.")
                        .player("I will. Goodbye, Simon.")
                        .npc(SIMON, "Bye, mate.")
                        .open()
                }
            }
            .open()
    }
}

useItem(PYRAMID_TOP).onNpc(SIMON) {
    sellTops(plr)
}
