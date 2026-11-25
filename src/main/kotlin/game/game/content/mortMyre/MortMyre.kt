package game.content.mortMyre

import api.predef.*
import api.predef.ext.*
import game.obj.entrances.Entrances
import game.player.Animations
import game.player.Sound
import game.skill.agility.AgilityAction
import game.skill.agility.AgilityApproach
import game.skill.agility.AgilityStep
import game.skill.agility.AgilityWalk
import game.skill.agility.shortcut.ShortcutMoves.SPLASH
import game.skill.agility.shortcut.ShortcutMoves.STONE_LEAP
import game.skill.agility.shortcut.ShortcutMoves.active
import game.skill.agility.shortcut.ShortcutMoves.keepsFooting
import game.skill.agility.shortcut.ShortcutMoves.ready
import io.luna.game.model.EntityState
import io.luna.game.model.LocalGraphic
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.`object`.GameObject

/**
 * Mort Myre swamp after Nature Spirit: the blessed silver sickle makes the rotting logs, branches and bushes around the
 * player bloom, what blooms can be picked, three blossomed items at a time fill a druid pouch, and the Nature Grotto
 * blesses sickles and boosts Prayer.
 *
 * @author TheLining
 */
object MortMyre {

    const val SILVER_SICKLE = 2961
    const val BLESSED_SICKLE = 2963
    const val EMPTY_POUCH = 2957
    const val DRUID_POUCH = 2958
    const val FUNGUS = 2970
    const val STEM = 2972
    const val PEAR = 2974

    const val GROTTO_TREE = 3517
    const val GROTTO_POOL = 3520
    const val NATURE_ALTAR = 3521
    const val BRIDGE = 3522

    /**
     * DRUIDICSPIRIT_HUMAN_BLOOM and the graphics of the cast: on the player, and on each tile around them.
     */
    private const val BLOOM_ANIMATION = 1100
    private const val BLOOM_PLAYER_GRAPHIC = 264
    private const val BLOOM_TILE_GRAPHIC = 263

    /**
     * How long a bloomed log, branch or bush stays before it rots back.
     */
    private const val BLOOM_TICKS = 25

    private const val PICKUP_FLOOR = 827
    private const val PICKUP_TABLE = 832

    /**
     * Something in the swamp that blooms, from its rotting form to its bloomed form, and what it gives when picked.
     *
     * @param charges What the item is worth in a druid pouch.
     */
    enum class Blossom(val rotting: Int, val bloomed: Int, val item: Int, val charges: Int, val sound: Sound,
                       val animation: Int, val message: String) {
        LOG(3508, 3509, FUNGUS, 1, Sound.BLOOM_MUSHROOM, PICKUP_FLOOR, "You pick a mushroom from the log."),
        BRANCH(3510, 3511, STEM, 2, Sound.BLOOM_BRANCH, PICKUP_FLOOR, "You take a cutting from the budding branch."),
        BUSH(3512, 3513, PEAR, 3, Sound.BLOOM_PEARS, PICKUP_TABLE, "You pick a pear from the tree.");

        companion object {
            val ROTTING = entries.associateBy { it.rotting }
        }
    }

    /**
     * The blossomed items in the order a druid pouch takes them, most charges first.
     */
    private val POUCH_ORDER = Blossom.entries.sortedByDescending { it.charges }

