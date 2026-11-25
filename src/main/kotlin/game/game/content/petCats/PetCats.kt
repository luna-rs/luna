package game.content.petCats

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.content.petCats.PetCat.Stage
import game.player.Sound
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.dialogue.Expression
import io.luna.game.model.path.route.LineOfSight

/**
 * Pet cats: a cat put down from the inventory follows its owner, a kitten needs feeding and attention while it grows
 * into a cat, a cat can chase rats, and a cat grows on into an overgrown cat that is too big to hunt.
 *
 * Growth, hunger, attention and the rat count belong to the player, not to one cat, and only move while a cat is out.
 *
 * @author TheLining
 */
object PetCats {

    /**
     * Rats a cat will chase: the ordinary rat and the one found indoors.
     */
    val RATS = setOf(47, 2682)

    const val BALL_OF_WOOL = 1759
    const val BUCKET_OF_MILK = 1927
    const val BUCKET = 1925

    const val PICKUP_ANIMATION = 827
    const val ROLL_OVER_ANIMATION = 318
    const val POUNCE_ANIMATION = 319

    /**
     * Ticks between two growth steps of a cat that is out: 90 seconds.
     */
    const val GROWTH_TICKS = 150

    /**
     * Growth steps for a kitten to become a cat, and for a cat to become overgrown.
     */
    const val CAT_GROWTH = 120
    const val OVERGROWN_GROWTH = 320

    /**
     * A new kitten starts with this much loneliness; stroking puts it back here.
     */
    const val CONTENT = 28

    /**
     * Rats a cat must catch before Gertrude hands out her medal.
     */
    const val RATS_FOR_MEDAL = 100

    /**
     * How far a cat looks for a rat to chase.
     */
    const val HUNT_RADIUS = 10

    /**
     * A cat further away than this, or on another floor, is brought back next to its owner.
     */
    const val LOST_DISTANCE = 15

    /**
     * Fish a kitten will eat: sardine and tuna raw or cooked, and raw shrimps, anchovies, trout and salmon.
     */
    val FISH = setOf(327, 325, 359, 361, 317, 321, 335, 331)

    /**
     * Chance in 100 that a cat catches the rat it chases, and that a kitten does.
     */
    const val CAT_CATCH = 70
    const val KITTEN_CATCH = 15

    /**
     * The item of the cat that is out, or 0 when no cat is out. Saved, so the cat comes back after logging in.
     */
    var Player.catOut by Attr.int().persist("pet_cat_out")

    /**
     * Growth steps so far: a kitten becomes a cat at [CAT_GROWTH], and a cat becomes overgrown at [OVERGROWN_GROWTH].
     */
    var Player.catGrowth by Attr.int().persist("pet_cat_growth")

    /**
     * Growth steps since a kitten last ate.
     */
    var Player.catHunger by Attr.int().persist("pet_cat_hunger")

    /**
     * How lonely a kitten is. 0 means a new kitten, which counts as [CONTENT].
     */
    var Player.catLoneliness by Attr.int().persist("pet_cat_loneliness")

    /**
     * Rats caught, up to [RATS_FOR_MEDAL].
     */
    var Player.catRats by Attr.int().persist("pet_cat_rats")

    /**
     * Ticks since the last growth step of the cat that is out.
     */
    internal var Player.growthTicks by Attr.int()

    /**
     * The cat NPC each player has out.
     */
    private val followers = HashMap<Player, Npc>()

    /**
     * The cat [plr] has out, or `null`.
     */
    fun follower(plr: Player): Npc? = followers[plr]?.takeIf { it.state == EntityState.ACTIVE }

    /**
     * Whether [npc] is the cat [plr] has out.
     */
    fun owns(plr: Player, npc: Npc) = follower(plr) === npc

    /**
     * Whether [plr] owns a kitten or cat anywhere: out, in the inventory or in the bank.
     */
    fun hasKittenOrCat(plr: Player): Boolean {
        val out = PetCat.forItem(plr.catOut)
        if (out != null) return true
        return PetCat.ITEMS.any {
            val stage = PetCat.forItem(it)!!.stage
            stage != Stage.OVERGROWN && (plr.inventory.contains(it) || plr.bank.contains(it))
        }
    }

