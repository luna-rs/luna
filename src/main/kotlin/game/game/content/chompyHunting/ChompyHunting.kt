package game.content.chompyHunting

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.player.Sound
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic

/**
 * Chompy bird hunting in the Feldip Hills: swamp gas in ogre bellows inflates swamp toads, a bloated toad left on the
 * ground draws a chompy bird down, and only an ogre bow and ogre arrows can bring the bird down.
 *
 * @author TheLining
 */
object ChompyHunting {

    const val SWAMP_TOAD = 1013
    const val BLOATED_TOAD_NPC = 1014
    const val CHOMPY_BIRD = 1015
    const val DEAD_CHOMPY = 1016

    const val EMPTY_BELLOWS = 2871
    const val BLOATED_TOAD = 2875
    const val RAW_CHOMPY = 2876
    const val COOKED_CHOMPY = 2878
    const val OGRE_ARROW = 2866
    const val OGRE_BOW = 2883
    const val CHOMPY_PICTURE = 2884
    const val BONES = 526
    const val FEATHER = 314

    /**
     * Ogre bellows holding three, two and one loads of swamp gas.
     */
    val FILLED_BELLOWS = listOf(2872, 2873, 2874)

    /**
     * Every kind of ogre bellows.
     */
    val ALL_BELLOWS = listOf(EMPTY_BELLOWS) + FILLED_BELLOWS

    /**
     * Swamp bubbles out in the open and in caves.
     */
    val SWAMP_BUBBLES = listOf(684, 735)

    const val BELLOWS_ANIMATION = 1026
    const val PICKUP_ANIMATION = 827
    const val TOAD_INFLATE_ANIMATION = 1019
    const val BELLOWS_GRAPHIC = 241
    const val TOAD_EXPLODE_GRAPHIC = 240

    /**
     * How long an inflated swamp toad stays away before it is back.
     */
    const val TOAD_RESPAWN_TICKS = 100

    /**
     * How many bloated toads a player can carry before one gets away.
     */
    const val MAX_CARRIED_TOADS = 3

    /**
     * The chompy kills the 30,000 Ranged experience reward is given at.
     */
    const val EXPERT_KILLS = 4000

    /**
     * The chompy birds this player has killed with an ogre bow.
     */
    var Player.chompyKills by Attr.int().persist("chompy_kills")

    /**
     * The ogre lands where chompy birds can be lured.
     */
    private fun inHuntingGrounds(pos: Position) = pos.z == 0 && pos.x in 2496..2687 && pos.y in 2944..3007

    /**
     * The patch of ground beside Rantz where he does his own hunting.
     */
    private fun onRantzTurf(pos: Position) = pos.z == 0 && pos.x in 2631..2639 && pos.y in 2962..2971

    /**
     * Fills the ogre bellows at [index] with swamp gas from swamp bubbles.
     */
    fun fillBellows(plr: Player, index: Int) {
        val bellows = plr.inventory[index]?.id ?: return
        if (bellows == FILLED_BELLOWS[0]) {
            plr.sendMessage("These bellows are already full of swamp gas!")
            return
        }
        if (bellows !in ALL_BELLOWS) {
            plr.sendMessage("Nothing interesting happens.")
            return
        }
        plr.animation(Animation(BELLOWS_ANIMATION))
        plr.playSound(Sound.OGRE_BELLOWS_SUCK)
        plr.lock(2) {
            if (plr.inventory[index]?.id == bellows) {
                plr.sendMessage("You collect some gas from the swamp.")
                plr.inventory[index] = Item(FILLED_BELLOWS[0])
            }
        }
    }

