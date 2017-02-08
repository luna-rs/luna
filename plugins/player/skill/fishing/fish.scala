/*
 A plugin for the Fishing skill that adds functionality for catching fish.

 SUPPORTS:
  -> Catching fish with the proper tools and bait.
  -> Big net fishing catching 1-3 fish, instead of 1.

 TODO:
  -> Karambwan fishing.

 AUTHOR: lare96
*/

import io.luna.game.action.HarvestingAction
import io.luna.game.event.impl.NpcClickEvent.{NpcFirstClickEvent, NpcSecondClickEvent}
import io.luna.game.model.item.Item
import io.luna.game.model.mobile.Animation.CANCEL
import io.luna.game.model.mobile.{Animation, Player}
import io.luna.util.Rational


/* Class representing fish that can be caught. */
private case class Fish(id: Int, level: Int, exp: Double)

/* Class representing tools that can be used to catch fish. */
private case class Tool(
  id: Int,
  level: Int,
  bait: Option[Int],
  chance: Rational,
  animation: Int,
  fish: Fish*
)


/* Constants representing fish that can be caught. */
private val SHRIMP =
  Fish(id = 317,
    level = 1,
    exp = 10.0)

private val SARDINE =
  Fish(id = 327,
    level = 5,
    exp = 20.0)

private val HERRING =
  Fish(id = 345,
    level = 10,
    exp = 30.0)

private val ANCHOVY =
  Fish(id = 321,
    level = 15,
    exp = 40.0)

private val MACKEREL =
  Fish(id = 353,
    level = 16,
    exp = 20.0)

private val CASKET =
  Fish(id = 405,
    level = 16,
    exp = 10.0)

private val OYSTER =
  Fish(id = 407,
    level = 16,
    exp = 10.0)

private val LEATHER_BOOTS =
  Fish(id = 1061,
    level = 16,
    exp = 1.0)

private val LEATHER_GLOVES =
  Fish(id = 1059,
    level = 16,
    exp = 1.0)

private val SEAWEED =
  Fish(id = 401,
    level = 16,
    exp = 1.0)

private val TROUT =
  Fish(id = 335,
    level = 20,
    exp = 50.0)

private val COD =
  Fish(id = 341,
    level = 23,
    exp = 45.0)

private val PIKE =
  Fish(id = 349,
    level = 25,
    exp = 60.0)

private val SALMON =
  Fish(id = 331,
    level = 30,
    exp = 70.0)

private val TUNA =
  Fish(id = 359,
    level = 35,
    exp = 80.0)

private val LOBSTER =
  Fish(id = 377,
    level = 40,
    exp = 90.0)

private val BASS =
  Fish(id = 363,
    level = 46,
    exp = 100.0)

private val SWORDFISH =
  Fish(id = 371,
    level = 50,
    exp = 100.0)

private val MONKFISH =
  Fish(id = 7944,
    level = 62,
    exp = 120.0)

private val SHARK =
  Fish(id = 383,
    level = 76,
    exp = 110.0)

/* Constants representing tools that can be used. */
private val SMALL_NET =
  Tool(id = 303,
    level = 1,
    bait = None,
    chance = CHANCE_COMMON,
    animation = 621,
    fish = SHRIMP, ANCHOVY)

private val FISHING_ROD =
  Tool(id = 307,
    level = 5,
    bait = Some(313),
    chance = CHANCE_COMMON,
    animation = 622,
    fish = SARDINE, HERRING, PIKE)

private val BIG_NET =
  Tool(id = 305,
    level = 16,
    bait = None,
    chance = CHANCE_UNCOMMON,
    animation = 620,
    fish = MACKEREL, OYSTER, COD, BASS, CASKET, LEATHER_BOOTS, LEATHER_GLOVES, SEAWEED)

private val FLY_FISHING_ROD =
  Tool(id = 309,
    level = 20,
    bait = Some(314),
    chance = CHANCE_VERY_COMMON,
    animation = 622,
    fish = TROUT, SALMON)