    /**
     * Puts down the cat at inventory [index] so it follows [plr]. Returns `false` if the item there isn't a pet cat.
     */
    fun release(plr: Player, index: Int): Boolean {
        val cat = PetCat.forItem(plr.inventory[index]?.id ?: return false) ?: return false
        if (plr.catOut != 0) {
            plr.sendMessage("You already have a follower.")
            return true
        }
        if (!allowedOut(plr)) {
            plr.sendMessage("You can't put your cat down here.")
            return true
        }
        plr.inventory[index] = null
        plr.playSound(Sound.PUT_DOWN)
        plr.overlays.closeWindows()
        plr.catOut = cat.item
        spawn(plr, cat, plr.position)?.speak("Miaow!")
        return true
    }

    /**
     * Gives [plr] a new kitten of a random coat, already following them.
     */
    fun giveKitten(plr: Player) {
        follower(plr)?.let { remove(plr, it) }
        val kitten = PetCat(Stage.KITTEN, rand(0, PetCat.COATS - 1))
        resetCat(plr)
        plr.catOut = kitten.item
        spawn(plr, kitten, nearbySquare(plr) ?: plr.position)?.speak("Meeow!")
    }

    /**
     * Keeps the cat of [plr] next to them and moves its growth on. Runs every tick.
     */
    fun tick(plr: Player) {
        var npc = followers[plr]
        if (npc != null && npc.state != EntityState.ACTIVE) {
            followers.remove(plr)
            npc = null
        }
        val cat = PetCat.forItem(plr.catOut)
        if (cat == null) {
            if (plr.catOut != 0) plr.catOut = 0
            if (npc != null) remove(plr, npc)
            return
        }
        if (plr.state != EntityState.ACTIVE) return
        if (!allowedOut(plr)) {
            hopIntoBag(plr, npc, cat)
            return
        }
        if (npc == null) {
            if (plr.isPendingPlacement) return
            spawn(plr, cat, plr.position)?.speak("Meeeew!")
            return
        }
        if (npc.position.z != plr.position.z || !npc.position.isWithinDistance(plr.position, LOST_DISTANCE)) {
            nearbySquare(plr)?.let { npc.move(it) }
        }
        if (npc.navigator.currentTarget !== plr && !npc.isLocked) npc.navigator.follow(plr)
        if (++plr.growthTicks >= GROWTH_TICKS) {
            plr.growthTicks = 0
            grow(plr, npc, cat)
        }
    }

    /**
     * Takes the cat out of the world when [plr] logs out. It stays saved as out and comes back at the next login.
     */
    fun logout(plr: Player) {
        followers.remove(plr)?.let { if (it.state == EntityState.ACTIVE) it.world.npcs.remove(it) }
    }

    /**
     * The Interact-with menu: stroke the cat, send it after vermin, or shoo it away.
     */
    fun interact(plr: Player, npc: Npc) {
        if (!owns(plr, npc)) {
            plr.sendMessage("This is not your cat.")
            return
        }
        plr.newDialogue().options(
            "Stroke", { it.overlays.closeWindows(); stroke(it, npc) },
            "Chase vermin", { it.overlays.closeWindows(); chaseVermin(it, npc) },
            "Shoo-away", { it.overlays.closeWindows(); shoo(it, npc) }).open()
    }

    /**
     * Talks to a cat. Without a way to understand cats, all that comes back is a meow.
     */
    fun talk(plr: Player, npc: Npc) {
        if (!owns(plr, npc)) {
            plr.sendMessage("This is not your cat.")
            return
        }
        // TODO Icthlarin's Little Helper: with an amulet of catspeak worn, the cat talks back.
        npc.speak("Meow!")
    }