    /**
     * Uses the bellows at [index] on a swamp [toad].
     */
    fun inflateToad(plr: Player, toad: Npc, index: Int) {
        val bellows = plr.inventory[index]?.id ?: return
        if (bellows == EMPTY_BELLOWS) {
            callToad(plr, toad)
            plr.lock(2) {
                if (toad.state == EntityState.ACTIVE) toad.speak("Hissss....")
                plr.lock(2) {
                    plr.sendMessage("The air seems too thin to stay in the toad.")
                    plr.sendMessage("Perhaps you need something thicker than air.")
                }
            }
            return
        }
        if (bellows !in FILLED_BELLOWS) {
            plr.sendMessage("Nothing interesting happens.")
            return
        }
        if (plr.inventory.computeRemainingSize() < 1) {
            plr.sendMessage("You don't have space to carry that.")
            return
        }
        if (plr.inventory.computeAmountForId(BLOATED_TOAD) >= MAX_CARRIED_TOADS) {
            plr.sendMessage("One of your bloated toads manages to escape.")
            val escaped = spawnToad(plr, freeTileNear(plr, plr.position) ?: plr.position, 100) ?: return
            escaped.speak("Ribbitt!")
            plr.inventory.remove(Item(BLOATED_TOAD))
            plr.lock(2) {
                plr.sendMessage("You manage to catch the toad and inflate it with the swamp gas.")
                callToad(plr, escaped)
                plr.lock(4) {
                    plr.sendMessage("You add the bloated toad to your inventory.")
                    plr.inventory.add(Item(BLOATED_TOAD))
                    useGas(plr, bellows)
                    plr.world.npcs.remove(escaped)
                }
            }
            return
        }
        plr.sendMessage("You manage to catch the toad and inflate it with the swamp gas.")
        callToad(plr, toad)
        plr.lock(4) {
            if (toad.state != EntityState.ACTIVE || !useGas(plr, bellows)) return@lock
            plr.sendMessage("You add the bloated toad to your inventory.")
            plr.inventory.add(Item(BLOATED_TOAD))
            catchToad(toad)
        }
    }

    /**
     * The player calls the toad over and pumps it full of gas.
     */
    private fun callToad(plr: Player, toad: Npc) {
        plr.speak("Come here toady!")
        toad.interact(plr)
        plr.animation(Animation(BELLOWS_ANIMATION))
        plr.playSound(Sound.OGRE_BELLOWS)
        plr.graphic(Graphic(BELLOWS_GRAPHIC, 64))
        toad.animation(Animation(TOAD_INFLATE_ANIMATION))
    }

    /**
     * Takes one load of gas out of [bellows]. Returns `false` if the player no longer has them.
     */
    private fun useGas(plr: Player, bellows: Int): Boolean {
        val next = FILLED_BELLOWS.indexOf(bellows) + 1
        return plr.inventory.replace(bellows, if (next < FILLED_BELLOWS.size) FILLED_BELLOWS[next] else EMPTY_BELLOWS)
    }

    /**
     * Takes a caught swamp toad out of the swamp until it is back. Toads spawned by this plugin are just removed.
     */
    private fun catchToad(toad: Npc) {
        if (toad.respawnTicks < 0 || toad.index < 0) {
            toad.world.npcs.remove(toad)
            return
        }
        toad.despawn()
        toad.world.scheduleOnce(TOAD_RESPAWN_TICKS) { toad.respawn() }
    }

    /**
     * Spawns a swamp toad that is removed after [lifeTicks].
     */
    private fun spawnToad(plr: Player, pos: Position, lifeTicks: Int): Npc? {
        val toad = Npc(plr.context, SWAMP_TOAD, pos)
        toad.respawnTicks = -1
        if (!plr.world.npcs.add(toad)) return null
        plr.world.scheduleOnce(lifeTicks) {
            if (toad.state == EntityState.ACTIVE) plr.world.npcs.remove(toad)
        }
        return toad
    }

    /**
     * A random direction a toad or the player can step in from [pos].
     */
    private fun freeDirection(plr: Player, pos: Position): Direction? =
        Direction.ALL_EXCEPT_NONE.shuffled().firstOrNull {
            plr.world.collisionManager.traversable(pos, EntityType.NPC, it)
        }

    /**
     * A random tile next to [pos] that a toad or the player can step onto.
     */
    private fun freeTileNear(plr: Player, pos: Position): Position? =
        freeDirection(plr, pos)?.let { pos.translate(it.translateX, it.translateY) }

    /**
     * Lets the bloated toad at [index] go. Returns `false` if the item there isn't a bloated toad.
     */
    fun releaseToad(plr: Player, index: Int): Boolean {
        if (plr.inventory[index]?.id != BLOATED_TOAD) return false
        plr.inventory[index] = null
        letGo(plr)
        if (rand(4) == 0) plr.speak("Free the fatsy toady!")
        plr.sendMessage("You release the toad and see it hop off into the distance.")
        return true
    }

