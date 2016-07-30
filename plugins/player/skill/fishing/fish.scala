/*
 A plugin for the Fishing skill that adds functionality for catching fish.

 SUPPORTS:
  -> Catching fish with the proper tools and bait.
  -> Big net fishing catching 1-3 fish, instead of 1.

 TODO:
  -> Karambwan fishing.

 AUTHOR: lare96
*/

import io.luna.game.action.HarvestingSkillAction
import io.luna.game.event.impl.NpcClickEvent.{NpcFirstClickEvent, NpcSecondClickEvent}
import io.luna.game.model.`def`.ItemDefinition.getNameForId
import io.luna.game.model.item.Item
import io.luna.game.model.mobile.Animation.CANCEL_ANIMATION
import io.luna.game.model.mobile.Skill.FISHING
import io.luna.game.model.mobile.{Animation, Player}


/* Class representing fish that can be caught. */
private case class Fish(id: Int, level: Int, exp: Double)

/* Class representing tools that can be used to catch fish. */
private case class Tool(
  id: Int,
  level: Int,
  bait: Option[Int],
  chance: Double,
  animation: Int,
  fish: Fish*
)


/* A collection of constants describing data for each fish that can be caught. */
private val SHRIMP = Fish(317, 1, 10.0)
private val SARDINE = Fish(327, 5, 20.0)
private val HERRING = Fish(345, 10, 30.0)
private val ANCHOVY = Fish(321, 15, 40.0)
private val MACKEREL = Fish(353, 16, 20.0)
private val CASKET = Fish(405, 16, 10.0)
private val OYSTER = Fish(407, 16, 10.0)
private val LEATHER_BOOTS = Fish(1061, 16, 1.0)
private val LEATHER_GLOVES = Fish(1059, 16, 1.0)
private val SEAWEED = Fish(401, 16, 1.0)
private val TROUT = Fish(335, 20, 50.0)
private val COD = Fish(341, 23, 45.0)
private val PIKE = Fish(349, 25, 60.0)
private val SALMON = Fish(331, 30, 70.0)
private val TUNA = Fish(359, 35, 80.0)
private val LOBSTER = Fish(377, 40, 90.0)
private val BASS = Fish(363, 46, 100.0)
private val SWORDFISH = Fish(371, 50, 100.0)
private val SHARK = Fish(383, 76, 110.0)

/* A collection of constants describing data for each tool that can be used. */
private val SMALL_NET = Tool(303, 1, None, 0.10, 621, SHRIMP, ANCHOVY)
private val FISHING_ROD = Tool(307, 5, Some(313), 0.15, 622, SARDINE, HERRING, PIKE)
private val BIG_NET = Tool(305, 16, None, 0.07, 620, MACKEREL, OYSTER, COD, BASS, CASKET, LEATHER_BOOTS, LEATHER_GLOVES, SEAWEED)
private val FLY_FISHING_ROD = Tool(309, 20, Some(314), 0.20, 622, TROUT, SALMON)
private val HARPOON = Tool(311, 35, None, 0.15, 618, TUNA, SWORDFISH)
private val LOBSTER_POT = Tool(301, 40, None, 0.05, 619, LOBSTER)
private val SHARK_HARPOON = Tool(311, 76, None, 0.01, 618, SHARK)


/* An Action implementation that will manage the fish catching operation. */
private final class FishAction(plr: Player, tool: Tool) extends HarvestingSkillAction(plr, FISHING) {

  /* A variable for the experience that will be given. */
  private var exp = 0.0

  /* Determines if the player can start or continue fishing. */
  private def canFish = {
    if (skill.getLevel < tool.level) {
      plr.sendMessage(s"You need a Fishing level of ${tool.level} to fish here.")
      false
    } else if (!tool.bait.forall(plr.inventory.contains)) {
      plr.sendMessage(s"You do not have the bait required to fish here.")
      false
    } else if (!plr.inventory.contains(tool.id)) {
      plr.sendMessage(s"You need a ${getNameForId(tool.id)} to fish here.")
      false
    } else {
      plr.animation(new Animation(tool.animation))
      true
    }
  }

  /* If this FishAction can be initialized. */
  override def canInit = {
    if (canFish) {
      plr.sendMessage("You begin to fish...")
      true
    } else {
      false
    }
  }

  /* Function executed when we receive some fish. */
  override def onHarvest() = {
    currentAdd.foreach(it => plr.sendMessage(s"You catch some ${getNameForId(it.getId)}."))

    skill.addExperience(exp)
    exp = 0.0
  }

  /* If we are able to harvest this tick (continue fishing). */
  override def canHarvest = canFish && rand.nextInt(500) != 0

  /* The chance of harvesting fish this tick. */
  override def harvestChance = tool.chance

  /* The fish that will be harvested (if successful). */
  override def add = {
    val fishCount = if (tool == BIG_NET) rand.nextInt(1, 4) else 1
    val fishArray = new Array[Item](fishCount)

    def randomFish = {
      val fish = tool.fish.randomElement

      exp += fish.exp
      if (fish.level <= skill.getLevel) fish else tool.fish.head
    }

    var index = 0
    fishCount.times {
      fishArray(index) = new Item(randomFish.id)
      index += 1
    }

    fishArray
  }

  /* The fish bait that will be removed (if successful). */
  override def remove = tool.bait.map(it => Array(new Item(it))).getOrElse(Array.empty)

  /* Cancel the animation when the Action is interrupted. */
  override def onInterrupt() = plr.animation(CANCEL_ANIMATION)
}


/* A collection of intercepted first index click events for fishing spots. */
intercept_@[NpcFirstClickEvent](233, 234, 235, 236) { (msg, plr) =>
  plr.submitAction(new FishAction(plr, FISHING_ROD))
}

intercept_@[NpcFirstClickEvent](309, 310, 311, 314, 315, 317, 318) { (msg, plr) =>
  plr.submitAction(new FishAction(plr, FLY_FISHING_ROD))
}

intercept_@[NpcFirstClickEvent](312) { (msg, plr) =>
  plr.submitAction(new FishAction(plr, LOBSTER_POT))
}

intercept_@[NpcFirstClickEvent](313) { (msg, plr) =>
  plr.submitAction(new FishAction(plr, BIG_NET))
}

intercept_@[NpcFirstClickEvent](316, 319) { (msg, plr) =>
  plr.submitAction(new FishAction(plr, SMALL_NET))
}

/* A collection of intercepted second index click events for fishing spots. */
intercept_@[NpcSecondClickEvent](309, 316, 319, 310, 311, 314, 315, 317, 318) { (msg, plr) =>
  plr.submitAction(new FishAction(plr, FISHING_ROD))
}

intercept_@[NpcSecondClickEvent](312) { (msg, plr) =>
  plr.submitAction(new FishAction(plr, HARPOON))
}

intercept_@[NpcSecondClickEvent](313) { (msg, plr) =>
  plr.submitAction(new FishAction(plr, SHARK_HARPOON))
}