    /**
     * Puts [plr]'s cat back in their inventory.
     */
    fun pickUp(plr: Player, npc: Npc) {
        if (!owns(plr, npc)) {
            plr.sendMessage("That isn't yours to take.")
            return
        }
        val cat = PetCat.forItem(plr.catOut) ?: return
        if (!plr.inventory.hasSpaceFor(Item(cat.item))) {
            plr.sendMessage("You don't have enough inventory space to pick up your cat.")
            return
        }
        remove(plr, npc)
        plr.catOut = 0
        plr.inventory.add(Item(cat.item))
        plr.playSound(Sound.PICK)
    }

    /**
     * Strokes [plr]'s cat, which keeps a kitten from getting lonely.
     */
    fun stroke(plr: Player, npc: Npc) {
        if (!owns(plr, npc)) {
            plr.sendMessage("This is not your cat.")
            return
        }
        plr.catLoneliness = if (plr.catLoneliness == CONTENT) CONTENT - 5 else CONTENT
        plr.sendMessage("You softly stroke your cat.")
        plr.animation(Animation(PICKUP_ANIMATION))
        npc.animation(Animation(ROLL_OVER_ANIMATION))
        plr.lock(2) {
            npc.speak("Purr...purr...")
            plr.newDialogue().player(Expression.HAPPY, "That cat sure loves to be stroked.").open()
        }
    }

    /**
     * Asks [plr] if they really want to shoo their cat away, and lets it go for good if they do.
     */
    fun shoo(plr: Player, npc: Npc) {
        if (!owns(plr, npc)) {
            plr.sendMessage("This is not your cat.")
            return
        }
        plr.newDialogue().options(
            "Yes I am.", {
                it.overlays.closeWindows()
                if (!owns(it, npc)) return@options
                followers.remove(it)
                it.catOut = 0
                resetCat(it)
                it.speak("Shoo cat!")
                npc.speak("Meeeooow!")
                runFrom(it, npc)
                it.world.scheduleOnce(4) { if (npc.state == EntityState.ACTIVE) npc.world.npcs.remove(npc) }
                it.lock(4) { it.sendMessage("The cat has run away.") }
            },
            "No I'm not.", {
                it.overlays.closeWindows()
                it.sendMessage("You choose not to shoo away the cat.")
            }).title("Are you sure you want to shoo away the cat?").open()
    }

    /**
     * Sends [plr]'s cat after a rat nearby. A cat catches it 70 times in 100, a kitten only 15.
     */
    fun chaseVermin(plr: Player, npc: Npc) {
        if (!owns(plr, npc)) {
            plr.sendMessage("This is not your cat.")
            return
        }
        val cat = PetCat.forItem(plr.catOut) ?: return
        if (cat.stage == Stage.OVERGROWN) {
            plr.sendMessage("The cat is just too big to catch vermin.")
            return
        }
        val rat = world.locator.findNpcs(npc, HUNT_RADIUS, true) {
            it.id in RATS && it.state == EntityState.ACTIVE && it.isAlive && it.position.z == npc.position.z
        }.firstOrNull()
        if (rat == null) {
            plr.sendMessage("There aren't any rats or mice around.")
            return
        }
        plr.speak("Go on kitty...kill that rat!")
        npc.speak("Meeeeeoooowww!")
        rat.speak("Eek!")
        plr.playSound(Sound.RAT_HIT)
        if (!LineOfSight.hasLineOfWalk(world.collisionManager.view(false), npc.position.z, npc.position.x,
                                       npc.position.y, rat.position.x, rat.position.y, 1, 1, 1, 1, 0)) {
            plr.sendMessage("Your cat cannot get to its prey.")
            return
        }
        npc.navigator.cancel()
        npc.interact(rat)
        npc.navigator.navigate(rat.position, false)
        plr.lock(2) {
            if (!owns(plr, npc)) return@lock
            npc.animation(Animation(POUNCE_ANIMATION))
            plr.lock(1) {
                if (!owns(plr, npc)) return@lock
                npc.interact(plr)
                npc.navigator.follow(plr)
                val caught = rand(0, 99) < if (cat.stage == Stage.KITTEN) KITTEN_CATCH else CAT_CATCH
                if (!caught || rat.state != EntityState.ACTIVE || !rat.isAlive) {
                    plr.sendMessage("The rat manages to get away!")
                    return@lock
                }
                kill(rat)
                npc.speak("MeeeoooooW!")
                plr.speak("Hey well done kitty, you got it!")
                plr.catRats = minOf(RATS_FOR_MEDAL, plr.catRats + 1)
            }
        }
    }