    /**
     * Lets every bloated toad the player carries go.
     */
    fun releaseAll(plr: Player) {
        val count = plr.inventory.computeAmountForId(BLOATED_TOAD)
        if (count < 1) return
        plr.inventory.remove(Item(BLOATED_TOAD, count))
        repeat(count) { letGo(plr) }
        if (count == 1) {
            if (rand(4) == 0) plr.speak("Free the fatsy toady!")
            plr.sendMessage("You release the toad and see it hop off into the distance.")
        } else {
            if (rand(4) == 0) plr.speak("Free the fatsy toadies!")
            plr.sendMessage("You release the toads and see them hop off into the distance.")
        }
    }

    /**
     * A released toad shrinks back to a swamp toad and hops away.
     */
    private fun letGo(plr: Player) {
        val start = freeTileNear(plr, plr.position) ?: plr.position
        val toad = spawnToad(plr, start, 10) ?: return
        val dx = (start.x - plr.position.x) * 10
        val dy = (start.y - plr.position.y) * 10
        toad.navigator.navigate(start.translate(dx, dy), false)
        when (rand(2)) {
            0 -> toad.speak("Croak!")
            1 -> toad.speak("Ribbet!")
        }
    }

    /**
     * Places the bloated toad at [index] on the ground as chompy bird bait.
     */
    fun placeToad(plr: Player, index: Int) {
        // TODO Big Chompy Bird Hunting: before Rantz has shown the player where to put the toad, it can't be placed
        //  ("You're not sure where Rantz told you to place the bloated toad..."), and during the quest it only goes on
        //  Rantz's turf. Until the quest exists every player has finished it.
        val pos = plr.position
        if (onRantzTurf(pos)) {
            plr.sendMessage("Rantz doesn't like it when you chompy hunt on his turf.")
            plr.sendMessage("But you can hunt for chompys in the rest of the ogre area.")
            return
        }
        if (!inHuntingGrounds(pos)) {
            plr.sendMessage("You won't attract a chompy bird this far away from the ogre area.")
            return
        }
        if (plr.world.npcs.any { it.id == BLOATED_TOAD_NPC && it.position == pos }) {
            plr.sendMessage("There is a bloated toad already placed at this location.")
            return
        }
        if (plr.inventory[index]?.id != BLOATED_TOAD) return
        val bait = BloatedToad(plr, pos)
        if (!plr.world.npcs.add(bait)) return
        plr.inventory[index] = null
        plr.animation(Animation(PICKUP_ANIMATION))
        plr.sendMessage("You carefully place the bloated toad bait.")
        val step = freeDirection(plr, pos)
        if (step != null) plr.world.scheduleOnce(1) { if (plr.position == pos) plr.walking.addStep(step) }
    }

    /**
     * Whether [plr] can shoot at [chompy], telling them why not.
     */
    fun canShoot(plr: Player, chompy: ChompyBird, ranged: Boolean): Boolean {
        val weapon = plr.equipment.weapon?.id
        val message = when {
            weapon == null -> "You'll need a weapon to try and attack this beast."
            !ranged -> "The Chompy Bird is too quick for your melee weapon."
            weapon != OGRE_BOW -> "Your ranged weapon isn't powerful enough to hurt the Chompy bird."
            !chompy.isBaitedBy(plr) -> "This is not your Chompy Bird to shoot."
            else -> return true
        }
        plr.sendMessage(message)
        return false
    }

    /**
     * Notches the ogre bow for a chompy kill.
     */
    fun recordKill(plr: Player) {
        // TODO Big Chompy Bird Hunting: kills only count once the quest is finished.
        if (plr.chompyKills >= 100_000) return
        plr.sendMessage("You scratch a notch on your bow for the chompy bird kill.")
        plr.chompyKills++
        if (plr.chompyKills == EXPERT_KILLS) {
            plr.skill(Skill.RANGED).addExperience(30_000.0)
            plr.sendMessage("You've been awarded ranged experience for your relentless pursuit of chompies!")
            plr.newDialogue()
                .give(Item(CHOMPY_PICTURE), "@blu@**** Congratulations! 4000 Chompies! ****\\n@dre@~ You're an " +
                                            "Expert Dragon Archer! ~\\n@bla@This is the highest honour that can be " +
                                            "bestowed\\non any chompy bird hunter.", false)
                .open()
        }
    }