private val HARPOON =
  Tool(id = 311,
    level = 35,
    bait = None,
    chance = CHANCE_VERY_UNCOMMON,
    animation = 618,
    fish = TUNA, SWORDFISH)

private val LOBSTER_POT =
  Tool(id = 301,
    level = 40,
    bait = None,
    chance = CHANCE_UNCOMMON,
    animation = 619,
    fish = LOBSTER)

private val MONKFISH_NET =
  Tool(id = 303,
    level = 62,
    bait = None,
    chance = CHANCE_COMMON,
    animation = 621,
    fish = MONKFISH)

private val SHARK_HARPOON =
  Tool(id = 311,
    level = 76,
    bait = None,
    chance = CHANCE_VERY_UNCOMMON,
    animation = 618,
    fish = SHARK)


/* An Action implementation that will manage the fish catching operation. */
private final class FishAction(plr: Player, tool: Tool) extends HarvestingAction(plr) {

  private var exp = 0.0
  private val skill = plr.skill(SKILL_FISHING)

  private def canFish = {
    if (skill.getLevel < tool.level) {
      plr.sendMessage(s"You need a Fishing level of ${ tool.level } to fish here.")
      false
    } else if (!tool.bait.forall(plr.inventory.contains)) {
      plr.sendMessage(s"You do not have the bait required to fish here.")
      false
    } else if (!plr.inventory.contains(tool.id)) {
      plr.sendMessage(s"You need a ${ nameOfItem(tool.id) } to fish here.")
      false
    } else {
      plr.animation(new Animation(tool.animation))
      true
    }
  }

  override def canInit = {
    if (canFish) {
      plr.sendMessage("You begin to fish...")
      true
    } else {
      false
    }
  }

  override def onHarvest() = {
    currentAdd.foreach(it => plr.sendMessage(s"You catch some ${ nameOfItem(it.getId) }."))

    skill.addExperience(exp)
    exp = 0.0
  }
  override def canHarvest = canFish && rand(500) != 0
  override def harvestChance = tool.chance

  override def add = {
    val fishCount = if (tool == BIG_NET) rand(1, 3) else 1
    val fishArray = new Array[Item](fishCount)

    def randomFish = {
      val fish = rand(tool.fish)

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

  override def remove = tool.bait.map { it => Array(new Item(it)) }.getOrElse(Array.empty)

  override def onInterrupt() = plr.animation(CANCEL)
}


/* Intercepted first click events for fishing spots. */
onargs[NpcFirstClickEvent](233, 234, 235, 236) { msg =>
  msg.plr.submitAction(new FishAction(msg.plr, FISHING_ROD))
}

onargs[NpcFirstClickEvent](309, 310, 311, 314, 315, 317, 318) { msg =>
  msg.plr.submitAction(new FishAction(msg.plr, FLY_FISHING_ROD))
}

onargs[NpcFirstClickEvent](312) { msg =>
  msg.plr.submitAction(new FishAction(msg.plr, LOBSTER_POT))
}

onargs[NpcFirstClickEvent](313) { msg =>
  msg.plr.submitAction(new FishAction(msg.plr, BIG_NET))
}

onargs[NpcFirstClickEvent](316, 319) { msg =>
  msg.plr.submitAction(new FishAction(msg.plr, SMALL_NET))
}

onargs[NpcFirstClickEvent](1174) { msg =>
  msg.plr.submitAction(new FishAction(msg.plr, MONKFISH_NET))
}

/* Intercepted second click events for fishing spots. */
onargs[NpcSecondClickEvent](309, 316, 319, 310, 311, 314, 315, 317, 318) { msg =>
  msg.plr.submitAction(new FishAction(msg.plr, FISHING_ROD))
}

onargs[NpcSecondClickEvent](312) { msg =>
  msg.plr.submitAction(new FishAction(msg.plr, HARPOON))
}

onargs[NpcSecondClickEvent](313) { msg =>
  msg.plr.submitAction(new FishAction(msg.plr, SHARK_HARPOON))
}