    /**
     * Casts Bloom with the blessed sickle: drains 1 to 6 Prayer points and blooms every rotting log, branch and bush
     * on the eight tiles around [plr].
     */
    fun castBloom(plr: Player) {
        // TODO Nature Spirit: only after the Nature Spirit has blessed the player's sickle.
        val prayer = plr.prayer
        if (prayer.level == 0) {
            plr.sendMessage("You need some prayer points to activate the power of the sickle.")
            return
        }
        prayer.level = maxOf(0, prayer.level - rand(1, 6))
        if (prayer.level == 0) {
            plr.sendMessage("You've run out of prayer points.")
        }
        plr.animation(Animation(BLOOM_ANIMATION))
        plr.graphic(Graphic(BLOOM_PLAYER_GRAPHIC, 0, 30))
        plr.playSound(Sound.CAST_BLOOM)
        for (dx in -1..1) {
            for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue
                val tile = plr.position.translate(dx, dy)
                LocalGraphic(plr.context, BLOOM_TILE_GRAPHIC, 0, 15, tile, ChunkUpdatableView.globalView()).display()
                val rotting = world.objects.findAll(tile).filter { it.id in Blossom.ROTTING }.findFirst().orElse(null)
                    ?: continue
                val blossom = Blossom.ROTTING.getValue(rotting.id)
                if (Entrances.replace(rotting, blossom.bloomed, BLOOM_TICKS)) {
                    plr.playSound(blossom.sound)
                }
            }
        }
    }

    /**
     * Picks [blossom] off [obj], which rots straight back.
     */
    fun pick(plr: Player, obj: GameObject, blossom: Blossom) {
        if (obj.state != EntityState.ACTIVE) return
        if (plr.inventory.isFull) {
            plr.sendMessage("Your inventory is too full to take that.")
            return
        }
        if (!world.removeObject(obj)) return
        world.addObject(blossom.rotting, obj.position, obj.objectType, obj.direction)
        plr.animation(Animation(blossom.animation))
        plr.playSound(Sound.PICK)
        plr.inventory.add(Item(blossom.item))
        plr.sendMessage(blossom.message)
    }

    /**
     * Adds three blossomed items to the druid pouch, pears first, then stems, then fungi.
     */
    fun fillPouch(plr: Player) {
        val inv = plr.inventory
        val taken = LinkedHashMap<Blossom, Int>()
        var left = 3
        for (blossom in POUCH_ORDER) {
            val count = minOf(left, inv.computeAmountForId(blossom.item))
            if (count > 0) {
                taken[blossom] = count
                left -= count
            }
        }
        if (left > 0) {
            plr.sendMessage("You need at least three blossomed items to add something to the druid pouch.")
            return
        }
        val emptyIndex = inv.computeIndexForId(EMPTY_POUCH)
        val filled = inv.contains(DRUID_POUCH)
        if (!filled && emptyIndex == -1) return
        for ((blossom, count) in taken) {
            inv.remove(Item(blossom.item, count))
        }
        val charges = taken.entries.sumOf { (blossom, count) -> blossom.charges * count }
        if (filled) inv.add(Item(DRUID_POUCH, charges)) else inv[emptyIndex] = Item(DRUID_POUCH, charges)
        plr.sendMessage("You add $charges natures harvests to your druid pouch.")
    }

    /**
     * Blesses the silver sickle at [index] in the grotto's waters.
     */
    fun blessSickle(plr: Player, index: Int) {
        // TODO Nature Spirit: only after the Nature Spirit has blessed the player's first sickle.
        if (plr.inventory.contains(BLESSED_SICKLE) || plr.equipment.contains(BLESSED_SICKLE) ||
            plr.bank.contains(BLESSED_SICKLE)) {
            plr.sendMessage("You already have a blessed sickle.")
            return
        }
        if (!plr.inventory.contains(index, SILVER_SICKLE)) return
        plr.inventory[index] = Item(BLESSED_SICKLE)
        plr.animation(Animation(BLOOM_ANIMATION))
        plr.graphic(Graphic(BLOOM_PLAYER_GRAPHIC, 0, 40))
        Entrances.itemBox(plr, BLESSED_SICKLE, "You dip the sickle into the grotto water to bless it.")
    }

    /**
     * Recharges Prayer at the Altar of Nature, and boosts it 2 points above the player's level.
     */
    fun prayAtAltar(plr: Player) {
        val prayer = plr.prayer
        val boosted = prayer.staticLevel + 2
        when {
            prayer.level >= boosted -> {
                plr.sendMessage("You have already boosted your prayer points.")
                return
            }
            prayer.level >= prayer.staticLevel -> {
                plr.sendMessage("You boost your prayer points.")
                plr.playSound(Sound.PRAYER_BOOST)
            }
            else -> {
                plr.animation(Animations.PRAY)
                plr.sendMessage("You recharge your prayer points.")
                plr.playSound(Sound.PRAYER_RECHARGE)
            }
        }
        prayer.level = boosted
    }

    /**
     * Agility XP for a jump across the broken bridge, and for a fall.
     */
    private const val JUMP_XP = 15.0
    private const val FALL_XP = 2.0

    /**
     * HUMAN_WOBBLEANDFALL_L and HUMAN_WOBBLEANDFALL_R: the fall heading north, and heading south.
     */
    private const val FALL_NORTH = 770
    private const val FALL_SOUTH = 771

    /**
     * The swamp beside the broken bridge, where a falling player lands.
     */
    private val BRIDGE_WATER = Position(3439, 3330)

    /**
     * Jumps the broken bridge to or from the grotto's island from [bridge], the half the player clicked. A fall drops
     * the player into the swamp, and they drag themselves out on the side they were heading for.
     */
    fun jumpBridge(plr: Player, bridge: GameObject) {
        if (!ready(plr)) return
        val from = Position(bridge.position.x, bridge.position.y)
        val north = from.y < 3330
        val landing = if (north) Position(3440, 3331) else Position(3441, 3329)
        val begin = begin@{
            if (!ready(plr) || plr.position != from || bridge.state != EntityState.ACTIVE) return@begin
            if (keepsFooting(plr.agility.level, 60, 252)) {
                plr.submitAction(AgilityAction(plr, listOf(
                    AgilityStep(landing, 3, animation = STONE_LEAP, moveStart = 58, moveEnd = 70)), null,
                                               valid = { active(plr) }).also {
                    it.onSuccess = { plr.agility.addExperience(JUMP_XP) }
                })
            } else {
                plr.submitAction(fall(plr, north))
            }
        }
        if (plr.position == from) begin() else plr.submitAction(AgilityApproach(plr, from) { begin() })
    }

    /**
     * Falls off the broken bridge into the swamp and swims out heading [north] or south.
     */
    private fun fall(plr: Player, north: Boolean): AgilityAction {
        val swim = AgilityWalk.SWIM.walk
        val dy = if (north) 1 else -1
        val shallows = BRIDGE_WATER.translate(-1, if (north) 1 else -2)
        val bank = shallows.translate(0, dy)
        return AgilityAction(plr, listOf(
            AgilityStep(BRIDGE_WATER, 2, animation = if (north) FALL_NORTH else FALL_SOUTH, moveStart = 58,
                        moveEnd = 60),
            AgilityStep(shallows, 2, walkingAnimation = swim, jump = true, begin = {
                LocalGraphic(plr.context, SPLASH, 0, 0, BRIDGE_WATER, ChunkUpdatableView.globalView()).display()
                plr.playSound(Sound.POOL_PLOP)
            }),
            AgilityStep(bank, 1, jump = true, message = "You nearly drown in the disgusting swamp.")), null,
                              valid = { active(plr) }).also {
            it.onSuccess = {
                plr.damage(rand(1, 7))
                plr.agility.addExperience(FALL_XP)
            }
        }
    }
}