    /**
     * Plucks a dead chompy bird: the meat and bones fall to the ground and the feathers go in the pack.
     */
    fun pluck(plr: Player, dead: Npc) {
        plr.sendMessage("You start plucking the chompy bird.")
        plr.animation(Animation(PICKUP_ANIMATION))
        plr.lock(2) {
            if (dead.state != EntityState.ACTIVE) return@lock
            val pos = dead.position
            plr.world.npcs.remove(dead)
            plr.world.addItem(RAW_CHOMPY, 1, pos, plr)
            plr.world.addItem(BONES, 1, pos, plr)
            val feathers = Item(FEATHER, rand(10, 29))
            if (!plr.inventory.add(feathers)) plr.world.addItem(feathers, plr.position, plr)
        }
    }

    /**
     * Tells the player how many chompy birds they have killed.
     */
    fun checkKills(plr: Player) {
        // TODO Big Chompy Bird Hunting: before the quest is finished this says "You've scratched up no kills yet!"
        //  and "You've got to complete the quest first!".
        val kills = plr.chompyKills
        val rank = if (kills == 0) "You've not even started." else "~ You're ${article(rank(kills))}! ~"
        plr.sendMessage("You've scratched up a total of $kills chompy bird kills so far!")
        plr.sendMessage(rank)
        val text = if (kills >= EXPERT_KILLS) {
            "@blu@**** Congratulations! ****\\n@dre@~ You're an Expert Dragon Archer! ~\\n@bla@This is the highest " +
            "honour that can be bestowed\\non any chompy bird hunter."
        } else {
            "You've killed a total of @blu@$kills @bla@chompy birds\\nso far!\\n\\n@blu@$rank"
        }
        plr.newDialogue().give(Item(CHOMPY_PICTURE), text, false).open()
    }

    /**
     * The hunting rank for [kills] chompy birds.
     */
    fun rank(kills: Int) = when {
        kills < 5 -> "Ogre Novice"
        kills < 15 -> "Beginner"
        kills < 20 -> "Ogre Learner"
        kills < 30 -> "Learner"
        kills < 40 -> "Ogre Bowman"
        kills < 50 -> "Bowman"
        kills < 70 -> "Ogre Yeoman"
        kills < 95 -> "Yeoman"
        kills < 125 -> "Ogre Marksman"
        kills < 170 -> "Marksman"
        kills < 225 -> "Ogre Woodsman"
        kills < 300 -> "Woodsman"
        kills < 400 -> "Ogre Forester"
        kills < 550 -> "Forester"
        kills < 700 -> "Ogre Bowmaster"
        kills < 1000 -> "Bowmaster"
        kills < 1300 -> "Ogre Expert"
        kills < 1700 -> "Expert"
        kills < 2250 -> "Ogre Dragon Archer"
        kills < 3000 -> "Dragon Archer"
        kills < 4000 -> "Expert Ogre Dragon Archer"
        else -> "Expert Dragon Archer"
    }

    /**
     * Splits [text] into chat box lines of at most [width] visible characters. Colour codes don't count.
     */
    fun lines(text: String, width: Int = 52): Array<String> {
        val out = ArrayList<String>()
        var line = ""
        for (word in text.split(' ')) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && next.replace(COLOUR, "").length > width) {
                out += line
                line = word
            } else {
                line = next
            }
        }
        if (line.isNotEmpty()) out += line
        return out.toTypedArray()
    }

    /**
     * [text] wrapped for the item box beside an item picture.
     */
    fun boxText(text: String) = lines(text, 44).joinToString("\\n")

    private val COLOUR = Regex("@[a-z0-9]{3}@")

    private fun article(word: String) = if (word.first().uppercaseChar() in "AEIOU") "an $word" else "a $word"
}
