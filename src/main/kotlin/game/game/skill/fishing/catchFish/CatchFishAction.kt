package game.skill.fishing.catchFish

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import game.skill.Skills
import game.skill.fishing.catchFish.Fish.*
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.Entity
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * An [Action] implementation that will catch fish.
 *
 * @author lare96
 */
class CatchFishAction(plr: Player,
                      private val target: Entity,
                      private val spot: FishingSpot,
                      private val tool: Tool) : Action<Player>(plr, ActionType.WEAK, false, 1) {

    companion object {

        /**
         * Everything a big net can catch, in the order it's rolled. Each one is rolled on every catch, so one catch can
         * bring up several items.
         */
        private val BIG_NET_ROLLS = listOf(LEATHER_BOOTS to LEATHER_BOOTS.chance,
                                           SEAWEED to SEAWEED.chance,
                                           LEATHER_GLOVES to LEATHER_GLOVES.chance,
                                           MACKEREL to MACKEREL.chance,
                                           MACKEREL to (10 to 10),
                                           OYSTER to OYSTER.chance,
                                           CASKET to CASKET.chance,
                                           COD to COD.chance,
                                           BASS to BASS.chance)

        /**
         * Where Fisherman Frodi stands when he thanks the player for a catch.
         */
        private val FRODI_POSITION = Position(2576, 3852)

        /**
         * Fisherman Frodi's NPC id.
         */
        private const val FRODI = 1397
    }

    // TODO@0.5.0 Implement correct sounds: FISHING_CAST(377), FISH_SWIM(378), NET(379), CATCH(238), ?

    /**
     * Where the fishing spot was when fishing started. Fishing stops if the spot moves.
     */
    private val spotPosition = target.position

    /**
     * Ticks since fishing started.
     */
    private var ticks = 0

    /**
     * The tick the current cast started on. Pike fishing casts again now and then.
     */
    private var castTick = 0

    /**
     * The tick of the next catch attempt.
     */
    private var nextRoll = tool.firstRoll

    override fun onSubmit() {
        if (canFish()) {
            mob.walking.clear()
            mob.interact(target)
        } else {
            complete()
        }
    }

    override fun run(): Boolean {
        ticks++
        if (target.position != spotPosition || !canFish()) {
            return true
        }

        // Cast, then roll for a catch every few ticks.
        when (ticks - castTick) {
            1 -> {
                mob.animation(Animation(tool.castAnimation))
                mob.sendMessage(tool.castMessages[0])
               // playSound(Sound.START_ROD_FISHING, Sound.START_FISHING)
            }

            2 -> tool.castMessages.getOrNull(1)?.let { mob.sendMessage(it) }
        }
        if (ticks == nextRoll) {
            if (ticks != tool.firstRoll || tool.animateFirstRoll) {
                mob.animation(Animation(tool.animation))
            }
            roll()
            if (tool == Tool.KARAMBWAN_VESSEL) {
                // Each click lowers the vessel once.
                return true
            }
            nextRoll = ticks + tool.delay
            if (tool == Tool.PIKE_FISHING_ROD && rand(0, 4) == 0) {
                // Pike fishers sometimes cast their line again, which takes an extra tick.
                castTick = ticks + 1
                nextRoll++
            }
        }
        return false
    }

    override fun onFinished() {
        mob.animation(Animation.CANCEL)
    }

    /**
     * Checks the level, tool, bait, and inventory space needed to keep fishing. Opens a dialogue explaining what's
     * missing if fishing can't continue.
     */
    private fun canFish(): Boolean {
        // TODO Tai Bwo Wannai Trio: Lubufu teaches Karambwan fishing during the quest. Players he hasn't taught get
        //  "You don't even know how to use the vessel!".
        if (tool == Tool.KARAMBWAN_VESSEL && mob.fishing.level < tool.level) {
            mob.sendMessage(tool.levelMessage)
            return false
        }
        val message = when {
            mob.fishing.level < tool.level -> tool.levelMessage
            tool == Tool.KARAMBWAN_VESSEL && tool.id !in mob.inventory &&
                    Tool.EMPTY_KARAMBWAN_VESSEL in mob.inventory ->
                "You need to load your vessel with a karambwanji to fish here."

            tool.id !in mob.inventory -> tool.toolMessage
            tool.bait != null && tool.bait !in mob.inventory -> tool.baitMessage
            (tool.checkSpaceOnCast || ticks - castTick >= 3) && isInventoryFull() -> tool.fullMessage
            else -> return true
        }
        mob.newDialogue().text(message).open()
        return false
    }

    /**
     * Determines if there's no room for another catch. Using up the last piece of bait frees its slot.
     */
    private fun isInventoryFull(): Boolean {
        val bait = tool.bait
        return mob.inventory.computeRemainingSize() < 1 &&
                (bait == null || mob.inventory.computeAmountForId(bait) > 1)
    }

    /**
     * Rolls for a catch. The highest level fish is rolled first, and at most one fish is caught. Big nets roll for
     * everything they can catch instead.
     */
    private fun roll() {
        val level = mob.fishing.level
        if (tool == Tool.KARAMBWAN_VESSEL) {
            mob.inventory.remove(Item(tool.id))
            mob.inventory.add(Item(Tool.EMPTY_KARAMBWAN_VESSEL))
            if (Skills.success(KARAMBWAN.chance, level)) {
                catch(KARAMBWAN)
            } else {
                mob.sendMessage("A Karambwan deftly snatches the Karambwanji from your vessel!")
            }
            return
        }
        if (tool == Tool.BIG_NET) {
            for ((fish, chance) in BIG_NET_ROLLS) {
                if (level >= fish.level && Skills.success(chance, level)) {
                    catch(fish)
                }
            }
            return
        }
        for (fish in tool.fish) {
            if (level >= fish.level && Skills.success(fish.chance, level)) {
                catch(fish)
                return
            }
        }
    }

    /**
     * Uses up bait and gives the player [fish] and its experience.
     */
    private fun catch(fish: Fish) {
        val bait = tool.bait
        if (bait != null) {
            mob.inventory.remove(Item(bait))
        }
        if (spot == FishingSpot.MISCELLANIA) {
            // TODO Managing Miscellania: each catch here should also raise the kingdom's approval rating.
            world.locator.findNpcs(FRODI_POSITION, 2) { it.id == FRODI }.firstOrNull()?.speak("Thanks!")
            mob.fishing.addExperience(0.1)
            mob.sendMessage("${fish.message.dropLast(1)} and give it to Fisherman Frodi.")
            return
        }
        if (!mob.inventory.add(Item(fish.id))) {
            // A big net can catch more than the inventory has room for.
            world.addItem(fish.id, 1, mob.position, mob)
        }
        mob.fishing.addExperience(fish.exp)
        mob.sendMessage(tool.catchMessages[fish] ?: fish.message)
       // playSound(Sound.CATCH_ROD_FISH, Sound.CATCH_FISH)
    }

    /**
     * Plays one of the argued sounds based on the tool the player is fishing with.
     */
    private fun playSound(rodSound: Sound, otherSound: Sound) {
        if (tool.castAnimation == 622) {
            mob.playSound(rodSound)
        } else {
            mob.playSound(otherSound)
        }
    }
}