    /**
     * Uses the item at inventory [index] on [plr]'s cat: milk and fish feed it, and a ball of wool keeps it happy.
     */
    fun useOn(plr: Player, npc: Npc, index: Int) {
        if (!owns(plr, npc)) {
            plr.sendMessage("This is not your cat.")
            return
        }
        val item = plr.inventory[index] ?: return
        val pet = if (PetCat.forItem(plr.catOut)?.stage == Stage.KITTEN) "kitten" else "cat"
        when (item.id) {
            BALL_OF_WOOL -> {
                plr.catLoneliness = 6
                plr.animation(Animation(PICKUP_ANIMATION))
                npc.animation(Animation(ROLL_OVER_ANIMATION))
                npc.speak("Purr!")
                plr.lock(2) {
                    plr.newDialogue().player(Expression.HAPPY, "That $pet loves to play with that ball of wool.",
                                             "I think it is its favourite.").open()
                }
            }
            BUCKET_OF_MILK -> {
                plr.catHunger = 0
                plr.inventory[index] = Item(BUCKET)
                plr.animation(Animation(PICKUP_ANIMATION))
                plr.sendMessage("The $pet laps up the milk.")
                npc.speak("Purpurr")
            }
            in FISH -> {
                plr.catHunger = 0
                plr.inventory[index] = null
                plr.animation(Animation(PICKUP_ANIMATION))
                plr.sendMessage("The $pet gobbles up the ${item.itemDef.name.lowercase()}.")
                npc.speak("Purr!")
            }
            else -> plr.sendMessage("Nothing interesting happens.")
        }
    }

    /**
     * One growth step of the cat that is out. A kitten also gets hungrier and lonelier, and runs off if neglected.
     */
    private fun grow(plr: Player, npc: Npc, cat: PetCat) {
        if (cat.stage == Stage.OVERGROWN) return
        plr.catGrowth = minOf(OVERGROWN_GROWTH, plr.catGrowth + 1)
        npc.speak("Meow!")
        if (cat.stage == Stage.CAT) {
            if (plr.catGrowth >= OVERGROWN_GROWTH) {
                growUp(plr, npc, cat)
                resetCat(plr)
                tell(plr, "Your cat has grown into a mighty feline,", "but it will no longer be able to chase vermin.")
            }
            return
        }
        if (plr.catGrowth >= CAT_GROWTH) {
            growUp(plr, npc, cat)
            tell(plr, "Your kitten has grown into a healthy cat,", "that can hunt for itself.")
            return
        }
        plr.catHunger++
        when (plr.catHunger) {
            16 -> complain(plr, npc, "Miaow!", Expression.WORRIED, "I think it's hungry!")
            18 -> complain(plr, npc, "Meeeooowww!", Expression.WORRIED, "I think it's really hungry!")
            20 -> {
                plr.sendMessage("Your kitten has run away to look for food.")
                loseCat(plr)
                return
            }
        }
        plr.catLoneliness = (if (plr.catLoneliness == 0) CONTENT else plr.catLoneliness) + 1
        when (plr.catLoneliness) {
            40 -> complain(plr, npc, "Purr...", Expression.NEUTRAL, "I think it wants some attention.")
            45 -> complain(plr, npc, "Meeeooowww...", Expression.NEUTRAL, "I think it's feeling lonely.")
            50 -> {
                plr.sendMessage("Your kitten got lonely and ran off.")
                loseCat(plr)
            }
        }
    }

    /**
     * Turns the cat that is out into its next stage, in place.
     */
    private fun growUp(plr: Player, npc: Npc, cat: PetCat) {
        val next = cat.grown() ?: return
        npc.transformKeepingLevels(next.npc)
        plr.catOut = next.item
        npc.speak("Meeeooowww!")
    }

    /**
     * The kitten speaks up and its owner says what it wants, unless they are busy with a window.
     */
    private fun complain(plr: Player, npc: Npc, meow: String, expression: Expression, line: String) {
        npc.speak(meow)
        if (plr.overlays.hasWindow()) plr.sendMessage(line) else plr.newDialogue().player(expression, line).open()
    }

    /**
     * Shows [lines] in a message box, or in the chat box if [plr] has a window open.
     */
    private fun tell(plr: Player, vararg lines: String) {
        if (plr.overlays.hasWindow()) plr.sendMessage(lines.joinToString(" ")) else plr.newDialogue().text(*lines).open()
    }

    /**
     * The cat that is out is gone for good, and the next cat starts from scratch.
     */
    private fun loseCat(plr: Player) {
        follower(plr)?.let { remove(plr, it) }
        plr.catOut = 0
        resetCat(plr)
    }

    /**
     * Clears growth, hunger, loneliness and the rat count.
     */
    private fun resetCat(plr: Player) {
        plr.catGrowth = 0
        plr.catHunger = 0
        plr.catLoneliness = 0
        plr.catRats = 0
    }

    /**
     * Puts the NPC for [cat] into the world at [at], following [plr].
     */
    private fun spawn(plr: Player, cat: PetCat, at: Position): Npc? {
        val npc = Npc(plr.context, cat.npc, at)
        npc.respawnTicks = -1
        if (!world.npcs.add(npc)) return null
        followers[plr] = npc
        plr.growthTicks = 0
        npc.interact(plr)
        npc.navigator.follow(plr)
        return npc
    }

    /**
     * Takes [plr]'s cat NPC out of the world.
     */
    private fun remove(plr: Player, npc: Npc) {
        if (followers[plr] === npc) followers.remove(plr)
        if (npc.state == EntityState.ACTIVE) world.npcs.remove(npc)
    }

    /**
     * Cats can't follow into instanced areas.
     */
    private fun allowedOut(plr: Player) = plr.dynamicMap == null

    /**
     * A cat that can't follow its owner hops into their bag, or runs away if there is no room for it.
     */
    private fun hopIntoBag(plr: Player, npc: Npc?, cat: PetCat) {
        if (npc != null) remove(plr, npc)
        plr.catOut = 0
        if (plr.inventory.add(Item(cat.item))) {
            plr.sendMessage("Your cat hops into your bag.")
        } else {
            plr.sendMessage("Your cat has run away.")
            resetCat(plr)
        }
    }

    /**
     * A free tile next to [plr] that a cat can step onto and walk to them from.
     */
    private fun nearbySquare(plr: Player): Position? = Direction.ALL_EXCEPT_NONE.shuffled().firstNotNullOfOrNull {
        val position = plr.position.translate(1, it)
        if (!world.collisionManager.isBlocked(position, false) &&
            world.collisionManager.traversable(plr.position, EntityType.NPC, it, false)) position else null
    }

    /**
     * A shooed cat runs a few tiles away from its owner.
     */
    private fun runFrom(plr: Player, npc: Npc) {
        npc.navigator.cancel()
        npc.interact(null)
        val dx = Integer.signum(npc.position.x - plr.position.x).takeIf { it != 0 } ?: rand(-1, 1)
        val dy = Integer.signum(npc.position.y - plr.position.y).takeIf { it != 0 } ?: rand(-1, 1)
        npc.navigator.navigate(npc.position.translate(dx * 5, dy * 5), false)
    }

    /**
     * A caught rat leaves the world until it respawns.
     */
    private fun kill(rat: Npc) {
        if (rat.respawnTicks < 0 || rat.index < 0) {
            rat.world.npcs.remove(rat)
            return
        }
        rat.despawn()
        rat.world.scheduleOnce(rat.respawnTicks) { rat.respawn() }
    }
}
